package com.gerko.fourenote.data.ink

/**
 * "Smart invert" for PDF pages, scans and pictures: light becomes dark and
 * dark becomes light, but hues are kept (red stays red, blue stays blue), the
 * way phone dark modes invert web pages.
 *
 * It is inversion followed by a 180 degree hue rotation. The hue rotation
 * matrix H uses the standard luminance weights (0.213, 0.715, 0.072); since
 * each of its rows sums to 1, inverting then rotating is simply 255 - H x.
 * Laid out as a 4x5 Android color matrix (RGBA rows, last column is offset).
 */
object SmartInvert {
    val MATRIX = floatArrayOf(
        0.574f, -1.430f, -0.144f, 0f, 255f,
        -0.426f, -0.430f, -0.144f, 0f, 255f,
        -0.426f, -1.430f, 0.856f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    )

    /** Applies [MATRIX] to one color (0-255 channels); used by tests and for checking colors. */
    fun apply(r: Int, g: Int, b: Int): Triple<Int, Int, Int> {
        fun channel(row: Int) =
            (MATRIX[row * 5] * r + MATRIX[row * 5 + 1] * g + MATRIX[row * 5 + 2] * b + MATRIX[row * 5 + 4])
                .toInt().coerceIn(0, 255)
        return Triple(channel(0), channel(1), channel(2))
    }
}
