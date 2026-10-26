package com.gerko.fourenote.ui.editor

import com.gerko.fourenote.data.ink.PageText
import com.gerko.fourenote.data.ink.TextStyleSpec
import com.gerko.fourenote.data.ink.Tool
import androidx.compose.ui.graphics.toArgb

// Part of NoteEditorViewModel: Typing in text boxes.
// Extension functions, so callers keep writing viewModel.x(); the state stays in the class.

/**
 * A tap on a page with the text tool: finishes the box being typed in, or else starts a new
 * one there in the pen's colour.
 */
fun NoteEditorViewModel.tapWithText(pageId: String, x: Float, y: Float) {
    if (editingText != null) {
        finishTextEditing()
        return
    }
    val page = page(pageId) ?: return
    val style = textStyle.copy(color = penColor.toArgb())
    val width = minOf(NoteEditorViewModel.DEFAULT_TEXT_WIDTH, page.width - x - 24f).coerceAtLeast(120f)
    val box = PageText(x = x.coerceIn(0f, page.width - width), y = (y - style.size * 0.7f).coerceAtLeast(0f), width = width, style = style, autoMath = autoLatex)
    recordUndo()
    page.texts.add(box)
    textStyle = style
    editingText = pageId to box.id
    persist()
}

fun NoteEditorViewModel.editText(pageId: String, textId: String) {
    if (editingText == pageId to textId) return
    finishTextEditing()
    val box = page(pageId)?.texts?.firstOrNull { it.id == textId } ?: return
    recordUndo()
    textStyle = box.style
    // Picked up with Select too: switch to Text so its style options show.
    tool = Tool.TEXT
    editingText = pageId to textId
}

fun NoteEditorViewModel.updateText(pageId: String, box: PageText) {
    val texts = page(pageId)?.texts ?: return
    val index = texts.indexOfFirst { it.id == box.id }
    if (index < 0) return
    texts[index] = box
    persist()
}

fun NoteEditorViewModel.finishTextEditing() {
    val (pageId, textId) = editingText ?: return
    editingText = null
    val texts = page(pageId)?.texts ?: return
    if (texts.removeAll { it.id == textId && it.text.isBlank() }) persist()
}

fun NoteEditorViewModel.deleteEditingText() {
    val (pageId, textId) = editingText ?: return
    editingText = null
    page(pageId)?.texts?.removeAll { it.id == textId }
    persist()
}

fun NoteEditorViewModel.changeTextStyle(style: TextStyleSpec) {
    textStyle = style
    editedText()?.let { (pageId, box) -> updateText(pageId, box.copy(style = style)) }
}

fun NoteEditorViewModel.changeAutoLatex(on: Boolean) {
    autoLatex = on
    editedText()?.let { (pageId, box) -> updateText(pageId, box.copy(autoMath = on)) }
}

internal fun NoteEditorViewModel.editedText(): Pair<String, PageText>? {
    val (pageId, textId) = editingText ?: return null
    return page(pageId)?.texts?.firstOrNull { it.id == textId }?.let { pageId to it }
}
