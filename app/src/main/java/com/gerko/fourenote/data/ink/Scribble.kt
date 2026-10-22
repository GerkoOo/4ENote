package com.gerko.fourenote.data.ink

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Scribble to erase: scrubbing back and forth over ink with the pen deletes
 * that ink instead of adding the scribble.
 */
object Scribble {
    private const val MIN_REVERSALS = 4
    /** How much longer the scribble's path must be than its bounding box's diagonal. */
    private const val MIN_DENSITY = 3f
    /** Movement shorter than this (page units) never counts as a reversal, so jitter is ignored. */
    private const val MIN_SWING = 6f

    /**
     * A reversal only counts if the pass it ends spans at least this share of
     * the scribble's extent. Scrubbing sweeps nearly edge to edge every pass;
     * the backward loops of cursive letters (e, a, l) and a t crossed in one go
     * reach back only a third of a short word or so.
     */
    private const val MIN_PASS_FRACTION = 0.6f

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
        for (i in 1 until n) {
            length += hypot(p[2 * i] - p[2 * i - 2], p[2 * i + 1] - p[2 * i - 1])
            minX = minOf(minX, p[2 * i]); maxX = maxOf(maxX, p[2 * i])
            minY = minOf(minY, p[2 * i + 1]); maxY = maxOf(maxY, p[2 * i + 1])
        }
        val diagonal = hypot(maxX - minX, maxY - minY).coerceAtLeast(1f)
        if (length < diagonal * MIN_DENSITY) return false
        // Count reversals along the scribble's main direction (usually sideways).
        val axis = if (maxX - minX >= maxY - minY) 0 else 1
        val extent = if (axis == 0) maxX - minX else maxY - minY
        return reversals(p, axis, minSwing = maxOf(MIN_SWING, extent * MIN_PASS_FRACTION)) >= MIN_REVERSALS
    }

    /**
     * Counts direction changes along [axis] where the pen swings back at least
     * [minSwing] from the furthest point it reached.
     */
    private fun reversals(points: FloatArray, axis: Int, minSwing: Float): Int {
        var count = 0
        var direction = 0
        var extreme = points[axis]
        for (i in 1 until points.size / 2) {
            val v = points[2 * i + axis]
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
