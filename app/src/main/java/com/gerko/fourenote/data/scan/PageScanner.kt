package com.gerko.fourenote.data.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.hypot
import kotlin.math.roundToInt

/** How a scanned page is cleaned up. */
enum class ScanFilter(val label: String) {
    ORIGINAL("Original"),
    CLEAN("Clean"),
    BLACK_AND_WHITE("B&W"),
}

/**
 * The scanner's picture work, all with Android's own graphics (no extra library): finding the
 * sheet in a photo ([PageFinder]), straightening it, and cleaning it up.
 */
object PageScanner {
    private const val FIND_SIZE = 400
    private const val MAX_SIDE = 3000

    /** The sheet's corners in [photo], or corners just inside the photo's edges if none stands out. */
    fun findCorners(photo: Bitmap): PageFinder.Corners {
        val factor = FIND_SIZE.toFloat() / maxOf(photo.width, photo.height)
        val w = (photo.width * factor).roundToInt().coerceAtLeast(8)
        val h = (photo.height * factor).roundToInt().coerceAtLeast(8)
        val small = Bitmap.createScaledBitmap(photo, w, h, true)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        if (small !== photo) small.recycle()
        val luma = IntArray(pixels.size) { luminance(pixels[it]) }
        PageFinder.find(luma, w, h)?.let { return it.scaled(photo.width.toFloat() / w) }
        val mx = photo.width * 0.03f
        val my = photo.height * 0.03f
        return PageFinder.Corners(
            floatArrayOf(mx, my, photo.width - mx, my, photo.width - mx, photo.height - my, mx, photo.height - my),
        )
    }

    /** Straightens the sheet inside [corners] into an upright rectangle, then applies [filter]. */
    fun process(photo: Bitmap, corners: PageFinder.Corners, filter: ScanFilter, maxSide: Int = MAX_SIDE): Bitmap {
        val flat = straighten(photo, corners, maxSide)
        return when (filter) {
            ScanFilter.ORIGINAL -> flat
            ScanFilter.CLEAN -> clean(flat, blackAndWhite = false)
            ScanFilter.BLACK_AND_WHITE -> clean(flat, blackAndWhite = true)
        }
    }

    private fun straighten(photo: Bitmap, corners: PageFinder.Corners, maxSide: Int): Bitmap {
        val p = corners.points
        fun dist(a: Int, b: Int) = hypot(p[2 * a] - p[2 * b], p[2 * a + 1] - p[2 * b + 1])
        var width = (dist(0, 1) + dist(3, 2)) / 2
        var height = (dist(0, 3) + dist(1, 2)) / 2
        val shrink = minOf(1f, maxSide / maxOf(width, height))
        width = (width * shrink).coerceAtLeast(1f)
        height = (height * shrink).coerceAtLeast(1f)
        val matrix = Matrix()
        matrix.setPolyToPoly(p, 0, floatArrayOf(0f, 0f, width, 0f, width, height, 0f, height), 0, 4)
        val out = Bitmap.createBitmap(width.roundToInt(), height.roundToInt(), Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(photo, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        }
        return out
    }

    /**
     * Evens out the lighting (dividing by a blurred copy of the page, so shadows and a yellow
     * tint disappear and the paper turns white), then stretches the contrast. Black-and-white
     * turns the result into ink and paper only, with soft edges so writing stays smooth.
     */
    private fun clean(page: Bitmap, blackAndWhite: Boolean): Bitmap {
        val w = page.width
        val h = page.height
        // The paper's brightness everywhere: the page shrunk to a blur (ink vanishes) and grown back.
        val small = Bitmap.createScaledBitmap(page, maxOf(1, w / 24), maxOf(1, h / 24), true)
        val background = Bitmap.createScaledBitmap(small, w, h, true)
        if (small !== page && small !== background) small.recycle()
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val rows = 128
        val pixels = IntArray(w * rows)
        val paper = IntArray(w * rows)
        var y = 0
        while (y < h) {
            val n = minOf(rows, h - y)
            page.getPixels(pixels, 0, w, 0, y, w, n)
            background.getPixels(paper, 0, w, 0, y, w, n)
            for (i in 0 until w * n) {
                val c = pixels[i]
                val b = paper[i]
                var r = level(Color.red(c), Color.red(b))
                var g = level(Color.green(c), Color.green(b))
                var bl = level(Color.blue(c), Color.blue(b))
                if (blackAndWhite) {
                    val v = ink((r * 299 + g * 587 + bl * 114) / 1000)
                    r = v; g = v; bl = v
                }
                pixels[i] = Color.rgb(r, g, bl)
            }
            out.setPixels(pixels, 0, w, 0, y, w, n)
            y += n
        }
        background.recycle()
        if (out !== page) page.recycle()
        return out
    }

    // One colour channel divided by the paper's, then contrast-stretched so near-paper becomes white.
    private fun level(value: Int, paper: Int): Int {
        val ratio = value / maxOf(paper, 24).toFloat()
        return ((ratio - 0.1f) / 0.82f * 255f).roundToInt().coerceIn(0, 255)
    }

    // Grey to ink-or-paper, with a short soft ramp between them.
    private fun ink(grey: Int): Int = when {
        grey <= 150 -> 0
        grey >= 205 -> 255
        else -> ((grey - 150) * 255) / 55
    }

    private fun luminance(color: Int) = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000
}
