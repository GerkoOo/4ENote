package com.gerko.fourenote.data.scan

import kotlin.math.abs

/**
 * Decides when the scanner takes a picture by itself: once the page found in the camera's
 * frames has held still for [framesNeeded] frames in a row. After a capture it waits until the
 * page is taken away or clearly moved (the next page laid down) before capturing again, so one
 * sheet is never captured twice. Corners are fractions of the frame (0..1), as x, y pairs.
 */
class AutoCapture(private val framesNeeded: Int = 12, private val tolerance: Float = 0.02f) {
    private var last: FloatArray? = null
    private var steadyFrames = 0
    private var armed = true
    private var captured: FloatArray? = null

    /** How close the page is to being captured, 0..1, for the on-screen ring. */
    val progress: Float get() = if (armed) steadyFrames.toFloat() / framesNeeded else 0f

    /** Feeds one frame's page (or null when none was found); true means take the picture now. */
    fun onFrame(corners: FloatArray?): Boolean {
        if (corners == null) {
            last = null
            steadyFrames = 0
            armed = true
            return false
        }
        if (!armed) {
            val moved = captured?.let { biggestMove(it, corners) } ?: 1f
            if (moved > REARM_MOVE) armed = true else return false
        }
        steadyFrames = if (last?.let { biggestMove(it, corners) < tolerance } == true) steadyFrames + 1 else 0
        last = corners
        if (steadyFrames < framesNeeded) return false
        armed = false
        captured = corners
        steadyFrames = 0
        return true
    }

    private fun biggestMove(a: FloatArray, b: FloatArray): Float {
        var most = 0f
        for (i in a.indices) most = maxOf(most, abs(a[i] - b[i]))
        return most
    }

    private companion object {
        /** A page this far (as a fraction of the frame) from the last capture is a new page. */
        const val REARM_MOVE = 0.12f
    }
}
