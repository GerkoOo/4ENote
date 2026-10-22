package com.gerko.fourenote.ui.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AlignmentSpan
import android.text.style.ReplacementSpan
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.gerko.fourenote.data.ink.MathText
import com.gerko.fourenote.data.ink.PageText
import com.gerko.fourenote.data.ink.TextFont
import com.gerko.fourenote.data.ink.TextStyleSpec
import ru.noties.jlatexmath.JLatexMathDrawable
import kotlin.math.roundToInt

/** Ready-made looks for text boxes; colour comes from the current pen. */
enum class TextPreset(val label: String, val size: Float, val bold: Boolean, val font: TextFont) {
    TITLE("Title", 32f, true, TextFont.SANS),
    HEADING("Heading", 24f, true, TextFont.SANS),
    BODY("Body", 16f, false, TextFont.SANS),
    CODE("Code", 14f, false, TextFont.MONO),
}

private fun TextStyleSpec.typeface(): Typeface {
    val family = when (font) {
        TextFont.SANS -> Typeface.SANS_SERIF
        TextFont.SERIF -> Typeface.SERIF
        TextFont.MONO -> Typeface.MONOSPACE
    }
    val style = when {
        bold && italic -> Typeface.BOLD_ITALIC
        bold -> Typeface.BOLD
        italic -> Typeface.ITALIC
        else -> Typeface.NORMAL
    }
    return Typeface.create(family, style)
}

/**
 * Lays out a text box at [pxPerUnit] pixels per page unit, with `$…$` and `$$…$$` typeset when
 * the box has auto maths on. Used for the screen and for PDF export, so both look the same.
 */
