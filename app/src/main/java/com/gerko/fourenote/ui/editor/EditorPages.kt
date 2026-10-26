package com.gerko.fourenote.ui.editor

import com.gerko.fourenote.data.ink.BackgroundPlacement
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.gerko.fourenote.data.ink.InkPage
import com.gerko.fourenote.data.ink.InkStroke
import com.gerko.fourenote.data.ink.Shapes
import com.gerko.fourenote.data.ink.Tool
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.InputStream

// Part of NoteEditorViewModel: Pages: adding, clearing, moving, deleting, importing PDFs and images, and their backgrounds.
// Extension functions, so callers keep writing viewModel.x(); the state stays in the class.

fun NoteEditorViewModel.clearPage(pageId: String) {
    val strokes = page(pageId)?.strokes ?: return
    if (strokes.none { it.layerId == activeLayerId }) return
    recordUndo()
    strokes.removeAll { it.layerId == activeLayerId }
    clearSelection()
    persist()
}

fun NoteEditorViewModel.addBlankPage(afterPageId: String? = null): Int {
    recordUndo()
    val index = afterPageId?.let { id -> pages.indexOfFirst { it.id == id } + 1 }?.takeIf { it > 0 } ?: pages.size
    pages.add(index, PageState.of(InkPage()))
    persist()
    return index
}

fun NoteEditorViewModel.setBackgroundInverted(pageId: String, inverted: Boolean) {
    val page = page(pageId) ?: return
    if (page.background == null || page.invertBackground == inverted) return
    recordUndo()
    page.invertBackground = inverted
    persist()
}

fun NoteEditorViewModel.setAllBackgroundsInverted(inverted: Boolean) {
    val targets = pages.filter { it.background != null && it.invertBackground != inverted }
    if (targets.isEmpty()) return
    recordUndo()
    targets.forEach { it.invertBackground = inverted }
    persist()
}

fun NoteEditorViewModel.startAdjustingBackground(pageId: String) {
    clearSelection()
    adjustingPageId = pageId
}

fun NoteEditorViewModel.finishAdjustingBackground() {
    adjustingPageId = null
}

fun NoteEditorViewModel.setBackgroundPlacement(pageId: String, placement: BackgroundPlacement) {
    val page = page(pageId) ?: return
    if (page.placement == placement) return
    recordUndo()
    page.placement = placement
    persist()
}

/**
 * Draws a table of [rows] by [columns] cells in the pen's color, centered on the page, and
 * selects it so it can be moved and stretched straight away.
 */
fun NoteEditorViewModel.insertTable(pageId: String, rows: Int, columns: Int) {
    val page = page(pageId) ?: return
    val width = minOf(page.width * 0.8f, columns * 160f)
    val height = rows * TABLE_ROW_HEIGHT
    val left = (page.width - width) / 2
    val top = ((page.height - height) / 2).coerceAtLeast(40f)
    val color = penColor.toArgb()
    fun line(x0: Float, y0: Float, x1: Float, y1: Float) =
        InkStroke(color, penWidth, highlighter = false, points = Shapes.polyline(floatArrayOf(x0, y0, x1, y1)), layerId = activeLayerId)
    val lines = (0..rows).map { r -> line(left, top + r * TABLE_ROW_HEIGHT, left + width, top + r * TABLE_ROW_HEIGHT) } +
        (0..columns).map { c -> line(left + c * width / columns, top, left + c * width / columns, top + height) }
    recordUndo()
    page.strokes.addAll(lines)
    persist()
    if (tool != Tool.LASSO) {
        previousTool = tool
        toolBeforeLoop = tool
        tool = Tool.LASSO
    }
    selectedImage = null
    selection = lines.toSet()
    selectionPageId = pageId
}

fun NoteEditorViewModel.beginPageMove() = recordUndo()

fun NoteEditorViewModel.movePage(from: Int, to: Int) {
    if (from !in pages.indices || to !in pages.indices || from == to) return
    pages.add(to, pages.removeAt(from))
}

fun NoteEditorViewModel.endPageMove() = persist()

fun NoteEditorViewModel.deletePage(pageId: String) {
    val index = pages.indexOfFirst { it.id == pageId }
    if (index < 0) return
    recordUndo()
    pages.removeAt(index)
    if (pages.isEmpty()) pages.add(PageState.of(InkPage()))
    if (selectionPageId == pageId) clearSelection()
    persist()
}

/**
 * Appends one page per PDF page, optionally with colors inverted for dark
 * reading; [onAdded] gets the index of the first new page.
 */
fun NoteEditorViewModel.importPdf(open: () -> InputStream?, inverted: Boolean, onAdded: (Int) -> Unit) =
    importPages(onAdded) {
        listOfNotNull(open()).flatMap { attachments.importPdf(noteId, it) }.map { it.copy(invertBackground = inverted) }
    }

fun NoteEditorViewModel.decodePhoto(file: java.io.File) = attachments.decodeUpright(file)

fun NoteEditorViewModel.importImages(opens: List<() -> InputStream?>, onAdded: (Int) -> Unit) =
    importPages(onAdded) { opens.mapNotNull { it() }.map { attachments.importImage(noteId, it) } }

internal fun NoteEditorViewModel.importPages(onAdded: (Int) -> Unit, load: suspend () -> List<InkPage>) {
    viewModelScope.launch {
        importing = true
        try {
            val newPages = load()
            if (newPages.isEmpty()) {
                Log.w(NoteEditorViewModel.TAG, "Import produced no pages")
                message = "Nothing to import"
                return@launch
            }
            recordUndo()
            // A note that is still one empty page is replaced rather than left blank at the top.
            val replaceBlank = pages.size == 1 && pages[0].strokes.isEmpty() && pages[0].background == null
            if (replaceBlank) pages.clear()
            val first = pages.size
            pages.addAll(newPages.map(PageState::of))
            persist()
            onAdded(first)
        } catch (e: Exception) {
            Log.w(NoteEditorViewModel.TAG, "Import failed", e)
            message = e.message ?: "Import failed"
        } finally {
            importing = false
        }
    }
}
