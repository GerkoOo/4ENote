package com.gerko.fourenote.ios.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.gerko.fourenote.data.ink.InkPage
import com.gerko.fourenote.data.ink.InkStroke
import com.gerko.fourenote.ios.PageView

/**
 * The ink editor: a scrolling column of the note's pages with a toolbar on top. The stylus always
 * draws; a finger only draws when [EditorState.fingerDraws] is on, and otherwise scrolls the
 * column (so a resting palm doesn't leave marks).
 */
@Composable
fun InkEditor(state: EditorState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        EditorToolbar(state)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFE0E0E0))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.document.pages.forEachIndexed { index, page ->
                EditorPage(state, index, page)
            }
        }
    }
}

@Composable
private fun EditorPage(state: EditorState, index: Int, page: InkPage) {
    var liveStroke by remember { mutableStateOf<InkStroke?>(null) }
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    val builder = remember { StrokeBuilder() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(page.width / page.height)
            .onSizeChanged { sizePx = it }
            .pointerInput(state, index, page.id) {
                // The last time a stylus was seen: a palm landing just after it is ignored.
                var lastStylusMs = 0L
                // The one pointer writing the current stroke; any other is ignored until it lifts.
                var drawing: PointerId? = null
                fun toPage(p: Offset): Offset {
                    val scale = if (sizePx.width > 0) sizePx.width.toFloat() / page.width else 1f
                    return Offset(p.x / scale, p.y / scale)
                }
                fun point(position: Offset, time: Long, pressure: Float, first: Boolean) {
                    val p = toPage(position)
                    when {
                        state.tool == EditorTool.ERASER -> state.eraseAt(index, p.x, p.y, ERASER_RADIUS)
                        first -> builder.start(p.x, p.y, time, pressure)
                        else -> builder.add(p.x, p.y, time, pressure)
                    }
                }
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        // Every pointer in the event: with a palm on the glass, the pen is often not the first.
                        for (change in event.changes) {
                            val isStylus = change.type == PointerType.Stylus || change.type == PointerType.Eraser
                            if (isStylus) lastStylusMs = change.uptimeMillis
                            if (drawing == null) {
                                // The pen writes even while the pages are still gliding from a flick:
                                // the scrolling column takes that touch-down to stop, which would
                                // otherwise swallow the stroke.
                                if (!(if (isStylus) change.changedToDownIgnoreConsumed() else change.changedToDown())) continue
                                val allowed = isStylus ||
                                    (state.fingerDraws && change.uptimeMillis - lastStylusMs > STYLUS_GRACE_MS)
                                // A finger that doesn't draw is left alone, so the pages scroll.
                                if (!allowed) continue
                                drawing = change.id
                                point(change.position, change.uptimeMillis, change.pressure, first = true)
                            } else if (change.id == drawing) {
                                // The pen reports more points than frames: take the ones in between too.
                                for (h in change.historical) point(h.position, h.uptimeMillis, change.pressure, first = false)
                                if (change.pressed) {
                                    point(change.position, change.uptimeMillis, change.pressure, first = false)
                                } else {
                                    if (state.tool != EditorTool.ERASER) {
                                        builder.finish(state.tool, state.color, state.width)?.let { state.addStroke(index, it) }
                                    }
                                    drawing = null
                                }
                            } else {
                                continue
                            }
                            change.consume()
                            liveStroke = if (drawing != null) previewStroke(state, builder) else null
                        }
                    }
                }
            },
    ) {
        PageView(
            page = page,
            paper = state.document.paper,
            liveStroke = liveStroke,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** The stroke as it looks so far, drawn on top of the page while the pen is still down. */
private fun previewStroke(state: EditorState, builder: StrokeBuilder): InkStroke? {
    val points = builder.livePoints()
    if (points.isEmpty()) return null
    return when (state.tool) {
        EditorTool.PEN -> InkStroke(state.color, state.width, false, points)
        EditorTool.HIGHLIGHTER -> InkStroke(
            state.color and 0x00FFFFFF or (0x66 shl 24),
            state.width * 6f,
            true,
            if (points.size == 2) floatArrayOf(points[0], points[1], points[0], points[1]) else points,
        )
        EditorTool.ERASER -> null
    }
}

@Composable
private fun EditorToolbar(state: EditorState) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ToolButton("Pen", state.tool == EditorTool.PEN) { state.tool = EditorTool.PEN }
            ToolButton("Highlighter", state.tool == EditorTool.HIGHLIGHTER) { state.tool = EditorTool.HIGHLIGHTER }
            ToolButton("Eraser", state.tool == EditorTool.ERASER) { state.tool = EditorTool.ERASER }

            for (c in COLORS) {
                val selected = state.color == c
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .size(if (selected) 28.dp else 22.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .clickable { state.color = c },
                )
            }

            for (w in WIDTHS) {
                ToolButton(widthLabel(w), state.width == w) { state.width = w }
            }

            ToolButton("Undo", enabled = state.canUndo) { state.undo() }
            ToolButton("Redo", enabled = state.canRedo) { state.redo() }
            ToolButton(if (state.fingerDraws) "Finger: on" else "Finger: off") {
                state.fingerDraws = !state.fingerDraws
            }
        }
    }
}

@Composable
private fun ToolButton(label: String, selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun widthLabel(w: Float): String = when (w) {
    2f -> "Thin"
    3f -> "Medium"
    else -> "Thick"
}

private val COLORS = listOf(
    0xFF1F2A44.toInt(),
    0xFFC62828.toInt(),
    0xFF1E5AA8.toInt(),
    0xFF2E7D32.toInt(),
    0xFF6A1B9A.toInt(),
    0xFF202020.toInt(),
)

private val WIDTHS = listOf(2f, 3f, 6f)

private const val ERASER_RADIUS = 12f
private const val STYLUS_GRACE_MS = 500L
