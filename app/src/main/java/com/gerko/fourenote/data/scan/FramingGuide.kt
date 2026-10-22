package com.gerko.fourenote.data.scan

import kotlin.math.abs

/**
 * Tells the scanner when the page is framed well, so its outline can turn green: a page has been
 * found, it holds still for [framesNeeded] frames in a row, and it fills a fair part of the view.
 * It never takes the picture; that is always the user's tap. Corners are fractions of the frame
 * (0..1), as x, y pairs.
 */
class FramingGuide(
    private val framesNeeded: Int = 8,
    private val tolerance: Float = 0.02f,
    private val minArea: Float = 0.2f,
) {
    private var last: FloatArray? = null
    private var steadyFrames = 0

    /** Feeds one frame's page (or null when none was found); true while the framing is good. */
    fun onFrame(corners: FloatArray?): Boolean {
        if (corners == null) {
            last = null
            steadyFrames = 0
            return false
        }
        steadyFrames = if (last?.let { biggestMove(it, corners) < tolerance } == true) steadyFrames + 1 else 0
        last = corners
        return steadyFrames >= framesNeeded && area(corners) >= minArea
    }

    private fun biggestMove(a: FloatArray, b: FloatArray): Float {
        var most = 0f
        for (i in a.indices) most = maxOf(most, abs(a[i] - b[i]))
        return most
    }

    // The quadrilateral's area (shoelace formula), as a fraction of the frame.
    private fun area(p: FloatArray): Float {
        var sum = 0f
        for (k in 0 until 4) {
            val j = (k + 1) % 4
            sum += p[2 * k] * p[2 * j + 1] - p[2 * j] * p[2 * k + 1]
        }
        return abs(sum) / 2
    }
}
