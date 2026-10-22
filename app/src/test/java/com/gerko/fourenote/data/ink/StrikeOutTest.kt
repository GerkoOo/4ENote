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
    fun aLineThroughTheMiddleStrikesTheWordsOut() {
        val hello = word("hello", 0f, 100f)
        val world = word("world", 70f, 100f)
        val dot = stroke("dot", 30f, 92f, 31f, 92f) // an i dot just above
        val other = word("other", 300f, 100f)
        val struck = StrikeOut.struckBy(line(-5f, 115f, 135f, 116f), listOf(hello, world, dot, other))
        assertEquals(setOf("hello", "world", "dot"), struck.map { it.id }.toSet())
    }

    @Test
    fun aWordWrittenInOneStrokeNeedsMoreThanOneStrokeCrossed() {
        assertTrue(StrikeOut.struckBy(line(-5f, 115f, 65f, 116f), listOf(word("hello", 0f, 100f))).isEmpty())
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

    @Test
    fun theBarOfATOrFIsNotAStrikeOut() {
        // A t: a tall stem, then a short bar across its middle.
        val stem = stroke("stem", 20f, 100f, 20f, 115f, 20f, 130f, 22f, 140f, 28f, 142f)
        assertTrue(StrikeOut.struckBy(line(10f, 112f, 32f, 112f), listOf(stem)).isEmpty())
        // An f with a loop, crossed by a slightly longer bar.
        val f = stroke("f", 30f, 105f, 24f, 100f, 20f, 110f, 20f, 140f, 18f, 160f, 12f, 162f)
        assertTrue(StrikeOut.struckBy(line(8f, 125f, 34f, 124f), listOf(f)).isEmpty())
        // The t's bar drawn next to the word "hello" written earlier doesn't erase it either.
        val hello = word("hello", 40f, 100f)
        assertTrue(StrikeOut.struckBy(line(10f, 112f, 32f, 112f), listOf(hello, stem)).isEmpty())
    }
}
