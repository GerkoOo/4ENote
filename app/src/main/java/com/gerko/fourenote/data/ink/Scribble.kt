package com.gerko.fourenote.data.ink

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Scribble to erase: scrubbing back and forth over ink with the pen deletes
 * that ink instead of adding the scribble.
 */
object Scribble {
    private const val MIN_REVERSALS = 3
    /** How much longer the scribble's path must be than its bounding box's diagonal. */
    private const val MIN_DENSITY = 2.5f
    /** Movement shorter than this (page units) never counts as a reversal, so jitter is ignored. */
    private const val MIN_SWING = 6f

    /**
     * A reversal only counts if the pass it ends spans at least this share of
     * the scribble's extent, so jitter and small letter loops don't count.
     */
    private const val MIN_PASS_FRACTION = 0.3f

    /**
     * How much of the stroke must run along one line, back and forth (0 to 1). A scrub goes
     * along one direction and back; handwriting, cursive included, heads in many directions
     * (letters, loops, joins). Measured on real strokes, scrubs come out around 0.95 and written
     * words below about 0.77, whatever the angle of the scrub.
     */
    private const val MIN_CONSISTENCY = 0.82f

    /** True if [stroke] is a back-and-forth scrub rather than writing. */
    fun isScribble(stroke: InkStroke): Boolean {
        val n = stroke.points.size / 2
        if (n < 8) return false
        val p = stroke.points
        var length = 0f
        var minX = p[0]
        var maxX = p[0]
        var minY = p[1]
        var maxY = p[1]
        // Segment directions with their angles doubled, so going one way and coming back
        // along the same line add up instead of cancelling out.
        var sumCos = 0.0
        var sumSin = 0.0
        for (i in 1 until n) {
            val dx = p[2 * i] - p[2 * i - 2]
            val dy = p[2 * i + 1] - p[2 * i - 1]
            val segment = hypot(dx, dy)
            length += segment
            if (segment > 0f) {
                val angle = 2.0 * atan2(dy.toDouble(), dx.toDouble())
                sumCos += segment * cos(angle)
                sumSin += segment * sin(angle)
            }
            minX = minOf(minX, p[2 * i]); maxX = maxOf(maxX, p[2 * i])
            minY = minOf(minY, p[2 * i + 1]); maxY = maxOf(maxY, p[2 * i + 1])
        }
        val diagonal = hypot(maxX - minX, maxY - minY).coerceAtLeast(1f)
        if (length < diagonal * MIN_DENSITY) return false
        val consistency = hypot(sumCos, sumSin) / length
        if (consistency < MIN_CONSISTENCY) return false
        // Count the back-and-forths along that main line, so scrubs at any angle work.
        val axis = atan2(sumSin, sumCos) / 2
        val ux = cos(axis).toFloat()
        val uy = sin(axis).toFloat()
        val along = FloatArray(n) { i -> p[2 * i] * ux + p[2 * i + 1] * uy }
        val extent = along.max() - along.min()
        return reversals(along, minSwing = maxOf(MIN_SWING, extent * MIN_PASS_FRACTION)) >= MIN_REVERSALS
    }

    /** Counts direction changes in [values] where they swing back at least [minSwing] from the furthest point reached. */
    private fun reversals(values: FloatArray, minSwing: Float): Int {
        var count = 0
        var direction = 0
        var extreme = values[0]
        for (i in 1 until values.size) {
            val v = values[i]
            val delta = v - extreme
            when {
                direction >= 0 && delta > 0 -> { extreme = v; direction = 1 }
                direction <= 0 && delta < 0 -> { extreme = v; direction = -1 }
                abs(delta) >= minSwing -> {
                    count++
                    direction = -direction
                    extreme = v
                }
            }
        }
        return count
    }

    /**
     * The strokes in [candidates] a scribble wipes out: those it touches whose
     * points lie mostly inside the scribbled area, so crossing the tail of a
     * long line leaves the line alone.
     */
    fun erasedBy(scribble: InkStroke, candidates: List<InkStroke>, touchRadius: Float): List<InkStroke> {
        val p = scribble.points
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in p.indices step 2) {
            minX = minOf(minX, p[i]); maxX = maxOf(maxX, p[i])
            minY = minOf(minY, p[i + 1]); maxY = maxOf(maxY, p[i + 1])
        }
        val margin = scribble.width + touchRadius
        return candidates.filter { stroke ->
            val n = stroke.points.size / 2
            val inside = (0 until n).count { i ->
                stroke.points[2 * i] in (minX - margin)..(maxX + margin) &&
                    stroke.points[2 * i + 1] in (minY - margin)..(maxY + margin)
            }
            inside * 2 >= n && touches(scribble, stroke, touchRadius)
        }
    }

    private fun touches(scribble: InkStroke, stroke: InkStroke, radius: Float): Boolean {
        val reach = radius + (scribble.width + stroke.width) / 2
        val s = scribble.points
        val t = stroke.points
        for (i in s.indices step 2) {
            for (j in t.indices step 2) {
                if (hypot(s[i] - t[j], s[i + 1] - t[j + 1]) <= reach) return true
            }
        }
        return false
    }
}

