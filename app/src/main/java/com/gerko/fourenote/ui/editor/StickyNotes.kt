package com.gerko.fourenote.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.gerko.fourenote.data.ink.StickyNote
import kotlin.math.roundToInt

/** A folded sticky note's size on the page, in page units. */
private const val FOLDED_SIZE = 40f

/** The smallest a sticky note can be dragged to, in page units. */
private const val MIN_STICKY_SIZE = 80f

/** Corner radius of an open sticky note, in page units. */
internal const val STICKY_CORNER = 4f

/**
 * The controls of one page's sticky notes (the notes themselves are drawn by InkCanvas). With [interactive] (the Sticky or Select
 * tool) a tap selects an open note, dragging its body moves it and the handle at its bottom-right
 * corner resizes it; the change is reported once, when the drag ends. Otherwise the body takes no
 * touches at all, so the pen and fingers draw on the page as usual; only the fold button and a
 * folded note's icon stay tappable.
 */
@Composable
fun StickyLayer(
    stickies: List<StickyNote>,
    pageWidth: Float,
    pageHeight: Float,
    pxPerUnit: Float,
    selectedId: String?,
    interactive: Boolean,
    onSelect: (String) -> Unit,
    onChange: (StickyNote) -> Unit,
    onToggleFold: (String) -> Unit,
    onDelete: () -> Unit,
    /** The note being moved or resized, until the gesture ends (null after); the page draws it. */
    onLive: (StickyNote?) -> Unit,
) {
    for (sticky in stickies) {
        if (sticky.folded) {
            FoldedSticky(sticky, pxPerUnit, onToggleFold = { onToggleFold(sticky.id) })
        } else {
            OpenSticky(
                sticky = sticky,
                pageWidth = pageWidth,
                pageHeight = pageHeight,
                pxPerUnit = pxPerUnit,
                selected = interactive && sticky.id == selectedId,
                interactive = interactive,
                onSelect = { onSelect(sticky.id) },
                onChange = onChange,
                onToggleFold = { onToggleFold(sticky.id) },
                onDelete = onDelete,
                onLive = onLive,
            )
        }
    }
}

/** A folded note: a small square in its colour; tapping it unfolds the note. */
@Composable
private fun FoldedSticky(sticky: StickyNote, pxPerUnit: Float, onToggleFold: () -> Unit) {
    val density = LocalDensity.current
    val side = with(density) { (FOLDED_SIZE * pxPerUnit).toDp() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset { IntOffset((sticky.x * pxPerUnit).roundToInt(), (sticky.y * pxPerUnit).roundToInt()) }
            .size(side)
            .shadow(2.dp, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .background(Color(sticky.color))
            .clickable(onClick = onToggleFold),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.StickyNote2,
            contentDescription = "Unfold sticky note",
            tint = Color.Black.copy(alpha = 0.55f),
            modifier = Modifier.size(side * 0.6f),
        )
    }
}

@Composable
private fun OpenSticky(
    sticky: StickyNote,
    pageWidth: Float,
    pageHeight: Float,
    pxPerUnit: Float,
    selected: Boolean,
    interactive: Boolean,
    onSelect: () -> Unit,
    onChange: (StickyNote) -> Unit,
    onToggleFold: () -> Unit,
    onDelete: () -> Unit,
    onLive: (StickyNote?) -> Unit,
) {
    val density = LocalDensity.current
    val current by rememberUpdatedState(sticky)
    val currentOnLive by rememberUpdatedState(onLive)
    val currentOnChange by rememberUpdatedState(onChange)
    // The note as it is being dragged or resized; reported once, when the gesture ends.
    var live by remember(sticky.id) { mutableStateOf<StickyNote?>(null) }
    // The page draws the note, so it hears of every step of the gesture too.
    fun setLive(value: StickyNote?) {
        live = value
        currentOnLive(value)
    }
    val shown = live ?: sticky
    val accent = MaterialTheme.colorScheme.primary
    val corner = with(density) { (STICKY_CORNER * pxPerUnit).toDp() }
    val shape = RoundedCornerShape(corner)
    val width = with(density) { (shown.width * pxPerUnit).toDp() }
    val height = with(density) { (shown.height * pxPerUnit).toDp() }
    val handle = 20.dp

    Box(
        Modifier
            .offset { IntOffset((shown.x * pxPerUnit).roundToInt(), (shown.y * pxPerUnit).roundToInt()) }
            .size(width, height)
            // The note itself (colour and ink) is drawn by the page's InkCanvas, under the pen's
            // stroke in progress; this box only holds the buttons and takes moves and resizes.
            .clip(shape)
            .then(if (selected) Modifier.border(2.dp, accent, shape) else Modifier)
            // Only the interactive layer takes touches on the body; otherwise they reach the page.
            .then(
                if (interactive) {
                    Modifier
                        .clickable(onClick = onSelect)
                        .pointerInput(sticky.id, pxPerUnit, pageWidth, pageHeight) {
                            detectDragGestures(
                                onDragStart = { onSelect() },
                                onDragEnd = {
                                    live?.let { currentOnChange(it) }
                                    setLive(null)
                                },
                                onDragCancel = { setLive(null) },
                            ) { change, drag ->
                                change.consume()
                                val from = live ?: current
                                setLive(from.copy(
                                    x = (from.x + drag.x / pxPerUnit).coerceIn(0f, (pageWidth - from.width).coerceAtLeast(0f)),
                                    y = (from.y + drag.y / pxPerUnit).coerceIn(0f, (pageHeight - from.height).coerceAtLeast(0f)),
                                ))
                            }
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        // Fold button at the top-right corner, always tappable.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onToggleFold),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.StickyNote2,
                contentDescription = "Fold sticky note",
                tint = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier.size(16.dp),
            )
        }
        if (selected) {
            // Delete at the top-left of the selected note.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onDelete),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Delete sticky note",
                    tint = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp),
                )
            }
            // Resize handle at the bottom-right corner.
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(handle)
                    .pointerInput(sticky.id, pxPerUnit, pageWidth, pageHeight) {
                        detectDragGestures(
                            onDragEnd = {
                                live?.let { currentOnChange(it) }
                                setLive(null)
                            },
                            onDragCancel = { setLive(null) },
                        ) { change, drag ->
                            change.consume()
                            val from = live ?: current
                            setLive(from.copy(
                                width = (from.width + drag.x / pxPerUnit)
                                    .coerceIn(MIN_STICKY_SIZE, (pageWidth - from.x).coerceAtLeast(MIN_STICKY_SIZE)),
                                height = (from.height + drag.y / pxPerUnit)
                                    .coerceIn(MIN_STICKY_SIZE, (pageHeight - from.y).coerceAtLeast(MIN_STICKY_SIZE)),
                            ))
                        }
                    },
            ) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(12.dp)
                        .background(accent, RoundedCornerShape(3.dp)),
                )
            }
        }
    }
}
