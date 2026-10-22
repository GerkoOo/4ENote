package com.gerko.fourenote.data.ink

import kotlin.math.ceil
import kotlin.math.hypot

/**
 * Handwriting stabilization. Levels run from 0 (raw input) to [MAX_LEVEL].
 *
 * Two stages work together:
 *  - [Stabilizer] smooths points live while the pen moves, so the ink on
 *    screen already looks steady.
 *  - [smoothStroke] cleans the finished stroke: it drops jitter with
 *    Ramer–Douglas–Peucker, then rebuilds the line as a centripetal Catmull-Rom
 *    spline that keeps sharp corners sharp.
 */
object Smoothing {
    const val MAX_LEVEL = 10
    const val DEFAULT_LEVEL = 4

    /** Spacing in dp between points of the rebuilt curve. */
    private const val SAMPLE_SPACING_DP = 2f

    /**
     * Pulls each point part of the way toward the raw input, like a pen on a
     * short string, so small tremors never reach the page.
     */
    class Stabilizer(level: Int) {
        private val follow = 1f - level.coerceIn(0, MAX_LEVEL) * 0.085f
        private var x = 0f
        private var y = 0f
        private var started = false

        fun next(rawX: Float, rawY: Float): Pair<Float, Float> {
            if (!started) {
                x = rawX
                y = rawY
                started = true
            } else {
                x += (rawX - x) * follow
                y += (rawY - y) * follow
            }
            return x to y
        }
    }

    /** Returns smoothed packed points (x0, y0, x1, y1, ...) for a finished stroke. */
    fun smoothStroke(points: FloatArray, level: Int): FloatArray {
        val l = level.coerceIn(0, MAX_LEVEL)
        if (l == 0 || points.size < 6) return points
        val simplified = simplify(points, epsilon = l * 0.12f)
        return catmullRom(simplified)
    }

    /** Ramer–Douglas–Peucker: keeps only points that deviate more than [epsilon] dp from the line. */
    private fun simplify(points: FloatArray, epsilon: Float): FloatArray {
        val n = points.size / 2
        val keep = BooleanArray(n)
        keep[0] = true
        keep[n - 1] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to n - 1)
        while (stack.isNotEmpty()) {
            val (first, last) = stack.removeLast()
            var maxDist = 0f
            var index = -1
            for (i in first + 1 until last) {
                val d = distanceToLine(
                    points[2 * i], points[2 * i + 1],
                    points[2 * first], points[2 * first + 1],
                    points[2 * last], points[2 * last + 1],
                )
                if (d > maxDist) {
                    maxDist = d
                    index = i
                }
            }
            if (index != -1 && maxDist > epsilon) {
                keep[index] = true
                stack.addLast(first to index)
                stack.addLast(index to last)
            }
        }
        val out = ArrayList<Float>()
        for (i in 0 until n) {
            if (keep[i]) {
                out.add(points[2 * i])
                out.add(points[2 * i + 1])
            }
        }
        return out.toFloatArray()
    }

    /**
     * A turn sharper than this (cosine of the angle between one segment and the next) is a real
     * corner, as in N, V or k: the curve passes through it without rounding it off.
     */
    private const val CORNER_COS = 0.35f

    /**
     * Centripetal Catmull-Rom spline through every point, sampled about every [SAMPLE_SPACING_DP].
     * Unlike the plain (uniform) kind it never forms loops or cusps where points are unevenly
     * spaced, and the curve is not carried round sharp corners, so they stay crisp instead of
     * bulging out.
     */
    private fun catmullRom(points: FloatArray): FloatArray {
        val n = points.size / 2
        if (n < 3) return points
        fun px(i: Int) = points[2 * i.coerceIn(0, n - 1)]
        fun py(i: Int) = points[2 * i.coerceIn(0, n - 1) + 1]
        val corner = BooleanArray(n) { i ->
            if (i == 0 || i == n - 1) {
                false
            } else {
                val ax = px(i) - px(i - 1); val ay = py(i) - py(i - 1)
                val bx = px(i + 1) - px(i); val by = py(i + 1) - py(i)
                val lengths = hypot(ax, ay) * hypot(bx, by)
                lengths > 0f && (ax * bx + ay * by) / lengths < CORNER_COS
            }
        }

        val out = ArrayList<Float>()
        out.add(px(0))
        out.add(py(0))
        for (i in 0 until n - 1) {
            // At a corner the neighbour beyond it is ignored, so the curve doesn't swing past it.
            val before = if (corner[i]) i else i - 1
            val after = if (corner[i + 1]) i + 1 else i + 2
            val steps = ceil(hypot(px(i + 1) - px(i), py(i + 1) - py(i)) / SAMPLE_SPACING_DP)
                .toInt().coerceAtLeast(1)
            for (s in 1..steps) {
                val t = s.toFloat() / steps
                val (x, y) = centripetal(px(before), py(before), px(i), py(i), px(i + 1), py(i + 1), px(after), py(after), t)
                out.add(x)
                out.add(y)
            }
        }
        return out.toFloatArray()
    }

    // The centripetal (alpha = 1/2) Catmull-Rom point at [t] (0..1) between p1 and p2, by the Barry-Goldman construction.
    private fun centripetal(
        x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, t: Float,
    ): Pair<Float, Float> {
        fun knot(ax: Float, ay: Float, bx: Float, by: Float) = kotlin.math.sqrt(hypot(bx - ax, by - ay)).coerceAtLeast(1e-4f)
        val t1 = knot(x0, y0, x1, y1)
        val t2 = t1 + knot(x1, y1, x2, y2)
        val t3 = t2 + knot(x2, y2, x3, y3)
        val u = t1 + (t2 - t1) * t
        fun lerp(a: Float, b: Float, ta: Float, tb: Float) = (tb - u) / (tb - ta) * a + (u - ta) / (tb - ta) * b
        val a1x = lerp(x0, x1, 0f, t1); val a1y = lerp(y0, y1, 0f, t1)
        val a2x = lerp(x1, x2, t1, t2); val a2y = lerp(y1, y2, t1, t2)
        val a3x = lerp(x2, x3, t2, t3); val a3y = lerp(y2, y3, t2, t3)
        val b1x = lerp(a1x, a2x, 0f, t2); val b1y = lerp(a1y, a2y, 0f, t2)
        val b2x = lerp(a2x, a3x, t1, t3); val b2y = lerp(a2y, a3y, t1, t3)
        return lerp(b1x, b2x, t1, t2) to lerp(b1y, b2y, t1, t2)
    }

    private fun distanceToLine(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val length = hypot(dx, dy)
        if (length == 0f) return hypot(px - ax, py - ay)
        return kotlin.math.abs(dy * px - dx * py + bx * ay - by * ax) / length
    }
}
