package com.gerko.fourenote.data.ink

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Turns a hand-drawn line, circle, ellipse, triangle, rectangle or square into a clean
 * one (draw it, then hold the pen still), and spots closed loops drawn around things.
 * Points are flat x, y pairs in page units, as in [InkStroke.points].
 */
object Shapes {
    /** How far a line may wander from straight, as a share of its length. */
    private const val LINE_TOLERANCE = 0.08f

    /** How close a shape's end must come back to its start, as a share of its size. */
    private const val CLOSED_GAP = 0.25f

    /** Corner detection tolerance, as a share of the shape's size. */
    private const val CORNER_TOLERANCE = 0.1f

    /** How far an edge may lean and still count as horizontal or vertical, in degrees. */
    private const val AXIS_SLACK = 15.0

    /** Width and height closer than this share count as a square or circle. */
    private const val EQUAL_SIDES = 0.15f

    /** Average distance from a fitted ellipse, as a share of its radius, for it to count as one. */
    private const val ELLIPSE_TOLERANCE = 0.2f

    /** Clean shapes are drawn as points about this far apart, so they erase like ink. */
    private const val STEP = 4f

    /** The clean version of the shape [points] draws, or null if it isn't one. */
    fun recognize(points: FloatArray): FloatArray? {
        val n = points.size / 2
        if (n < 3) return null
        val bounds = Bounds.of(points)
        val size = hypot(bounds.width, bounds.height)
        if (size < 12f) return null

        val x0 = points[0]; val y0 = points[1]
        val x1 = points[2 * n - 2]; val y1 = points[2 * n - 1]
        val chord = hypot(x1 - x0, y1 - y0)
        if (chord > 0.5f * size && maxDistanceFromLine(points, x0, y0, x1, y1) <= LINE_TOLERANCE * chord) {
            return polyline(floatArrayOf(x0, y0, x1, y1))
        }
        if (!isLoop(points)) return null

        val corners = corners(points, CORNER_TOLERANCE * size)
        return when (corners.size / 2) {
            3 -> polyline(corners + corners.copyOf(2))
            4 -> rectangle(corners, bounds)
            else -> ellipse(points, bounds)
        }
    }

    /** True if [points] come back to where they started, enclosing an area. */
    fun isLoop(points: FloatArray): Boolean {
        val n = points.size / 2
        if (n < 6) return false
        val bounds = Bounds.of(points)
        val size = hypot(bounds.width, bounds.height)
        if (size < 12f || minOf(bounds.width, bounds.height) < 0.15f * size) return false
        // Closed if the end comes back near the start, or (drawn past it, as loops often are) near
        // the start's part of the path, or the start lies near the end's part.
        val endX = points[2 * n - 2]
        val endY = points[2 * n - 1]
        var gap = hypot(endX - points[0], endY - points[1])
        for (i in 0 until n / 3) gap = minOf(gap, hypot(endX - points[2 * i], endY - points[2 * i + 1]))
        for (i in n - n / 3 until n) gap = minOf(gap, hypot(points[0] - points[2 * i], points[1] - points[2 * i + 1]))
        return gap <= CLOSED_GAP * size
    }

    private fun rectangle(corners: FloatArray, bounds: Bounds): FloatArray {
        val axisAligned = (0 until 4).all { i ->
            val j = (i + 1) % 4
            val degrees = atan2(corners[2 * j + 1] - corners[2 * i + 1], corners[2 * j] - corners[2 * i]).toDouble() * 180.0 / PI
            val offAxis = ((degrees % 90.0) + 90.0) % 90.0
            offAxis <= AXIS_SLACK || offAxis >= 90.0 - AXIS_SLACK
        }
        // A tilted four-sided shape keeps its drawn corners, straightened into clean edges.
        if (!axisAligned) return polyline(corners + corners.copyOf(2))
        var w = bounds.width
        var h = bounds.height
        if (abs(w - h) <= EQUAL_SIDES * maxOf(w, h)) {
            w = (w + h) / 2
            h = w
        }
        val cx = bounds.centerX
        val cy = bounds.centerY
        val l = cx - w / 2; val r = cx + w / 2; val t = cy - h / 2; val b = cy + h / 2
        return polyline(floatArrayOf(l, t, r, t, r, b, l, b, l, t))
    }

    private fun ellipse(points: FloatArray, bounds: Bounds): FloatArray? {
        val cx = bounds.centerX
        val cy = bounds.centerY
        var rx = bounds.width / 2
        var ry = bounds.height / 2
        if (rx < 1f || ry < 1f) return null
        // How far the drawing strays from the ellipse through its bounds.
        var error = 0f
        for (i in points.indices step 2) {
            val dx = (points[i] - cx) / rx
            val dy = (points[i + 1] - cy) / ry
            error += abs(hypot(dx, dy) - 1f)
        }
        if (error / (points.size / 2) > ELLIPSE_TOLERANCE) return null
        if (abs(rx - ry) <= EQUAL_SIDES * maxOf(rx, ry)) {
            rx = (rx + ry) / 2
            ry = rx
        }
        val segments = (2 * PI * maxOf(rx, ry) / STEP).toInt().coerceIn(24, 360)
        return FloatArray((segments + 1) * 2) { k ->
            val angle = 2 * PI * (k / 2) / segments
            if (k % 2 == 0) cx + rx * cos(angle).toFloat() else cy + ry * sin(angle).toFloat()
        }
    }

