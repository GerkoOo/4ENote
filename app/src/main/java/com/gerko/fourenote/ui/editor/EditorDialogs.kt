package com.gerko.fourenote.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gerko.fourenote.data.ink.PageLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A link that leads nowhere, with the page it is on, so it can be removed. */
internal data class BrokenLink(
    val pageId: String,
    val link: PageLink,
    val resolution: LinkResolution,
)

/** Explains why a link didn't open, and offers to remove it or open the note it names. */
@Composable
internal fun BrokenLinkDialog(
    broken: BrokenLink,
    onOpenNote: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val pageMissing = broken.resolution as? LinkResolution.PageNotFound
    val objectsMissing = broken.resolution as? LinkResolution.ObjectsNotFound
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    objectsMissing != null -> "Linked objects not found"
                    pageMissing != null -> "Linked page not found"
                    else -> "Note not found"
                },
            )
        },
        text = {
            Text(
                when {
                    objectsMissing != null -> "The ink or pictures this link points to were erased or moved."
                    pageMissing != null -> "The page was deleted. You can open the note instead."
                    else -> "The note this link points to was deleted or isn't on this device."
                },
            )
        },
        confirmButton = {
            when {
                objectsMissing != null -> TextButton(onClick = onOpenNote) { Text("Open page") }
                pageMissing != null -> TextButton(onClick = onOpenNote) { Text("Open note") }
                else -> TextButton(onClick = onRemove) { Text("Remove link") }
            }
        },
        dismissButton = {
            Row {
                if (pageMissing != null || objectsMissing != null) {
                    TextButton(onClick = onRemove) { Text("Remove link") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/**
 * Shown after picking a PDF: its first page twice, as it is and with colors
 * inverted for dark reading. Tapping one imports the PDF that way; it can still
 * be changed per page later from the page menu. In dark mode the inverted
 * version starts highlighted.
 */
@Composable
internal fun PdfColorsDialog(
    uri: android.net.Uri,
    suggestInvert: Boolean,
    onKeep: () -> Unit,
    onInvert: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val preview by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                    android.graphics.pdf.PdfRenderer(fd).use { renderer ->
                        renderer.openPage(0).use { page ->
                            val width = 480
                            val bitmap = android.graphics.Bitmap.createBitmap(
                                width,
                                (width.toLong() * page.height / page.width).toInt().coerceAtLeast(1),
                                android.graphics.Bitmap.Config.ARGB_8888,
                            )
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap.asImageBitmap()
                        }
                    }
                }
            }.getOrNull()
        }
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Import PDF") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PdfChoice("Original", preview, inverted = false, highlighted = !suggestInvert, onClick = onKeep, Modifier.weight(1f))
                PdfChoice("Inverted", preview, inverted = true, highlighted = suggestInvert, onClick = onInvert, Modifier.weight(1f))
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun PdfChoice(
    label: String,
    preview: ImageBitmap?,
    inverted: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ring = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(preview?.let { it.width.toFloat() / it.height } ?: (210f / 297f))
                .border(if (highlighted) 3.dp else 1.dp, ring, RoundedCornerShape(8.dp))
                .padding(3.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (inverted) Color.Black else Color.White),
        ) {
            if (preview == null) {
                CircularProgressIndicator(Modifier.size(32.dp))
            } else {
                Image(
                    bitmap = preview,
                    contentDescription = "$label colors",
                    colorFilter = if (inverted) SmartInvert else null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

/** Asks how many rows and columns a new table gets. */
@Composable
internal fun TableDialog(onInsert: (rows: Int, columns: Int) -> Unit, onDismiss: () -> Unit) {
    var rows by rememberSaveable { mutableIntStateOf(3) }
    var columns by rememberSaveable { mutableIntStateOf(3) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert table") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Stepper("Rows", rows) { rows = it }
                Stepper("Columns", columns) { columns = it }
                Text(
                    "The table is selected after inserting: drag to move it, pull the dots to resize it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onInsert(rows, columns) }) { Text("Insert") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        IconButton(onClick = { onChange((value - 1).coerceAtLeast(1)) }, enabled = value > 1) {
            Icon(Icons.Filled.Remove, contentDescription = "Fewer $label")
        }
        Text("$value", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
        IconButton(onClick = { onChange((value + 1).coerceAtMost(MAX_TABLE_CELLS)) }, enabled = value < MAX_TABLE_CELLS) {
            Icon(Icons.Filled.Add, contentDescription = "More $label")
        }
    }
}

private const val MAX_TABLE_CELLS = 20

/** Pen button and multi-finger gestures, like the input settings of other note apps. */
@Composable
internal fun PenSettingsDialog(viewModel: NoteEditorViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pen & gestures") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Hold a button while writing to use its tool for that stroke; click it while the pen hovers to " +
                        "switch to that tool (click again to switch back). The pen's eraser end always erases.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PenButtonChoice("Side button 1", viewModel.primaryButtonAction) { viewModel.primaryButtonAction = it }
                PenButtonChoice("Side button 2", viewModel.secondaryButtonAction) { viewModel.secondaryButtonAction = it }
                Text(
                    "Last signal from the pen: ${viewModel.lastPenSignal ?: "none yet. Press a pen button over the page."}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Nothing shows up? On Xiaomi, turn off the button shortcuts in Settings → Additional settings → Xiaomi Smart Pen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SettingSwitch("Two-finger double-tap to undo", viewModel.twoFingerDoubleTapUndo) { viewModel.twoFingerDoubleTapUndo = it }
                SettingSwitch("Three-finger double-tap to redo", viewModel.threeFingerDoubleTapRedo) { viewModel.threeFingerDoubleTapRedo = it }
                SettingSwitch("Stylus only (fingers scroll, palm ignored)", viewModel.stylusOnly) { viewModel.stylusOnly = it }
                SettingSwitch("Hold pen and finger on a picture to select it (any tool)", viewModel.penAndFingerSelect) {
                    viewModel.penAndFingerSelect = it
                }
                Text(
                    "Draw a line, circle, triangle or rectangle and hold the pen still to straighten it. " +
                        "Draw a loop around something, then hold a finger inside it to select it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "Hold two fingers still on a page, or tap the dial button, to open the quick dial.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
