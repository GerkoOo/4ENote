package com.gerko.fourenote.data.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrikeOutTest {
    private fun stroke(id: String, vararg xy: Float) = InkStroke(0, 3f, false, xy, id = id)

    // A wavy "word" 60 wide and 30 tall, from (x, y).
    private fun word(id: String, x: Float, y: Float) =
        stroke(id, x, y + 30f, x + 10f, y, x + 20f, y + 30f, x + 30f, y, x + 40f, y + 30f, x + 50f, y, x + 60f, y + 30f)

    private fun line(x0: Float, y0: Float, x1: Float, y1: Float) =
        stroke("line", *FloatArray(22) { k -> if (k % 2 == 0) x0 + (x1 - x0) * (k / 2) / 10f else y0 + (y1 - y0) * (k / 2) / 10f })

    @Test
    fun aStraightLevelLineIsALine() {
        assertTrue(StrikeOut.isLine(line(0f, 100f, 200f, 104f)))
        assertTrue(StrikeOut.isLine(line(200f, 100f, 0f, 110f)))
        assertFalse(StrikeOut.isLine(line(0f, 0f, 100f, 100f))) // too steep
        assertFalse(StrikeOut.isLine(word("w", 0f, 0f))) // writing bends too much
        assertFalse(StrikeOut.isLine(line(0f, 0f, 10f, 0f))) // too short
    }

    @Test
    fun aLineThroughTheMiddleStrikesTheWordOut() {
        val hello = word("hello", 0f, 100f)
        val dot = stroke("dot", 30f, 92f, 31f, 92f) // an i dot just above
        val other = word("other", 200f, 100f)
        val struck = StrikeOut.struckBy(line(-5f, 115f, 65f, 116f), listOf(hello, dot, other))
        assertEquals(setOf("hello", "dot"), struck.map { it.id }.toSet())
    }

    @Test
    fun underliningKeepsTheWords() {
        val hello = word("hello", 0f, 100f)
        assertTrue(StrikeOut.struckBy(line(-5f, 134f, 65f, 134f), listOf(hello)).isEmpty())
        assertTrue(StrikeOut.struckBy(line(-5f, 98f, 65f, 98f), listOf(hello)).isEmpty())
    }

    @Test
    fun aLineOnEmptyPaperErasesNothing() {
        assertTrue(StrikeOut.struckBy(line(0f, 300f, 100f, 300f), listOf(word("hello", 0f, 100f))).isEmpty())
    }
}