fun textBoxLayout(text: PageText, pxPerUnit: Float): StaticLayout {
    val style = text.style
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = style.color
        textSize = style.size * pxPerUnit
        typeface = style.typeface()
    }
    val content = SpannableStringBuilder()
    val parts = if (text.autoMath) MathText.split(text.text) else listOf(MathText.Part.Plain(text.text))
    for (part in parts) {
        when (part) {
            is MathText.Part.Plain -> content.append(part.text)
            is MathText.Part.Math -> {
                val formula = runCatching {
                    JLatexMathDrawable.builder(part.latex).textSize(paint.textSize).color(style.color).build()
                }.getOrNull()?.takeIf { it.intrinsicWidth > 0 }
                if (formula == null) {
                    // Not valid LaTeX (yet): show what was typed.
                    content.append(if (part.display) "$$${part.latex}$$" else "$${part.latex}$")
                } else {
                    val start = content.length
                    content.append('￼')
                    content.setSpan(CenteredDrawableSpan(formula), start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (part.display) {
                        // Centres the line it is on: write $$…$$ on a line of its own.
                        content.setSpan(AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
        }
    }
    val width = (text.width * pxPerUnit).roundToInt().coerceAtLeast(1)
    return StaticLayout.Builder.obtain(content, 0, content.length, paint, width)
        .setIncludePad(false)
        .build()
}

/** The text box drawn on a transparent bitmap, for PDF export. */
fun renderTextBox(text: PageText, pxPerUnit: Float): Bitmap {
    val layout = textBoxLayout(text, pxPerUnit)
    val bitmap = Bitmap.createBitmap(layout.width.coerceAtLeast(1), layout.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    layout.draw(Canvas(bitmap))
    return bitmap
}

/** A formula inside a line of text, centred on the text around it rather than sitting on the baseline. */
private class CenteredDrawableSpan(private val drawable: Drawable) : ReplacementSpan() {
    init {
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
    }

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        if (fm != null) {
            val metrics = paint.fontMetricsInt
            val center = (metrics.ascent + metrics.descent) / 2
            val half = drawable.intrinsicHeight / 2
            fm.ascent = minOf(metrics.ascent, center - half)
            fm.top = fm.ascent
            fm.descent = maxOf(metrics.descent, center + half)
            fm.bottom = fm.descent
        }
        return drawable.intrinsicWidth
    }

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val metrics = paint.fontMetricsInt
        val center = y + (metrics.ascent + metrics.descent) / 2
        canvas.save()
        canvas.translate(x, (center - drawable.intrinsicHeight / 2).toFloat())
        drawable.draw(canvas)
        canvas.restore()
    }
}

/** A text box as it looks on the page. */
@Composable
private fun TextBoxView(text: PageText, pxPerUnit: Float, modifier: Modifier = Modifier) {
    val layout = remember(text, pxPerUnit) { textBoxLayout(text, pxPerUnit) }
    val density = LocalDensity.current
    androidx.compose.foundation.Canvas(
        modifier.size(with(density) { layout.width.toDp() }, with(density) { layout.height.coerceAtLeast(1).toDp() }),
    ) {
        drawIntoCanvas { layout.draw(it.nativeCanvas) }
    }
}

/**
 * The text boxes of one page, over its ink. With the text tool, tapping a box edits it; the box
 * being edited shows the keyboard, a handle to move it, one on its right edge to change its width,
 * and, with auto maths on, the typeset result right under what is typed.
 */
@Composable
fun TextBoxLayer(
    texts: List<PageText>,
    pxPerUnit: Float,
    editingId: String?,
    textTool: Boolean,
    onEdit: (String) -> Unit,
    onChange: (PageText) -> Unit,
    onDelete: () -> Unit,
) {
    for (text in texts) {
        val position = Modifier.offset { IntOffset((text.x * pxPerUnit).roundToInt(), (text.y * pxPerUnit).roundToInt()) }
        if (text.id == editingId) {
            EditingTextBox(text, pxPerUnit, position, onChange, onDelete)
        } else {
            TextBoxView(text, pxPerUnit, if (textTool) position.clickable { onEdit(text.id) } else position)
        }
    }
}

@Composable
private fun EditingTextBox(
    text: PageText,
    pxPerUnit: Float,
    position: Modifier,
    onChange: (PageText) -> Unit,
    onDelete: () -> Unit,
) {
    val density = LocalDensity.current
    val accent = MaterialTheme.colorScheme.primary
    val current by rememberUpdatedState(text)
    val currentOnChange by rememberUpdatedState(onChange)
    // The field keeps its own cursor and selection; the box only stores the text.
    var field by remember(text.id) { mutableStateOf(TextFieldValue(text.text)) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(text.id) {
        focus.requestFocus()
        keyboard?.show()
    }
    val style = text.style
    val textStyle = TextStyle(
        color = Color(style.color),
        fontSize = with(density) { (style.size * pxPerUnit).toSp() },
        fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
        fontFamily = when (style.font) {
            TextFont.SANS -> FontFamily.SansSerif
            TextFont.SERIF -> FontFamily.Serif
            TextFont.MONO -> FontFamily.Monospace
        },
    )
    val widthDp = with(density) { (text.width * pxPerUnit).toDp() }
    Box(position) {
        Column(Modifier.width(widthDp).border(1.dp, accent.copy(alpha = 0.6f))) {
            BasicTextField(
                value = field,
                onValueChange = {
                    field = it
                    if (it.text != current.text) currentOnChange(current.copy(text = it.text))
                },
                textStyle = textStyle,
                cursorBrush = SolidColor(accent),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            // Typeset maths appears as soon as a formula's closing $ is typed.
            if (text.autoMath && MathText.hasMath(text.text)) {
                HorizontalDivider(color = accent.copy(alpha = 0.3f))
                TextBoxView(text, pxPerUnit, Modifier.padding(vertical = 2.dp))
            }
        }
        // Move handle above the box, delete beside it.
        Row(
            Modifier.offset(y = (-32).dp).height(28.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.9f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.OpenWith,
                contentDescription = "Move text",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp).padding(4.dp).pointerInput(text.id) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        currentOnChange(current.copy(x = current.x + drag.x / pxPerUnit, y = current.y + drag.y / pxPerUnit))
                    }
                },
            )
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete text",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp).padding(4.dp).clickable(onClick = onDelete),
            )
        }
        // Width handle on the right edge, as tall as the box.
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 10.dp)
                    .width(12.dp)
                    .fillMaxHeight()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(accent.copy(alpha = 0.7f))
                    .pointerInput(text.id) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            currentOnChange(current.copy(width = (current.width + drag.x / pxPerUnit).coerceAtLeast(MIN_TEXT_WIDTH)))
                        }
                    },
            )
        }
    }
}

private const val MIN_TEXT_WIDTH = 40f
