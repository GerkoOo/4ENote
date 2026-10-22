package com.gerko.fourenote.data.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class ShapesTest {
    /** Points along the given corners, with a hand's small wobble added. */
    private fun drawn(vararg corners: Float, wobble: Float = 2f): FloatArray {
        val clean = Shapes.polyline(corners)
        return FloatArray(clean.size) { i -> clean[i] + wobble * sin(i * 1.7f) }
    }

    private fun drawnEllipse(cx: Float, cy: Float, rx: Float, ry: Float, wobble: Float = 3f, turn: Double = 2 * PI): FloatArray {
        val n = 80
        return FloatArray(2 * (n + 1)) { k ->
            val a = turn * (k / 2) / n
            val noise = wobble * sin(k * 1.3f)
            if (k % 2 == 0) cx + (rx + noise) * cos(a).toFloat() else cy + (ry + noise) * sin(a).toFloat()
        }
    }

    private fun xs(points: FloatArray) = points.filterIndexed { i, _ -> i % 2 == 0 }
    private fun ys(points: FloatArray) = points.filterIndexed { i, _ -> i % 2 == 1 }

    @Test
    fun straightensALine() {
        val shape = checkNotNull(Shapes.recognize(drawn(10f, 10f, 300f, 120f)))
        val first = shape[0] to shape[1]
        val last = shape[shape.size - 2] to shape[shape.size - 1]
        // Every point sits on the straight line from first to last.
        for (i in shape.indices step 2) {
            val cross = (last.first - first.first) * (shape[i + 1] - first.second) - (last.second - first.second) * (shape[i] - first.first)
            assertEquals(0f, cross / hypot(last.first - first.first, last.second - first.second), 0.01f)
        }
    }

    @Test
    fun roundsACircle() {
        val shape = checkNotNull(Shapes.recognize(drawnEllipse(200f, 200f, 80f, 76f)))
        val cx = (xs(shape).min() + xs(shape).max()) / 2
        val cy = (ys(shape).min() + ys(shape).max()) / 2
        val radii = (shape.indices step 2).map { hypot(shape[it] - cx, shape[it + 1] - cy) }
        assertEquals(radii.min(), radii.max(), 1f)
    }

    @Test
    fun keepsAnEllipseOval() {
        val shape = checkNotNull(Shapes.recognize(drawnEllipse(200f, 200f, 120f, 60f)))
        assertEquals(240f, xs(shape).max() - xs(shape).min(), 12f)
        assertEquals(120f, ys(shape).max() - ys(shape).min(), 12f)
    }

    @Test
    fun squaresARectangle() {
        val shape = checkNotNull(Shapes.recognize(drawn(50f, 50f, 250f, 52f, 248f, 150f, 52f, 148f, 51f, 55f)))
        val left = xs(shape).min(); val right = xs(shape).max()
        val top = ys(shape).min(); val bottom = ys(shape).max()
        // Every point lies on one of the four straight edges.
        for (i in shape.indices step 2) {
            val onEdge = abs(shape[i] - left) < 0.01f || abs(shape[i] - right) < 0.01f ||
                abs(shape[i + 1] - top) < 0.01f || abs(shape[i + 1] - bottom) < 0.01f
            assertTrue(onEdge)
        }
        assertEquals(200f, right - left, 8f)
        assertEquals(100f, bottom - top, 8f)
    }

    @Test
    fun evensOutASquare() {
        val shape = checkNotNull(Shapes.recognize(drawn(50f, 50f, 200f, 50f, 200f, 190f, 50f, 190f, 50f, 54f)))
        assertEquals(xs(shape).max() - xs(shape).min(), ys(shape).max() - ys(shape).min(), 0.01f)
    }

    @Test
    fun straightensATriangle() {
        val shape = checkNotNull(Shapes.recognize(drawn(100f, 40f, 180f, 180f, 20f, 180f, 98f, 44f)))
        // Closed, and not squared off: its top is a point, not an edge.
        assertEquals(shape[0], shape[shape.size - 2], 0.01f)
        val top = ys(shape).min()
        val topXs = (shape.indices step 2).filter { abs(shape[it + 1] - top) < 1f }.map { shape[it] }
        assertTrue(topXs.max() - topXs.min() < 10f)
    }

    @Test
    fun leavesWritingAlone() {
        // A zigzag that doesn't close is neither a line nor a shape.
        assertNull(Shapes.recognize(drawn(0f, 0f, 40f, 60f, 80f, 0f, 120f, 60f, 160f, 0f)))
    }

    @Test
    fun spotsLoops() {
        assertTrue(Shapes.isLoop(drawnEllipse(100f, 100f, 90f, 60f)))
        assertFalse(Shapes.isLoop(drawnEllipse(100f, 100f, 90f, 60f, turn = PI)))
        assertFalse(Shapes.isLoop(drawn(0f, 0f, 300f, 10f)))
    }

    @Test
    fun containsPointsInsideALoop() {
        val loop = drawnEllipse(100f, 100f, 50f, 50f, wobble = 0f)
        assertTrue(Shapes.contains(loop, 100f, 100f))
        assertFalse(Shapes.contains(loop, 170f, 100f))
    }
}
