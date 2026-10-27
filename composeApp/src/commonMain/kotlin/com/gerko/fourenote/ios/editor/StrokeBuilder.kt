package com.gerko.fourenote.ios.editor

import com.gerko.fourenote.data.ink.InkPen
import com.gerko.fourenote.data.ink.InkStroke
import com.gerko.fourenote.data.ink.Smoothing

/**
 * Collects the points of one stroke while the pen is down, then turns them into an [InkStroke]
 * when it lifts. Kept separate from the editor so the maths can be tested without a UI.
 */
class StrokeBuilder {
    private val xs = ArrayList<Float>()
    private val ys = ArrayList<Float>()
    private val times = ArrayList<Long>()
    private val pressures = ArrayList<Float>()

    val isEmpty: Boolean get() = xs.isEmpty()

    fun start(x: Float, y: Float, timeMs: Long, pressure: Float) {
        xs.clear(); ys.clear(); times.clear(); pressures.clear()
        add(x, y, timeMs, pressure)
    }

    fun add(x: Float, y: Float, timeMs: Long, pressure: Float) {
        // A repeated point adds nothing but noise to the smoothing.
        if (xs.isNotEmpty() && xs.last() == x && ys.last() == y) return
        xs += x
        ys += y
        times += timeMs
        pressures += pressure
    }

    /** The raw points so far, for drawing the stroke while it is still being written. */
    fun livePoints(): FloatArray {
        val out = FloatArray(xs.size * 2)
        for (i in xs.indices) {
            out[2 * i] = xs[i]
            out[2 * i + 1] = ys[i]
        }
        return out
    }

    /**
     * The finished stroke, or null if nothing was drawn. A single tap becomes a two-point dot so
     * it still shows up as a mark.
     */
    fun finish(tool: EditorTool, color: Int, width: Float): InkStroke? {
        if (xs.isEmpty()) return null
        // A tap: a two-point dot, so it still shows as a mark.
        if (xs.size == 1) add(xs[0] + 0.01f, ys[0], times[0], pressures[0])
        val raw = livePoints()
        val rawTimes = times.toLongArray()
        val rawPressures = pressures.toFloatArray()
        return when (tool) {
            EditorTool.PEN -> {
                val smoothed = if (raw.size >= 6) Smoothing.smoothStroke(raw, SMOOTHING_LEVEL) else raw
                val widths = InkPen.resampleWidths(raw, InkPen.widthFactors(raw, rawTimes, rawPressures), smoothed)
                InkStroke(color = color, width = width, highlighter = false, points = smoothed, widths = widths)
            }
            EditorTool.HIGHLIGHTER -> {
                InkStroke(
                    color = color and 0x00FFFFFF or (HIGHLIGHTER_ALPHA shl 24),
                    width = width * HIGHLIGHTER_WIDTH,
                    highlighter = true,
                    points = raw,
                )
            }
            EditorTool.ERASER -> null
        }
    }

    private companion object {
        const val SMOOTHING_LEVEL = 2
        const val HIGHLIGHTER_WIDTH = 6f
        const val HIGHLIGHTER_ALPHA = 0x66
    }
}
