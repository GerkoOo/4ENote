package com.gerko.fourenote.ios.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gerko.fourenote.data.ink.InkDocument
import com.gerko.fourenote.data.ink.InkPage
import com.gerko.fourenote.data.ink.InkStroke
import kotlin.math.hypot

enum class EditorTool { PEN, HIGHLIGHTER, ERASER }

/**
 * The editor's document plus the bits of UI state that go with it (tool, colour, width) and the
 * undo history. Every change goes through [commit], which records the previous document for undo
 * and tells the owner about the new one so it can be saved.
 */
class EditorState(
    initial: InkDocument,
    private val onChange: (InkDocument) -> Unit = {},
) {
    var document: InkDocument by mutableStateOf(initial)
        private set

    var tool: EditorTool by mutableStateOf(EditorTool.PEN)
    var color: Int by mutableStateOf(DEFAULT_COLOR)
    var width: Float by mutableStateOf(DEFAULT_WIDTH)

    /** When false, a finger scrolls the pages and only the stylus draws (palm rejection). */
    var fingerDraws: Boolean by mutableStateOf(false)

    private val undoStack = ArrayDeque<InkDocument>()
    private val redoStack = ArrayDeque<InkDocument>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(document)
        document = previous
        onChange(previous)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(document)
        document = next
        onChange(next)
    }

    fun addStroke(pageIndex: Int, stroke: InkStroke) {
        val page = document.pages.getOrNull(pageIndex) ?: return
        commit(page.copy(strokes = page.strokes + stroke))
    }

    /**
     * Removes every stroke on the page whose line passes within [radius] of ([x], [y]) (plus half
     * the stroke's own width, so a fat stroke is hit by its edge too). Whole strokes go, not parts:
     * splitting a stroke would need new ids and would break links that point at it.
     */
    fun eraseAt(pageIndex: Int, x: Float, y: Float, radius: Float) {
        val page = document.pages.getOrNull(pageIndex) ?: return
        val kept = page.strokes.filterNot { stroke -> strokeHits(stroke, x, y, radius) }
        if (kept.size == page.strokes.size) return
        commit(page.copy(strokes = kept))
    }

    private fun commit(page: InkPage) {
        val pages = document.pages.toMutableList()
        val index = pages.indexOfFirst { it.id == page.id }
        if (index < 0) return
        pages[index] = page
        val next = document.copy(pages = pages)
        undoStack.addLast(document)
        while (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        document = next
        onChange(next)
    }

    private fun strokeHits(stroke: InkStroke, x: Float, y: Float, radius: Float): Boolean {
        val points = stroke.points
        val reach = radius + stroke.width / 2f
        if (points.size < 2) return false
        if (points.size == 2) return hypot(points[0] - x, points[1] - y) <= reach
        var i = 2
        while (i < points.size) {
            if (distanceToSegment(x, y, points[i - 2], points[i - 1], points[i], points[i + 1]) <= reach) return true
            i += 2
        }
        return false
    }

    private fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val lengthSq = dx * dx + dy * dy
        if (lengthSq == 0f) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / lengthSq).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    companion object {
        const val DEFAULT_COLOR = 0xFF1F2A44.toInt()
        const val DEFAULT_WIDTH = 3f
        private const val MAX_HISTORY = 100
    }
}
