package com.gerko.fourenote.data.ink

import kotlin.math.abs
import kotlin.math.hypot

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
     * How far apart (as a share of the extent) the pen's turning points on each side may be.
     * A scrub turns back at about the same place every pass; joined-up writing loops back
     * too, but each loop turns further along the word.
     */
    private const val MAX_TURN_SPREAD = 0.3f

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
        val turns = turns(p, axis, minSwing = maxOf(MIN_SWING, extent * MIN_PASS_FRACTION))
        if (turns.size < MIN_REVERSALS) return false
        val spread = turns.groupBy { it.second }.values.maxOf { side -> side.maxOf { it.first } - side.minOf { it.first } }
        return spread <= extent * MAX_TURN_SPREAD
    }

    /**
     * Where along [axis] the pen turned back after swinging at least [minSwing] from the
     * furthest point it reached, and which way it was going then (1 forward, -1 back).
     */
    private fun turns(points: FloatArray, axis: Int, minSwing: Float): List<Pair<Float, Int>> {
        val turns = ArrayList<Pair<Float, Int>>()
        var direction = 0
        var extreme = points[axis]
        for (i in 1 until points.size / 2) {
            val v = points[2 * i + axis]
            val delta = v - extreme
            when {
                direction >= 0 && delta > 0 -> { extreme = v; direction = 1 }
                direction <= 0 && delta < 0 -> { extreme = v; direction = -1 }
                abs(delta) >= minSwing -> {
                    turns += extreme to direction
                    direction = -direction
                    extreme = v
                }
            }
        }
        return turns
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
