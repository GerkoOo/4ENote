package com.gerko.fourenote.data.recognition

import com.gerko.fourenote.data.ink.InkStroke
import org.junit.Assert.assertEquals
import org.junit.Test

class HandwritingLinesTest {
    /** A small stroke spanning [x], [x]+10 horizontally and [top]..[bottom] vertically. */
    private fun stroke(id: String, x: Float, top: Float, bottom: Float) =
        InkStroke(0, 2f, false, floatArrayOf(x, top, x + 10f, bottom), id = id)

    @Test
    fun twoLinesComeOutTopFirstInDrawingOrder() {
        val strokes = listOf(
            stroke("second-line-a", 0f, 100f, 130f),
            stroke("first-line-a", 0f, 0f, 30f),
            stroke("first-line-b", 20f, 5f, 28f),
            stroke("second-line-b", 20f, 102f, 128f),
        )
        val lines = HandwritingLines.split(strokes).map { line -> line.map { it.id } }
        assertEquals(
            listOf(listOf("first-line-a", "first-line-b"), listOf("second-line-a", "second-line-b")),
            lines,
        )
    }

    @Test
    fun tallLettersStayOnTheirLine() {
        // An "l" reaching well above the line and a "g" tail dipping below it.
        val strokes = listOf(stroke("a", 0f, 10f, 30f), stroke("l", 15f, -15f, 30f), stroke("g", 30f, 10f, 50f))
        assertEquals(1, HandwritingLines.split(strokes).size)
    }
}
