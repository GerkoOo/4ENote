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
 *    Ramer–Douglas–Peucker, then rebuilds the line as a Catmull-Rom spline.
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

    /** Uniform Catmull-Rom spline through every point, sampled about every [SAMPLE_SPACING_DP]. */
    private fun catmullRom(points: FloatArray): FloatArray {
        val n = points.size / 2
        if (n < 3) return points
        fun px(i: Int) = points[2 * i.coerceIn(0, n - 1)]
        fun py(i: Int) = points[2 * i.coerceIn(0, n - 1) + 1]

        val out = ArrayList<Float>()
        out.add(px(0))
        out.add(py(0))
        for (i in 0 until n - 1) {
            val steps = ceil(hypot(px(i + 1) - px(i), py(i + 1) - py(i)) / SAMPLE_SPACING_DP)
                .toInt().coerceAtLeast(1)
            for (s in 1..steps) {
                val t = s.toFloat() / steps
                out.add(spline(px(i - 1), px(i), px(i + 1), px(i + 2), t))
                out.add(spline(py(i - 1), py(i), py(i + 1), py(i + 2), t))
            }
        }
        return out.toFloatArray()
    }

    private fun spline(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val t2 = t * t
        val t3 = t2 * t
        return 0.5f * (
            2 * p1 +
                (p2 - p0) * t +
                (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 +
                (3 * p1 - p0 - 3 * p2 + p3) * t3
            )
    }

    private fun distanceToLine(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val length = hypot(dx, dy)
        if (length == 0f) return hypot(px - ax, py - ay)
        return kotlin.math.abs(dy * px - dx * py + bx * ay - by * ax) / length
    }
}
