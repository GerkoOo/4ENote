package com.gerko.fourenote.data.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScribbleTest {
    private fun stroke(id: String, vararg xy: Float) = InkStroke(0, 3f, false, xy, id = id)

    /** A zigzag scrub between x = [from] and [to], drifting down from [y]. */
    private fun scrub(from: Float, to: Float, y: Float, passes: Int): InkStroke {
        val points = ArrayList<Float>()
        for (k in 0..passes) {
            val x0 = if (k % 2 == 0) from else to
            val x1 = if (k % 2 == 0) to else from
            for (s in 0..4) {
                points += x0 + (x1 - x0) * s / 4f
                points += y + k * 2f
            }
        }
        return stroke("scrub", *points.toFloatArray())
    }

    /** A word-sized wiggle: ink a scribble could cover. */
    private fun word(id: String, x: Float, y: Float) =
        stroke(id, x, y, x + 10, y - 8, x + 20, y, x + 30, y - 8, x + 40, y)

    @Test
    fun backAndForthScrubIsAScribble() {
        assertTrue(Scribble.isScribble(scrub(0f, 60f, 0f, passes = 6)))
    }

    @Test
    fun ordinaryHandwritingIsNot() {
        // A long wavy line, like cursive writing: lots of ups and downs but it keeps moving forward.
        val wave = stroke("wave", *FloatArray(80) { i -> if (i % 2 == 0) i * 4f else if ((i / 2) % 2 == 0) 0f else 12f })
        assertFalse(Scribble.isScribble(wave))
        assertFalse(Scribble.isScribble(stroke("line", 0f, 0f, 50f, 0f, 100f, 0f, 150f, 0f, 200f, 0f, 250f, 0f, 300f, 0f, 350f, 0f)))
    }

    @Test
    fun longCursiveWordIsNotAScribble() {
        // Joined-up writing like "minimum": the pen keeps moving right but each letter loops back
        // a little (x goes backwards by about 10 units per loop) while going up and down.
        val points = ArrayList<Float>()
        var t = 0.0
        while (t < 40 * Math.PI) {
            points += (3.0 * t + 12.0 * Math.sin(t)).toFloat()
            points += (20.0 * Math.cos(t)).toFloat()
            t += 0.2
        }
        assertFalse(Scribble.isScribble(stroke("cursive", *points.toFloatArray())))
    }

    @Test
    fun scrubOverAShortWordStillCounts() {
        // Passes that cover a bit less than the full width each time, like a real hurried scrub.
        val points = floatArrayOf(0f, 0f, 50f, 2f, 8f, 4f, 55f, 6f, 5f, 8f, 48f, 10f, 2f, 12f, 52f, 14f, 6f, 16f, 50f, 18f)
        assertTrue(Scribble.isScribble(stroke("scrub", *points)))
    }

    @Test
    fun scribbleErasesTheWordUnderItButNotNeighbours() {
        val under = word("under", 10f, 6f)
        val elsewhere = word("elsewhere", 300f, 6f)
        // A long line whose start runs through the scribble: it is touched, but mostly outside.
        val longLine = stroke("long", *FloatArray(40) { i -> if (i % 2 == 0) 20f + i * 20f else 4f })
        val erased = Scribble.erasedBy(scrub(0f, 60f, 0f, 6), listOf(under, elsewhere, longLine), touchRadius = 4f)
        assertEquals(listOf("under"), erased.map { it.id })
    }
}
