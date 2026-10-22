package com.gerko.fourenote.data.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FramingGuideTest {
    private val page = floatArrayOf(0.2f, 0.2f, 0.8f, 0.2f, 0.8f, 0.8f, 0.2f, 0.8f)
    private fun shifted(by: Float) = FloatArray(8) { page[it] + by }

    /** Frames until the framing first counts as good, or -1. */
    private fun FramingGuide.framesUntilGood(frame: () -> FloatArray?, limit: Int = 100): Int {
        for (i in 1..limit) if (onFrame(frame())) return i
        return -1
    }

    @Test
    fun goodOnceThePageHoldsStill() {
        assertEquals(11, FramingGuide(framesNeeded = 10).framesUntilGood({ page }))
    }

    @Test
    fun staysGoodWhileThePageStaysStill() {
        val guide = FramingGuide(framesNeeded = 5)
        assertTrue(guide.framesUntilGood({ page }) > 0)
        repeat(20) { assertTrue(guide.onFrame(page)) }
    }

    @Test
    fun aShakyPageIsNotGood() {
        var k = 0
        assertEquals(-1, FramingGuide(framesNeeded = 10).framesUntilGood({ shifted(if (k++ % 2 == 0) 0f else 0.05f) }))
    }

    @Test
    fun aTinyPageIsNotGood() {
        val tiny = floatArrayOf(0.45f, 0.45f, 0.55f, 0.45f, 0.55f, 0.55f, 0.45f, 0.55f)
        assertEquals(-1, FramingGuide(framesNeeded = 5).framesUntilGood({ tiny }))
    }

    @Test
    fun losingThePageResetsIt() {
        val guide = FramingGuide(framesNeeded = 5)
        assertTrue(guide.framesUntilGood({ page }) > 0)
        assertFalse(guide.onFrame(null))
        assertFalse(guide.onFrame(page))
    }
}
