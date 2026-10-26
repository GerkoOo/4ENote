package com.gerko.fourenote.ui.editor

import androidx.compose.ui.graphics.Color
import com.gerko.fourenote.data.ink.InkStroke
import com.gerko.fourenote.data.ink.Shapes
import com.gerko.fourenote.data.ink.Tool
import com.gerko.fourenote.data.ink.isInsideLasso
import com.gerko.fourenote.data.ink.transformed
import com.gerko.fourenote.data.ink.withColor
import com.gerko.fourenote.data.ink.withTargetStrokesReplaced

// Part of NoteEditorViewModel: The lasso selection: selecting, moving, recolouring, copying and deleting ink.
// Extension functions, so callers keep writing viewModel.x(); the state stays in the class.

fun NoteEditorViewModel.loopToSelection(pageId: String) {
    val (loopPage, loop) = recentLoop ?: return
    val page = page(pageId) ?: return
    recentLoop = null
    if (loopPage != pageId || loop !in page.strokes) return
    recordUndo()
    page.strokes.remove(loop)
    persist()
    if (tool != Tool.LASSO) {
        previousTool = tool
        toolBeforeLoop = tool
        tool = Tool.LASSO
    }
    selectWithLasso(pageId, loop.points)
    if (selection.isEmpty()) {
        val picture = page.images.lastOrNull { Shapes.contains(loop.points, it.x, it.y) }
        if (picture != null) {
            selectedImage = pageId to picture.id
        } else {
            clearSelection()
            message = "Nothing inside the loop to select"
        }
    }
}

fun NoteEditorViewModel.selectWithLasso(pageId: String, polygon: FloatArray) {
    selectedImage = null
    val strokes = page(pageId)?.strokes ?: return
    selection = strokes.filter { it.layerId == activeLayerId && it.isInsideLasso(polygon) }.toSet()
    selectionPageId = pageId.takeIf { selection.isNotEmpty() }
}

fun NoteEditorViewModel.clearSelection() {
    toolBeforeLoop?.let {
        toolBeforeLoop = null
        tool = it
    }
    selection = emptySet()
    selectionPageId = null
}

fun NoteEditorViewModel.transformSelection(dx: Float, dy: Float, scaleX: Float, scaleY: Float, pivotX: Float, pivotY: Float) {
    replaceSelection { it.transformed(dx, dy, scaleX, scaleY, pivotX, pivotY) }
}

fun NoteEditorViewModel.recolorSelection(color: Color) {
    val argb = android.graphics.Color.argb(
        (color.alpha * 255).toInt(),
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt(),
    )
    replaceSelection { it.withColor(argb) }
}

fun NoteEditorViewModel.deleteSelection() {
    val strokes = selectionPageId?.let(::page)?.strokes ?: return
    recordUndo()
    strokes.removeAll { it in selection }
    clearSelection()
    persist()
}

fun NoteEditorViewModel.duplicateSelection() {
    val strokes = selectionPageId?.let(::page)?.strokes ?: return
    recordUndo()
    val copies = strokes.filter { it in selection }
        .map { it.transformed(NoteEditorViewModel.DUPLICATE_OFFSET, NoteEditorViewModel.DUPLICATE_OFFSET, 1f, 1f, 0f, 0f) }
    strokes.addAll(copies)
    selection = copies.toSet()
    persist()
}

internal fun NoteEditorViewModel.replaceSelection(transform: (InkStroke) -> InkStroke) {
    val page = selectionPageId?.let(::page) ?: return
    val strokes = page.strokes
    if (selection.isEmpty()) return
    recordUndo()
    val replaced = mutableSetOf<InkStroke>()
    // Links hang on stroke ids, which a move or recolor changes; carry them over.
    val newIds = HashMap<String, List<String>>()
    for (i in strokes.indices) {
        if (strokes[i] in selection) {
            val old = strokes[i]
            strokes[i] = transform(old).also { replaced.add(it) }
            newIds[old.id] = listOf(strokes[i].id)
        }
    }
    page.replaceLinkStrokes(newIds)
    remapTargetObjects(page.id, newIds)
    selection = replaced
    persist()
}

/**
 * Links in this note that point at objects on [pageId] follow them to their new ids, the same
 * way a link's own anchors do.
 */
internal fun NoteEditorViewModel.remapTargetObjects(pageId: String, replacements: Map<String, List<String>>) {
    if (replacements.isEmpty()) return
    for (p in pages) {
        if (p.links.isEmpty()) continue
        val remapped = p.links.withTargetStrokesReplaced(noteId, pageId, replacements)
        if (remapped == p.links.toList()) continue
        p.links.clear()
        p.links.addAll(remapped)
    }
}
