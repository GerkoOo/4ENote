package com.gerko.fourenote.data.recognition

import com.gerko.fourenote.data.ink.InkStroke

/** Splits handwriting into lines of text, since recognition works one line at a time. */
object HandwritingLines {
    data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width get() = right - left
        val height get() = bottom - top
        val centerY get() = (top + bottom) / 2
    }

    fun bounds(strokes: List<InkStroke>): Bounds {
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (stroke in strokes) {
            for (i in stroke.points.indices step 2) {
                left = minOf(left, stroke.points[i])
                right = maxOf(right, stroke.points[i])
                top = minOf(top, stroke.points[i + 1])
                bottom = maxOf(bottom, stroke.points[i + 1])
            }
        }
        return Bounds(left, top, right, bottom)
    }

    /**
     * Groups strokes into lines, top line first. A stroke joins a line when its
     * vertical middle falls within that line's height; tall strokes such as
     * the loop of a "g" or "l" therefore stay with their line. Within a line
     * the original drawing order is kept, which is the order the pen moved in.
     */
    fun split(strokes: List<InkStroke>): List<List<InkStroke>> {
        val lines = mutableListOf<MutableList<InkStroke>>()
        val lineBounds = mutableListOf<Bounds>()
        val byTop = strokes.sortedBy { bounds(listOf(it)).top }
        for (stroke in byTop) {
            val b = bounds(listOf(stroke))
            val index = lineBounds.indexOfFirst { b.centerY in it.top..it.bottom }
            if (index < 0) {
                lines += mutableListOf(stroke)
                lineBounds += b
            } else {
                lines[index] += stroke
                val l = lineBounds[index]
                lineBounds[index] = Bounds(minOf(l.left, b.left), minOf(l.top, b.top), maxOf(l.right, b.right), maxOf(l.bottom, b.bottom))
            }
        }
        val drawingOrder = strokes.withIndex().associate { it.value.id to it.index }
        return lines.indices.sortedBy { lineBounds[it].top }
            .map { i -> lines[i].sortedBy { drawingOrder.getValue(it.id) } }
    }
}