    /**
     * The corners of a closed drawing: the points that stand out most from straight runs,
     * found by Ramer-Douglas-Peucker after splitting the loop at the point farthest from its start.
     */
    private fun corners(points: FloatArray, tolerance: Float): FloatArray {
        val n = points.size / 2
        var far = 0
        var farDistance = 0f
        for (i in 0 until n) {
            val d = hypot(points[2 * i] - points[0], points[2 * i + 1] - points[1])
            if (d > farDistance) { far = i; farDistance = d }
        }
        val keep = BooleanArray(n)
        keep[0] = true
        keep[far] = true
        simplify(points, 0, far, tolerance, keep)
        simplify(points, far, n - 1, tolerance, keep)
        // The last point closes the loop onto the first; it isn't a corner of its own.
        keep[n - 1] = false
        val out = ArrayList<Float>()
        for (i in 0 until n) if (keep[i]) { out += points[2 * i]; out += points[2 * i + 1] }
        return dropStraightCorners(out.toFloatArray(), tolerance)
    }

    private fun simplify(points: FloatArray, from: Int, to: Int, tolerance: Float, keep: BooleanArray) {
        if (to <= from + 1) return
        var index = -1
        var max = 0f
        for (i in from + 1 until to) {
            val d = distanceToSegment(points[2 * i], points[2 * i + 1], points[2 * from], points[2 * from + 1], points[2 * to], points[2 * to + 1])
            if (d > max) { max = d; index = i }
        }
        if (index < 0 || max <= tolerance) return
        keep[index] = true
        simplify(points, from, index, tolerance, keep)
        simplify(points, index, to, tolerance, keep)
    }

    // The loop's start is always kept, even mid-edge; drop any corner that lies on a straight run.
    private fun dropStraightCorners(corners: FloatArray, tolerance: Float): FloatArray {
        var c = corners
        var changed = true
        while (changed && c.size / 2 > 3) {
            changed = false
            val m = c.size / 2
            for (i in 0 until m) {
                val p = (i + m - 1) % m
                val q = (i + 1) % m
                if (distanceToSegment(c[2 * i], c[2 * i + 1], c[2 * p], c[2 * p + 1], c[2 * q], c[2 * q + 1]) <= tolerance) {
                    c = c.copyOfRange(0, 2 * i) + c.copyOfRange(2 * i + 2, c.size)
                    changed = true
                    break
                }
            }
        }
        return c
    }

    private fun maxDistanceFromLine(points: FloatArray, x0: Float, y0: Float, x1: Float, y1: Float): Float {
        var max = 0f
        for (i in points.indices step 2) max = maxOf(max, distanceToSegment(points[i], points[i + 1], x0, y0, x1, y1))
        return max
    }

    private fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0f) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    /** Straight segments between the given corners, filled in with points [STEP] apart. */
    fun polyline(corners: FloatArray): FloatArray {
        val out = ArrayList<Float>()
        out += corners[0]; out += corners[1]
        for (i in 2 until corners.size step 2) {
            val ax = corners[i - 2]; val ay = corners[i - 1]
            val bx = corners[i]; val by = corners[i + 1]
            val steps = (hypot(bx - ax, by - ay) / STEP).toInt().coerceAtLeast(1)
            for (s in 1..steps) {
                out += ax + (bx - ax) * s / steps
                out += ay + (by - ay) * s / steps
            }
        }
        return out.toFloatArray()
    }

    /** True if the point ([x], [y]) is inside the closed loop [points] (even-odd rule). */
    fun contains(points: FloatArray, x: Float, y: Float): Boolean {
        var inside = false
        val n = points.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val xi = points[2 * i]; val yi = points[2 * i + 1]
            val xj = points[2 * j]; val yj = points[2 * j + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    internal class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width get() = right - left
        val height get() = bottom - top
        val centerX get() = (left + right) / 2
        val centerY get() = (top + bottom) / 2

        companion object {
            fun of(points: FloatArray): Bounds {
                var l = Float.MAX_VALUE; var t = Float.MAX_VALUE
                var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
                for (i in points.indices step 2) {
                    l = minOf(l, points[i]); r = maxOf(r, points[i])
                    t = minOf(t, points[i + 1]); b = maxOf(b, points[i + 1])
                }
                return Bounds(l, t, r, b)
            }
        }
    }
}
