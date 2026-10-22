package com.gerko.fourenote.data.scan

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.nio.FloatBuffer
import kotlin.math.exp

/**
 * Finds a document's four corners with DocQuadNet, a small neural network trained for exactly
 * this by the MakeACopy project (github.com/egdels/makeacopy, Apache License 2.0; the model file
 * in assets/docquad comes unchanged from its commit cdf0e66). It sees the photo shrunk into a
 * 256 x 256 square (black bars keep its shape) and answers with a heatmap per corner (top-left,
 * top-right, bottom-right, bottom-left); each corner is where its map peaks, and the peak's
 * strength says how sure it is. Runs on the device with ONNX Runtime.
 */
class EdgeModel(private val context: Context) {
    private var session: OrtSession? = null

    /** The page's corners in [photo], or null when the model isn't sure there is one. */
    @Synchronized
    fun find(photo: Bitmap): PageFinder.Corners? {
        val env = OrtEnvironment.getEnvironment()
        val model = session ?: env.createSession(
            context.assets.open(ASSET).use { it.readBytes() },
            OrtSession.SessionOptions(),
        ).also { session = it }

        // Letterbox: the whole photo, centred, on black.
        val scale = SIZE.toFloat() / maxOf(photo.width, photo.height)
        val w = photo.width * scale
        val h = photo.height * scale
        val left = (SIZE - w) / 2
        val top = (SIZE - h) / 2
        val square = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        Canvas(square).apply {
            drawColor(Color.BLACK)
            drawBitmap(photo, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val pixels = IntArray(SIZE * SIZE)
        square.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        square.recycle()
        val plane = SIZE * SIZE
        val input = FloatArray(3 * plane)
        for (i in pixels.indices) {
            val c = pixels[i]
            input[i] = (c shr 16 and 0xFF) / 255f
            input[plane + i] = (c shr 8 and 0xFF) / 255f
            input[2 * plane + i] = (c and 0xFF) / 255f
        }

        val heatmaps = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())).use { tensor ->
            model.run(mapOf("input" to tensor)).use { out ->
                @Suppress("UNCHECKED_CAST")
                (out.get("corner_heatmaps").get().value as Array<Array<Array<FloatArray>>>)[0]
            }
        }
        val points = FloatArray(8)
        for (k in 0 until 4) {
            val map = heatmaps[k]
            var bestX = 0
            var bestY = 0
            for (y in map.indices) for (x in map[y].indices) if (map[y][x] > map[bestY][bestX]) { bestX = x; bestY = y }
            if (sigmoid(map[bestY][bestX]) < MIN_CONFIDENCE) return null
            // Refine within the peak's neighbourhood, weighted by confidence.
            var sum = 0f
            var sx = 0f
            var sy = 0f
            for (dy in -1..1) for (dx in -1..1) {
                val yy = bestY + dy
                val xx = bestX + dx
                if (yy !in map.indices || xx !in map[yy].indices) continue
                val weight = sigmoid(map[yy][xx])
                sum += weight
                sx += weight * xx
                sy += weight * yy
            }
            val gx = sx / sum
            val gy = sy / sum
            // Heatmap cell to the 256 square, then back out of the letterbox into the photo.
            points[2 * k] = (((gx + 0.5f) * CELL - left) / scale).coerceIn(0f, photo.width.toFloat())
            points[2 * k + 1] = (((gy + 0.5f) * CELL - top) / scale).coerceIn(0f, photo.height.toFloat())
        }
        return PageFinder.Corners(points).takeIf { isPlausible(it, photo.width, photo.height) }
    }

    fun close() {
        session?.close()
        session = null
    }

    /** A convex quadrilateral in top-left, top-right, bottom-right, bottom-left order, of a sensible size. */
    private fun isPlausible(corners: PageFinder.Corners, width: Int, height: Int): Boolean {
        val p = corners.points
        var sign = 0
        var area = 0f
        for (k in 0 until 4) {
            val ax = p[2 * k]; val ay = p[2 * k + 1]
            val bx = p[(2 * k + 2) % 8]; val by = p[(2 * k + 3) % 8]
            val cx = p[(2 * k + 4) % 8]; val cy = p[(2 * k + 5) % 8]
            val cross = (bx - ax) * (cy - by) - (by - ay) * (cx - bx)
            val s = if (cross > 0) 1 else -1
            if (sign == 0) sign = s else if (s != sign) return false
            area += ax * by - bx * ay
        }
        // Clockwise on screen (y down) and at least a tenth of the photo.
        return sign > 0 && area / 2 >= 0.1f * width * height
    }

    private fun sigmoid(x: Float) = 1f / (1f + exp(-x))

    private companion object {
        const val ASSET = "docquad/docquadnet256_trained_opset17.ort"
        const val SIZE = 256
        const val CELL = 4f // 256 / 64 heatmap cells
        const val MIN_CONFIDENCE = 0.3f
    }
}
