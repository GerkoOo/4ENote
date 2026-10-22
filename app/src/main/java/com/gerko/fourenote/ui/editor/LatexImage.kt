package com.gerko.fourenote.ui.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import ru.noties.jlatexmath.JLatexMathDrawable

/**
 * Draws [latex] as a typeset formula on a transparent bitmap, or returns null if it isn't
 * valid LaTeX. [textSizePx] sets the formula's base font size.
 */
fun renderLatex(latex: String, textSizePx: Float, color: Int): Bitmap? = runCatching {
    val drawable = JLatexMathDrawable.builder(latex)
        .textSize(textSizePx)
        .color(color)
        .padding((textSizePx / 4).toInt())
        .build()
    val width = drawable.intrinsicWidth
    val height = drawable.intrinsicHeight
    if (width <= 0 || height <= 0) return@runCatching null
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        drawable.setBounds(0, 0, width, height)
        drawable.draw(Canvas(bitmap))
    }
}.getOrNull()
