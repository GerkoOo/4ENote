package com.gerko.fourenote.data.ink

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Fountain-pen dynamics. Each point of an ink-pen stroke gets a width factor
 * (multiplied by the stroke's base width): slow movement and firm pressure
 * give a broad line, fast movement a fine one, and both ends taper.
 */
object InkPen {
    private const val MIN_FACTOR = 0.35f
    private const val MAX_FACTOR = 1.6f
    private const val TAPER_POINTS = 4

    /**
     * [points] are packed x, y pairs; [times] in milliseconds and [pressures]
     * (0..1, where 0 means "not reported") have one entry per point.
     */
    fun widthFactors(points: FloatArray, times: LongArray, pressures: FloatArray): FloatArray {
        val n = points.size / 2
        val out = FloatArray(n)
        var smoothed = 1f
        for (i in 0 until n) {
            val pressure = pressures[i].takeIf { it > 0f } ?: 0.6f
            val speed = if (i == 0) {
                0f
            } else {
                val dt = (times[i] - times[i - 1]).coerceAtLeast(1)
                hypot(points[2 * i] - points[2 * i - 2], points[2 * i + 1] - points[2 * i - 1]) / dt
            }
            val pressureFactor = 0.5f + 0.7f * pressure.coerceIn(0f, 1f)
            val speedFactor = (1.3f - 0.3f * speed).coerceIn(0.6f, 1.3f)
            val target = pressureFactor * speedFactor
            // Ink responds gradually, so jumps in speed don't make the line jagged.
            smoothed = if (i == 0) target else smoothed + (target - smoothed) * 0.3f
            out[i] = smoothed
        }
        for (i in 0 until minOf(TAPER_POINTS, n)) {
            val taper = 0.55f + 0.45f * i / TAPER_POINTS
            out[i] *= taper
            out[n - 1 - i] *= taper
        }
        for (i in out.indices) out[i] = out[i].coerceIn(MIN_FACTOR, MAX_FACTOR)
        return out
    }

    /**
     * Carries per-point [widths] from [raw] points over to [resampled] points
     * of the same line (for example after smoothing), matching by distance along it.
     */
    fun resampleWidths(raw: FloatArray, widths: FloatArray, resampled: FloatArray): FloatArray {
        val rawLengths = cumulativeLengths(raw)
        val newLengths = cumulativeLengths(resampled)
        val rawTotal = rawLengths.last().coerceAtLeast(1e-3f)
        val newTotal = newLengths.last().coerceAtLeast(1e-3f)
        var j = 0
        return FloatArray(newLengths.size) { i ->
            val target = newLengths[i] / newTotal * rawTotal
            while (j < rawLengths.size - 2 && rawLengths[j + 1] < target) j++
            val span = (rawLengths.getOrElse(j + 1) { rawLengths[j] } - rawLengths[j]).coerceAtLeast(1e-6f)
            val t = ((target - rawLengths[j]) / span).coerceIn(0f, 1f)
            val next = widths.getOrElse(j + 1) { widths[j] }
            widths[j] + (next - widths[j]) * t
        }
    }

    private fun cumulativeLengths(points: FloatArray): FloatArray {
        val n = points.size / 2
        val out = FloatArray(n.coerceAtLeast(1))
        for (i in 1 until n) {
            out[i] = out[i - 1] + hypot(points[2 * i] - points[2 * i - 2], points[2 * i + 1] - points[2 * i - 1])
        }
        return out
    }
}

/**
 * Calligraphy with a flat (broad-edge) nib held at a fixed angle, like a real
 * italic pen. The nib is a thin rectangle swept along the path: moving across
 * its edge draws a full-width line, moving along it a hairline, and the ends
 * come out sharp and angled. Pressure lengthens the nib, squared, as a
 * flexible nib spreads under force.
 */
object Calligraphy {
    /**
     * Default nib angle from horizontal, in degrees, for the flat nib: a flat (0 degree) nib
     * makes up-and-down strokes thick and side-to-side strokes thin.
     */
    const val DEFAULT_NIB_DEGREES = 0f

    /** The nib's thickness as a fraction of its length: thin strokes are about a quarter of thick ones. */
    private const val THICKNESS = 0.25f

    fun nibRadians(degrees: Float) = Math.toRadians(degrees.toDouble()).toFloat()

    /** Thinnest line of the pointed pen, as a share of its full width: the hairline of an up-stroke. */
    private const val HAIRLINE = 0.16f

    /** Points on each side used to judge which way the pen is moving, so jitter doesn't flicker the width. */
    private const val DIRECTION_REACH = 3

    /**
     * Width factors for the pointed pen (like a dip pen or brush pen, and Notewise's calligraphy):
     * full width while the pen moves down the page, a hairline while it moves up or sideways,
     * blending smoothly in between. Unlike the flat nib, a stroke and the same stroke drawn the
     * other way look different: only down-strokes are bold.
     */
    fun pointedFactors(points: FloatArray): FloatArray {
        val n = points.size / 2
        if (n == 0) return FloatArray(0)
        val raw = FloatArray(n) { i ->
            val a = maxOf(0, i - DIRECTION_REACH)
            val b = minOf(n - 1, i + DIRECTION_REACH)
            val dx = points[2 * b] - points[2 * a]
            val dy = points[2 * b + 1] - points[2 * a + 1]
            val length = kotlin.math.hypot(dx, dy)
            // Screen y grows downwards, so a positive dy is a down-stroke.
            val down = if (length < 0.01f) 0f else (dy / length).coerceIn(0f, 1f)
            HAIRLINE + (1f - HAIRLINE) * down
        }
        // A short moving average keeps the swell from thin to thick gradual.
        return FloatArray(n) { i ->
            var sum = 0f
            var count = 0
            for (j in maxOf(0, i - 2)..minOf(n - 1, i + 2)) {
                sum += raw[j]
                count++
            }
            sum / count
        }
    }

    /** Per-point nib length factors from pen pressure; all 1 when pressure isn't reported. */
    fun pressureFactors(pressures: FloatArray): FloatArray = FloatArray(pressures.size) { i ->
        val p = pressures[i]
        if (p <= 0f) 1f else (0.35f + 0.9f * p * p)
    }

    /**
     * The filled shape of a nib stroke as convex polygons (packed x, y pairs):
     * for each segment, the hull of the nib rectangle at both ends. Each
     * polygon winds the same way, so filling them all draws their union.
     */
    fun nibShapes(stroke: InkStroke): List<FloatArray> {
        val angle = stroke.nibAngle ?: return emptyList()
        val n = stroke.points.size / 2
        if (n == 0) return emptyList()
        val ux = cos(angle)
        val uy = sin(angle)
        fun corners(i: Int): FloatArray {
            val length = stroke.width * (stroke.widths?.getOrNull(i) ?: 1f)
            val half = length / 2
            val thick = maxOf(length * THICKNESS, 0.6f) / 2
            val x = stroke.points[2 * i]
            val y = stroke.points[2 * i + 1]
            // Rectangle corners: along the nib (u) and across it (-uy, ux).
            return floatArrayOf(
                x + ux * half - uy * thick, y + uy * half + ux * thick,
                x + ux * half + uy * thick, y + uy * half - ux * thick,
                x - ux * half + uy * thick, y - uy * half - ux * thick,
                x - ux * half - uy * thick, y - uy * half + ux * thick,
            )
        }
        if (n == 1) return listOf(convexHull(corners(0)))
        return (0 until n - 1).map { i -> convexHull(corners(i) + corners(i + 1)) }
    }

    /** Andrew's monotone chain; returns the hull counter-clockwise. */
    private fun convexHull(xy: FloatArray): FloatArray {
        val pts = (xy.indices step 2).map { xy[it] to xy[it + 1] }.distinct().sortedWith(compareBy({ it.first }, { it.second }))
        if (pts.size < 3) return pts.flatMap { listOf(it.first, it.second) }.toFloatArray()
        fun cross(o: Pair<Float, Float>, a: Pair<Float, Float>, b: Pair<Float, Float>) =
            (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)
        val lower = ArrayList<Pair<Float, Float>>()
        for (p in pts) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0) lower.removeAt(lower.size - 1)
            lower += p
        }
        val upper = ArrayList<Pair<Float, Float>>()
        for (p in pts.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0) upper.removeAt(upper.size - 1)
            upper += p
        }
        val hull = lower.dropLast(1) + upper.dropLast(1)
        return hull.flatMap { listOf(it.first, it.second) }.toFloatArray()
    }

    /** Area of a polygon (packed x, y pairs); used by tests to compare shapes. */
    fun area(polygon: FloatArray): Float {
        var sum = 0f
        val n = polygon.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            sum += polygon[2 * i] * polygon[2 * j + 1] - polygon[2 * j] * polygon[2 * i + 1]
        }
        return abs(sum) / 2
    }
}

