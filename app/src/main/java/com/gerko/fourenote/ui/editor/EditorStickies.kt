package com.gerko.fourenote.ui.editor

import com.gerko.fourenote.data.ink.InkLayer
import com.gerko.fourenote.data.ink.InkStroke
import com.gerko.fourenote.data.ink.StickyNote
import com.gerko.fourenote.data.ink.lineage

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

/** The open sticky note on top at ([x], [y]) on the page: its index in the page's list, or -1. */
private fun NoteEditorViewModel.stickyIndexAt(pageId: String, x: Float, y: Float): Int {
    val stickies = page(pageId)?.stickies ?: return -1
    return stickies.indexOfLast { !it.folded && x >= it.x && x <= it.x + it.width && y >= it.y && y <= it.y + it.height }
}

/**
 * A stroke that starts on an open sticky note belongs to the note: it is kept relative to the
 * note's corner, so it moves, folds and syncs with it, and the page under it stays untouched.
 * Returns false when the stroke is for the page.
 */
fun NoteEditorViewModel.addToSticky(pageId: String, stroke: InkStroke): Boolean {
    if (stroke.points.size < 2) return false
    val index = stickyIndexAt(pageId, stroke.points[0], stroke.points[1])
    if (index < 0) return false
    val page = page(pageId) ?: return false
    val sticky = page.stickies[index]
    val points = FloatArray(stroke.points.size) { i -> stroke.points[i] - if (i % 2 == 0) sticky.x else sticky.y }
    recordUndo()
    val added = InkStroke(
        stroke.color, stroke.width, stroke.highlighter, points, stroke.widths, InkLayer.BASE_ID, stroke.id, stroke.nibAngle, stroke.squareEnds,
    )
    page.stickies[index] = sticky.copy(strokes = sticky.strokes + added)
    persist()
    return true
}

/** The eraser on an open sticky note rubs out the note's ink only. Returns false when not on a note. */
fun NoteEditorViewModel.eraseOnSticky(pageId: String, x: Float, y: Float): Boolean {
    val index = stickyIndexAt(pageId, x, y)
    if (index < 0) return false
    val page = page(pageId) ?: return false
    val sticky = page.stickies[index]
    val localX = x - sticky.x
    val localY = y - sticky.y
    val strokes = if (eraseWholeStrokes) {
        sticky.strokes.filterNot { it.isHitBy(localX, localY, ERASER_RADIUS_DP) }
    } else {
        sticky.strokes.flatMap { stroke ->
            // Pieces cut again in the same rub keep the original stroke as their ancestor (as on the page).
            val replacing = if (stroke.id in cutThisRub) stroke.replaces else stroke.lineage()
            stroke.erasedAround(localX, localY, ERASER_RADIUS_DP, replacing)?.also { pieces -> pieces.mapTo(cutThisRub) { it.id } } ?: listOf(stroke)
        }
    }
    // Undo for the whole rub is recorded by endErase (beginErase took the snapshot).
    if (strokes != sticky.strokes) page.stickies[index] = sticky.copy(strokes = strokes)
    return true
}