/**
 * Strike out to erase: one straight, roughly level line drawn through the middle of words
 * deletes them (as on iPad), alongside scrubbing. A line under or over words (underlining) or
 * across empty paper stays as ink.
 */
object StrikeOut {
    /** How much longer than straight the line may be; a hand-drawn straight line wobbles a little. */
    private const val MAX_BEND = 1.12f
    /** How far from level the line may lean, in degrees. */
    private const val MAX_TILT_DEG = 25.0
    private const val MIN_LENGTH = 20f
    /** The line must cross a stroke between these fractions of its height: its middle, not its top or bottom. */
    private const val BAND_LOW = 0.2f
    private const val BAND_HIGH = 0.8f
    /** At least this share of a stroke's width must lie along the line for it to count as struck. */
    private const val MIN_COVER = 0.5f
    /**
     * A strike-out is long next to the writing it crosses, and that writing is word-shaped (wider
     * than tall). The bar of a t or f is short next to its tall, narrow stem, so it stays a letter.
     */
    private const val MIN_LENGTH_TO_HEIGHT = 2f
    private const val MIN_WORD_WIDTH_TO_HEIGHT = 1.2f

    /** True if [stroke] is a straight, roughly level line: maybe a strike-out. */
    fun isLine(stroke: InkStroke): Boolean {
        val p = stroke.points
        val n = p.size / 2
        if (n < 2) return false
        val dx = p[2 * n - 2] - p[0]
        val dy = p[2 * n - 1] - p[1]
        val span = hypot(dx, dy)
        if (span < MIN_LENGTH) return false
        var length = 0f
        for (i in 1 until n) length += hypot(p[2 * i] - p[2 * i - 2], p[2 * i + 1] - p[2 * i - 1])
        if (length > span * MAX_BEND) return false
        val tilt = Math.toDegrees(atan2(abs(dy).toDouble(), abs(dx).toDouble()))
        return tilt <= MAX_TILT_DEG
    }

    /** The strokes in [candidates] that [line] strikes out: the words it crosses through the middle, dots and bars included. */
    fun struckBy(line: InkStroke, candidates: List<InkStroke>): List<InkStroke> {
        val p = line.points
        val n = p.size / 2
        val x0 = minOf(p[0], p[2 * n - 2])
        val x1 = maxOf(p[0], p[2 * n - 2])
        // The line's height at x, straight between its ends.
        fun lineY(x: Float): Float {
            val (ax, ay, bx, by) = listOf(p[0], p[1], p[2 * n - 2], p[2 * n - 1])
            if (bx == ax) return (ay + by) / 2
            return ay + (by - ay) * ((x - ax) / (bx - ax)).coerceIn(0f, 1f)
        }
        val margin = line.width
        fun cover(b: Shapes.Bounds) = if (b.width <= 0f) {
            if (b.left in (x0 - margin)..(x1 + margin)) 1f else 0f
        } else {
            (minOf(b.right, x1 + margin) - maxOf(b.left, x0 - margin)).coerceAtLeast(0f) / b.width
        }
        val bounds = candidates.associateWith { Shapes.Bounds.of(it.points) }
        // Strokes crossed through their middle.
        val crossed = candidates.filter { stroke ->
            val b = bounds.getValue(stroke)
            if (cover(b) < MIN_COVER) return@filter false
            val y = lineY(b.centerX)
            val low = b.top + b.height * BAND_LOW - stroke.width / 2
            val high = b.top + b.height * BAND_HIGH + stroke.width / 2
            y in low..high
        }
        if (crossed.isEmpty()) return emptyList()
        // The words' height: anything along the line within it goes too (i dots, t bars, accents).
        val top = crossed.minOf { bounds.getValue(it).top }
        val bottom = crossed.maxOf { bounds.getValue(it).bottom }
        val height = (bottom - top).coerceAtLeast(1f)
        val wordWidth = crossed.maxOf { bounds.getValue(it).right } - crossed.minOf { bounds.getValue(it).left }
        if (x1 - x0 < height * MIN_LENGTH_TO_HEIGHT || wordWidth < height * MIN_WORD_WIDTH_TO_HEIGHT) return emptyList()
        val slack = (bottom - top) * 0.3f
        return candidates.filter { stroke ->
            val b = bounds.getValue(stroke)
            stroke in crossed || (cover(b) >= MIN_COVER && b.centerY in (top - slack)..(bottom + slack))
        }
    }
}