/**
 * The filled shape of a variable-width stroke: the left edge forwards then the
 * right edge backwards, as packed x, y pairs. Renderers add round caps at the ends.
 */
fun InkStroke.outline(): FloatArray {
    val factors = widths ?: return FloatArray(0)
    val n = points.size / 2
    if (n < 2) return FloatArray(0)
    val left = FloatArray(n * 2)
    val right = FloatArray(n * 2)
    for (i in 0 until n) {
        val a = (i - 1).coerceAtLeast(0)
        val b = (i + 1).coerceAtMost(n - 1)
        var tx = points[2 * b] - points[2 * a]
        var ty = points[2 * b + 1] - points[2 * a + 1]
        val length = hypot(tx, ty).takeIf { it > 0f } ?: 1f
        tx /= length
        ty /= length
        val half = width * factors[i] / 2
        left[2 * i] = points[2 * i] - ty * half
        left[2 * i + 1] = points[2 * i + 1] + tx * half
        right[2 * i] = points[2 * i] + ty * half
        right[2 * i + 1] = points[2 * i + 1] - tx * half
    }
    val out = FloatArray(n * 4)
    left.copyInto(out)
    for (i in 0 until n) {
        out[2 * n + 2 * i] = right[2 * (n - 1 - i)]
        out[2 * n + 2 * i + 1] = right[2 * (n - 1 - i) + 1]
    }
    return out
}
