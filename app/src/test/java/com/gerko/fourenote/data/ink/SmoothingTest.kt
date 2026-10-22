package com.gerko.fourenote.data.ink

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SmoothingTest {
    /**
     * A horizontal line with ±1dp shake on every point, like an unsteady hand.
     * It starts and ends on the line, as a real stroke starts where the pen lands.
     */
    private val shaky = FloatArray(200) { i ->
        val point = i / 2
        when {
            i % 2 == 0 -> point * 2f
            point == 0 || point == 99 -> 0f
            point % 2 == 0 -> 1f
            else -> -1f
        }
    }

    /** Average distance from the ideal straight line y = 0. */
    private fun meanDeviation(points: FloatArray) =
        (1 until points.size step 2).map { abs(points[it]) }.average()

    private fun runThroughStabilizer(level: Int): FloatArray {
        val stabilizer = Smoothing.Stabilizer(level)
        val out = FloatArray(shaky.size)
        for (i in shaky.indices step 2) {
            val (x, y) = stabilizer.next(shaky[i], shaky[i + 1])
            out[i] = x
            out[i + 1] = y
        }
        return out
    }

    @Test
    fun levelZeroKeepsRawInk() {
        assertArrayEquals(shaky, Smoothing.smoothStroke(shaky, 0), 0f)
        assertArrayEquals(shaky, runThroughStabilizer(0), 0f)
    }

    @Test
    fun higherLevelsRemoveMoreShake() {
        val raw = meanDeviation(shaky)
        val medium = meanDeviation(Smoothing.smoothStroke(runThroughStabilizer(4), 4))
        val high = meanDeviation(Smoothing.smoothStroke(runThroughStabilizer(10), 10))
        assertTrue("medium $medium should be below raw $raw", medium < raw)
        assertTrue("high $high should be below medium $medium", high < medium)
    }

    @Test
    fun strokeKeepsItsEndpoints() {
        val smooth = Smoothing.smoothStroke(shaky, 10)
        assertEquals(shaky[0], smooth[0], 0.01f)
        assertEquals(shaky[shaky.size - 2], smooth[smooth.size - 2], 0.01f)
    }

    @Test
    fun sharpCornersDontOvershoot() {
        // A "V" with a long and a short arm, densely sampled, like a fast pen stroke.
        val points = ArrayList<Float>()
        for (k in 0..60) { points += k * 2f; points += k * 3f }          // long arm down
        for (k in 1..8) { points += 120f + k * 2f; points += 180f - k * 6f } // short arm back up
        val raw = points.toFloatArray()
        for (level in listOf(4, 10)) {
            val smooth = Smoothing.smoothStroke(raw, level)
            var maxY = -Float.MAX_VALUE
            for (i in 1 until smooth.size step 2) maxY = maxOf(maxY, smooth[i])
            // The bottom of the V is at y = 180: the curve shouldn't dip far below it.
            println("level $level: lowest point ${maxY - 180f} below the corner")
            assertTrue("level $level overshoots by ${maxY - 180f}", maxY - 180f < 1.5f)
        }
    }
}
