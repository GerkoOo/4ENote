package com.gerko.fourenote.data.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class PageFinderTest {
    private val width = 300
    private val height = 400

    /** A photo: a bright sheet with corners [quad] (x, y pairs, clockwise from top-left) on a darker desk, with noise and "writing". */
    private fun photo(quad: FloatArray, paper: Int = 220, desk: Int = 70): IntArray {
        val random = Random(3)
        return IntArray(width * height) { i ->
            val x = i % width + 0.5f
            val y = i / width + 0.5f
            var inside = true
            for (k in 0 until 4) {
                val ax = quad[2 * k]; val ay = quad[2 * k + 1]
                val bx = quad[(2 * k + 2) % 8]; val by = quad[(2 * k + 3) % 8]
                if ((bx - ax) * (y - ay) - (by - ay) * (x - ax) < 0) inside = false
            }
            val base = if (inside) paper else desk
            // Ink lines across the sheet and sensor noise.
            val ink = inside && (i / width) % 37 == 0 && (i % width) % 5 != 0
            ((if (ink) 40 else base) + random.nextInt(-12, 12)).coerceIn(0, 255)
        }
    }

    private fun assertCorners(expected: FloatArray, found: PageFinder.Corners?) {
        assertNotNull(found)
        for (i in 0 until 8) assertEquals("coordinate $i", expected[i], found!!.points[i], 6f)
    }

    @Test
    fun findsAStraightSheet() {
        val quad = floatArrayOf(40f, 50f, 260f, 50f, 260f, 350f, 40f, 350f)
        assertCorners(quad, PageFinder.find(photo(quad), width, height))
    }

    @Test
    fun findsATiltedSheetPhotographedAtAnAngle() {
        // Wider at the bottom, as when the camera leans over the page.
        val quad = floatArrayOf(70f, 40f, 240f, 60f, 280f, 370f, 20f, 340f)
        assertCorners(quad, PageFinder.find(photo(quad), width, height))
    }

    @Test
    fun findsADarkSheetOnALightTable() {
        val quad = floatArrayOf(50f, 60f, 250f, 60f, 250f, 340f, 50f, 340f)
        assertCorners(quad, PageFinder.find(photo(quad, paper = 60, desk = 210), width, height))
    }

    @Test
    fun givesUpWhenNoSheetStandsOut() {
        // The whole photo is paper (a close-up): nothing to straighten.
        val all = floatArrayOf(-5f, -5f, 305f, -5f, 305f, 405f, -5f, 405f)
        assertNull(PageFinder.find(photo(all), width, height))
    }
}
