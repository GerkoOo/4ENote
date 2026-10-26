package com.gerko.fourenote.ui.editor

import com.gerko.fourenote.data.ink.StickyNote

// Part of NoteEditorViewModel: Sticky notes: placing, moving, folding, colour.
// Extension functions, so callers keep writing viewModel.x(); the state stays in the class.

/**
 * A tap on a page with the Sticky tool: lets go of the selected sticky note, or else puts a new
 * one with its top-left corner at ([x], [y]), kept inside the page, and selects it.
 */
fun NoteEditorViewModel.placeSticky(pageId: String, x: Float, y: Float) {
    if (selectedSticky != null) {
        selectedSticky = null
        return
    }
    val page = page(pageId) ?: return
    recordUndo()
    val sticky = StickyNote(
        x = x.coerceIn(0f, (page.width - NoteEditorViewModel.STICKY_SIZE).coerceAtLeast(0f)),
        y = y.coerceIn(0f, (page.height - NoteEditorViewModel.STICKY_SIZE).coerceAtLeast(0f)),
        width = NoteEditorViewModel.STICKY_SIZE,
        height = NoteEditorViewModel.STICKY_SIZE,
        color = stickyColor,
    )
    page.stickies.add(sticky)
    selectedSticky = pageId to sticky.id
    persist()
}

fun NoteEditorViewModel.selectSticky(pageId: String, stickyId: String?) {
    selectedSticky = stickyId?.let { pageId to it }
}

fun NoteEditorViewModel.updateSticky(pageId: String, sticky: StickyNote) {
    val page = page(pageId) ?: return
    val index = page.stickies.indexOfFirst { it.id == sticky.id }
    if (index < 0 || page.stickies[index] == sticky) return
    recordUndo()
    page.stickies[index] = sticky
    persist()
}

fun NoteEditorViewModel.deleteSelectedSticky() {
    val (pageId, stickyId) = selectedSticky ?: return
    val page = page(pageId) ?: return
    if (page.stickies.none { it.id == stickyId }) return
    recordUndo()
    page.stickies.removeAll { it.id == stickyId }
    selectedSticky = null
    persist()
}

fun NoteEditorViewModel.toggleFold(pageId: String, stickyId: String) {
    val page = page(pageId) ?: return
    val index = page.stickies.indexOfFirst { it.id == stickyId }
    if (index < 0) return
    recordUndo()
    page.stickies[index] = page.stickies[index].let { it.copy(folded = !it.folded) }
    if (page.stickies[index].folded && selectedSticky == pageId to stickyId) selectedSticky = null
    persist()
}

fun NoteEditorViewModel.changeStickyColor(color: Int) {
    stickyColor = color
    val (pageId, stickyId) = selectedSticky ?: return
    val page = page(pageId) ?: return
    val index = page.stickies.indexOfFirst { it.id == stickyId }
    if (index < 0 || page.stickies[index].color == color) return
    recordUndo()
    page.stickies[index] = page.stickies[index].copy(color = color)
    persist()
}
