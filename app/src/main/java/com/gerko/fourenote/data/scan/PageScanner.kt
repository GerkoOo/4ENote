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
    /** Even lighting, white paper, colours kept: right for most documents. */
    CLEAN("Auto"),
    /** Like Auto with livelier colours, for coloured notes and diagrams. */
    COLOUR("Colour"),
    GREYSCALE("Greyscale"),
    BLACK_AND_WHITE("B&W"),
    /** The photo as taken, only straightened: for pictures. */
    ORIGINAL("Photo"),
}

/** How one scanned page is turned into the final picture. */
data class ScanSettings(
    val filter: ScanFilter = ScanFilter.CLEAN,
    /** Quarter turns clockwise, 0 to 3. */
    val quarterTurns: Int = 0,
    /** -1 (darker) to 1 (lighter); 0 leaves it. */
    val brightness: Float = 0f,
    /** -1 (flatter) to 1 (punchier); 0 leaves it. */
    val contrast: Float = 0f,
)

/**
 * The scanner's picture work, all with Android's own graphics (no extra library): finding the
 * sheet in a photo ([PageFinder]), straightening it, and cleaning it up.
 */
object PageScanner {
    private const val FIND_SIZE = 400
    private const val MAX_SIDE = 3000

    /**
     * The sheet's corners in [photo]: from the trained [model] when it is sure, else from the
     * brightness-based [PageFinder], else (no sheet edge in view, as in a close-up) a rectangle
     * around the writing, else just inside the photo's edges.
     */
    fun findCorners(photo: Bitmap, model: EdgeModel? = null): PageFinder.Corners {
        model?.let { runCatching { it.find(photo) }.getOrNull() }?.let { return it }
        val factor = FIND_SIZE.toFloat() / maxOf(photo.width, photo.height)
        val w = (photo.width * factor).roundToInt().coerceAtLeast(8)
        val h = (photo.height * factor).roundToInt().coerceAtLeast(8)
        val small = Bitmap.createScaledBitmap(photo, w, h, true)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        if (small !== photo) small.recycle()
        val luma = IntArray(pixels.size) { luminance(pixels[it]) }
        val scaleBack = photo.width.toFloat() / w
        PageFinder.find(luma, w, h)?.let { return it.scaled(scaleBack) }
        writingBounds(luma, w, h)?.let { return it.scaled(scaleBack) }
        val mx = photo.width * 0.03f
        val my = photo.height * 0.03f
        return PageFinder.Corners(
            floatArrayOf(mx, my, photo.width - mx, my, photo.width - mx, photo.height - my, mx, photo.height - my),
        )
    }

