package com.gerko.fourenote.ui.editor

import com.gerko.fourenote.data.ink.PageText
import com.gerko.fourenote.data.ink.TextStyleSpec
import com.gerko.fourenote.data.ink.Tool
import androidx.compose.ui.graphics.toArgb

// Part of NoteEditorViewModel: Typing in text boxes.
// Extension functions, so callers keep writing viewModel.x(); the state stays in the class.

/**
 * A tap on a page with the text tool: finishes the box being typed in, or else starts a new
 * one there in the pen's colour. On an open sticky note the box belongs to the note: it is kept
 * relative to the note's corner, so it moves, folds and syncs with it.
 */
fun NoteEditorViewModel.tapWithText(pageId: String, x: Float, y: Float) {
    if (editingText != null) {
        finishTextEditing()
        return
    }
    val page = page(pageId) ?: return
    val style = textStyle.copy(color = penColor.toArgb())
    val stickyIndex = stickyIndexAt(pageId, x, y)
    if (stickyIndex >= 0) {
        val sticky = page.stickies[stickyIndex]
        val localX = x - sticky.x
        val width = (sticky.width - localX - STICKY_TEXT_MARGIN).coerceAtLeast(MIN_STICKY_TEXT_WIDTH)
        val box = PageText(
            x = localX.coerceIn(0f, (sticky.width - width).coerceAtLeast(0f)),
            y = (y - sticky.y - style.size * 0.7f).coerceAtLeast(0f),
            width = width,
            style = style,
            autoMath = autoLatex,
        )
        recordUndo()
        page.stickies[stickyIndex] = sticky.copy(texts = sticky.texts + box)
        textStyle = style
        editingText = pageId to box.id
        editingSticky = sticky.id
        persist()
        return
    }
    val width = minOf(NoteEditorViewModel.DEFAULT_TEXT_WIDTH, page.width - x - 24f).coerceAtLeast(120f)
    val box = PageText(x = x.coerceIn(0f, page.width - width), y = (y - style.size * 0.7f).coerceAtLeast(0f), width = width, style = style, autoMath = autoLatex)
    recordUndo()
    page.texts.add(box)
    textStyle = style
    editingText = pageId to box.id
    editingSticky = null
    persist()
}

fun NoteEditorViewModel.editText(pageId: String, textId: String) {
    if (editingText == pageId to textId && editingSticky == null) return
    finishTextEditing()
    val box = page(pageId)?.texts?.firstOrNull { it.id == textId } ?: return
    startEditing(pageId, null, box)
}

/** Picks up a text box inside a sticky note for typing. */
fun NoteEditorViewModel.editStickyText(pageId: String, stickyId: String, textId: String) {
    if (editingText == pageId to textId && editingSticky == stickyId) return
    finishTextEditing()
    val box = stickyText(pageId, stickyId, textId) ?: return
    startEditing(pageId, stickyId, box)
}

private fun NoteEditorViewModel.startEditing(pageId: String, stickyId: String?, box: PageText) {
    recordUndo()
    textStyle = box.style
    // Picked up with Select too: switch to Text so its style options show.
    tool = Tool.TEXT
    editingText = pageId to box.id
    editingSticky = stickyId
}

fun NoteEditorViewModel.updateText(pageId: String, box: PageText) {
    val texts = page(pageId)?.texts ?: return
    val index = texts.indexOfFirst { it.id == box.id }
    if (index < 0) return
    texts[index] = box
    persist()
}

/** A change to a text box inside a sticky note (typing, moving it, its width). */
fun NoteEditorViewModel.updateStickyText(pageId: String, stickyId: String, box: PageText) {
    changeStickyTexts(pageId, stickyId) { texts -> texts.map { if (it.id == box.id) box else it } }
}

fun NoteEditorViewModel.finishTextEditing() {
    val (pageId, textId) = editingText ?: return
    val stickyId = editingSticky
    editingText = null
    editingSticky = null
    if (stickyId != null) {
        changeStickyTexts(pageId, stickyId) { texts -> texts.filterNot { it.id == textId && it.text.isBlank() } }
        return
    }
    val texts = page(pageId)?.texts ?: return
    if (texts.removeAll { it.id == textId && it.text.isBlank() }) persist()
}

fun NoteEditorViewModel.deleteEditingText() {
    val (pageId, textId) = editingText ?: return
    val stickyId = editingSticky
    editingText = null
    editingSticky = null
    if (stickyId != null) {
        changeStickyTexts(pageId, stickyId) { texts -> texts.filterNot { it.id == textId } }
        return
    }
    page(pageId)?.texts?.removeAll { it.id == textId }
    persist()
}

fun NoteEditorViewModel.changeTextStyle(style: TextStyleSpec) {
    textStyle = style
    editedText()?.let { (_, box) -> updateEditedText(box.copy(style = style)) }
}

fun NoteEditorViewModel.changeAutoLatex(on: Boolean) {
    autoLatex = on
    editedText()?.let { (_, box) -> updateEditedText(box.copy(autoMath = on)) }
}

internal fun NoteEditorViewModel.editedText(): Pair<String, PageText>? {
    val (pageId, textId) = editingText ?: return null
    val stickyId = editingSticky
    val box = if (stickyId != null) stickyText(pageId, stickyId, textId) else page(pageId)?.texts?.firstOrNull { it.id == textId }
    return box?.let { pageId to it }
}

private fun NoteEditorViewModel.updateEditedText(box: PageText) {
    val pageId = editingText?.first ?: return
    val stickyId = editingSticky
    if (stickyId != null) updateStickyText(pageId, stickyId, box) else updateText(pageId, box)
}

private fun NoteEditorViewModel.stickyText(pageId: String, stickyId: String, textId: String): PageText? =
    page(pageId)?.stickies?.firstOrNull { it.id == stickyId }?.texts?.firstOrNull { it.id == textId }

/** Replaces a sticky note's text boxes with [change] of them, and saves if anything changed. */
private fun NoteEditorViewModel.changeStickyTexts(pageId: String, stickyId: String, change: (List<PageText>) -> List<PageText>) {
    val stickies = page(pageId)?.stickies ?: return
    val index = stickies.indexOfFirst { it.id == stickyId }
    if (index < 0) return
    val texts = change(stickies[index].texts)
    if (texts == stickies[index].texts) return
    stickies[index] = stickies[index].copy(texts = texts)
    persist()
}

/** Room left between a new text box and a sticky note's right edge, in page units. */
private const val STICKY_TEXT_MARGIN = 8f

/** The narrowest a new text box on a sticky note starts, in page units. */
private const val MIN_STICKY_TEXT_WIDTH = 40f
