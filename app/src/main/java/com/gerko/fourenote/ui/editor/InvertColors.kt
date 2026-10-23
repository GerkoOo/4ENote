package com.gerko.fourenote.ui.editor

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.gerko.fourenote.data.ink.SmartInvert as SmartInvertMatrix

/** Draws an image with smart-inverted colors (see [SmartInvertMatrix]). */
val SmartInvert: ColorFilter = ColorFilter.colorMatrix(ColorMatrix(SmartInvertMatrix.MATRIX))

/** The colour matrix of a negative (each channel becomes 255 minus itself). */
val NEGATIVE_MATRIX = floatArrayOf(
    -1f, 0f, 0f, 0f, 255f,
    0f, -1f, 0f, 0f, 255f,
    0f, 0f, -1f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f,
)

/** Draws an image as a negative: every colour flipped (blue becomes yellow, black white). */
val NegativeColors: ColorFilter = ColorFilter.colorMatrix(ColorMatrix(NEGATIVE_MATRIX))