    /**
     * A rectangle around the writing on a page that fills the photo: pixels clearly darker than
     * the paper around them are ink, and the box around them (with a margin) is the page to keep.
     */
    fun writingBounds(luma: IntArray, w: Int, h: Int): PageFinder.Corners? {
        // The paper's local brightness: a wide box blur, in which thin strokes of ink vanish.
        val radius = maxOf(w, h) / 25
        val paper = boxBlur(luma, w, h, radius)
        var left = w
        var top = h
        var right = -1
        var bottom = -1
        var inkCount = 0
        val border = maxOf(w, h) / 50 // shadows and the table at the very edge aren't writing
        for (y in border until h - border) for (x in border until w - border) {
            val i = y * w + x
            if (paper[i] - luma[i] > 40) {
                inkCount++
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (inkCount < 30 || right <= left || bottom <= top) return null
        val margin = maxOf(w, h) * 0.04f
        val l = (left - margin).coerceAtLeast(0f)
        val t = (top - margin).coerceAtLeast(0f)
        val r = (right + margin).coerceAtMost(w.toFloat())
        val b = (bottom + margin).coerceAtMost(h.toFloat())
        // Only worth it when it actually trims something.
        if ((r - l) * (b - t) > 0.9f * w * h) return null
        return PageFinder.Corners(floatArrayOf(l, t, r, t, r, b, l, b))
    }

    private fun boxBlur(values: IntArray, w: Int, h: Int, radius: Int): IntArray {
        val horizontal = IntArray(values.size)
        for (y in 0 until h) {
            var sum = 0
            for (x in -radius..radius) sum += values[y * w + x.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                horizontal[y * w + x] = sum / (2 * radius + 1)
                sum += values[y * w + (x + radius + 1).coerceAtMost(w - 1)] - values[y * w + (x - radius).coerceAtLeast(0)]
            }
        }
        val out = IntArray(values.size)
        for (x in 0 until w) {
            var sum = 0
            for (y in -radius..radius) sum += horizontal[y.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                out[y * w + x] = sum / (2 * radius + 1)
                sum += horizontal[(y + radius + 1).coerceAtMost(h - 1) * w + x] - horizontal[(y - radius).coerceAtLeast(0) * w + x]
            }
        }
        return out
    }

    /** Straightens the sheet inside [corners] into an upright rectangle, then applies [settings]. */
    fun process(photo: Bitmap, corners: PageFinder.Corners, settings: ScanSettings, maxSide: Int = MAX_SIDE): Bitmap {
        val flat = straighten(photo, corners, maxSide)
        val filtered = when (settings.filter) {
            ScanFilter.ORIGINAL -> flat
            else -> clean(flat, settings.filter)
        }
        val adjusted = adjust(filtered, settings.brightness, settings.contrast)
        return turn(adjusted, settings.quarterTurns)
    }

    private fun turn(page: Bitmap, quarterTurns: Int): Bitmap {
        val turns = ((quarterTurns % 4) + 4) % 4
        if (turns == 0) return page
        val turned = Bitmap.createBitmap(page, 0, 0, page.width, page.height, Matrix().apply { postRotate(90f * turns) }, true)
        if (turned !== page) page.recycle()
        return turned
    }

    // Brightness and contrast on every channel, in place.
    private fun adjust(page: Bitmap, brightness: Float, contrast: Float): Bitmap {
        if (brightness == 0f && contrast == 0f) return page
        val gain = 1f + contrast * (if (contrast > 0) 1.5f else 0.8f)
        val lift = brightness * 90f
        val table = IntArray(256) { v -> ((v - 128) * gain + 128 + lift).roundToInt().coerceIn(0, 255) }
        val out = if (page.isMutable) page else page.copy(Bitmap.Config.ARGB_8888, true).also { page.recycle() }
        val row = IntArray(out.width)
        for (y in 0 until out.height) {
            out.getPixels(row, 0, out.width, 0, y, out.width, 1)
            for (i in row.indices) {
                val c = row[i]
                row[i] = Color.argb(Color.alpha(c), table[Color.red(c)], table[Color.green(c)], table[Color.blue(c)])
            }
            out.setPixels(row, 0, out.width, 0, y, out.width, 1)
        }
        return out
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
    private fun clean(page: Bitmap, filter: ScanFilter): Bitmap {
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
                when (filter) {
                    ScanFilter.BLACK_AND_WHITE -> {
                        val v = ink((r * 299 + g * 587 + bl * 114) / 1000)
                        r = v; g = v; bl = v
                    }
                    ScanFilter.GREYSCALE -> {
                        val v = (r * 299 + g * 587 + bl * 114) / 1000
                        r = v; g = v; bl = v
                    }
                    ScanFilter.COLOUR -> {
                        // Push each channel away from the grey, so faded ink and highlighter come back.
                        val grey = (r * 299 + g * 587 + bl * 114) / 1000
                        r = (grey + (r - grey) * 1.5f).roundToInt().coerceIn(0, 255)
                        g = (grey + (g - grey) * 1.5f).roundToInt().coerceIn(0, 255)
                        bl = (grey + (bl - grey) * 1.5f).roundToInt().coerceIn(0, 255)
                    }
                    else -> Unit
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
