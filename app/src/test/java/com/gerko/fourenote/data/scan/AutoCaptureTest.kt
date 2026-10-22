package com.gerko.fourenote.data.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCaptureTest {
    private val page = floatArrayOf(0.2f, 0.2f, 0.8f, 0.2f, 0.8f, 0.8f, 0.2f, 0.8f)
    private fun shifted(by: Float) = FloatArray(8) { page[it] + by }

    /** Frames until the first capture, or -1. */
    private fun AutoCapture.framesUntilCapture(frame: () -> FloatArray?, limit: Int = 100): Int {
        for (i in 1..limit) if (onFrame(frame())) return i
        return -1
    }

    @Test
    fun capturesOnceThePageHoldsStill() {
        val auto = AutoCapture(framesNeeded = 10)
        assertEquals(11, auto.framesUntilCapture({ page }))
    }

    @Test
    fun aShakyPageIsNotCaptured() {
        val auto = AutoCapture(framesNeeded = 10)
        var k = 0
        assertEquals(-1, auto.framesUntilCapture({ shifted(if (k++ % 2 == 0) 0f else 0.05f) }))
    }

    @Test
    fun doesNotCaptureTheSamePageTwice() {
        val auto = AutoCapture(framesNeeded = 5)
        assertTrue(auto.framesUntilCapture({ page }) > 0)
        assertEquals(-1, auto.framesUntilCapture({ page }, limit = 50))
    }

    @Test
    fun capturesTheNextPageAfterTheFirstIsTakenAway() {
        val auto = AutoCapture(framesNeeded = 5)
        assertTrue(auto.framesUntilCapture({ page }) > 0)
        assertFalse(auto.onFrame(null)) // page lifted off
        assertEquals(6, auto.framesUntilCapture({ page }))
    }

    @Test
    fun aMovedPageCountsAsANewOne() {
        val auto = AutoCapture(framesNeeded = 5)
        assertTrue(auto.framesUntilCapture({ page }) > 0)
        assertTrue(auto.framesUntilCapture({ shifted(0.2f) }) > 0)
    }
}
