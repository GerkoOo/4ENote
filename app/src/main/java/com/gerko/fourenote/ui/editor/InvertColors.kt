package com.gerko.fourenote.ui.editor

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.gerko.fourenote.data.ink.SmartInvert as SmartInvertMatrix

/** Draws an image with smart-inverted colors (see [SmartInvertMatrix]). */
val SmartInvert: ColorFilter = ColorFilter.colorMatrix(ColorMatrix(SmartInvertMatrix.MATRIX))
