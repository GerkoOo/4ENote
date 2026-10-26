package com.gerko.fourenote.ui.editor

import androidx.compose.material.icons.filled.Delete
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp
import com.gerko.fourenote.data.ink.PaperStyle

@Composable
internal fun AddMenu(
    onBlankPage: () -> Unit,
    onBlankPageAtEnd: () -> Unit,
    onImportPdf: () -> Unit,
    onImportImages: () -> Unit,
    onScan: () -> Unit,
    onTable: () -> Unit,
    onPhotoFromPhone: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.Add, contentDescription = "Add pages")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Blank page after this one") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null) },
                onClick = { open = false; onBlankPage() },
            )
            DropdownMenuItem(
                text = { Text("Blank page at the end") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null) },
                onClick = { open = false; onBlankPageAtEnd() },
            )
            DropdownMenuItem(
                text = { Text("Import PDF") },
                leadingIcon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null) },
                onClick = { open = false; onImportPdf() },
            )
            DropdownMenuItem(
                text = { Text("Import images") },
                leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                onClick = { open = false; onImportImages() },
            )
            DropdownMenuItem(
                text = { Text("Scan document") },
                leadingIcon = { Icon(Icons.Filled.DocumentScanner, contentDescription = null) },
                onClick = { open = false; onScan() },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Table…") },
                leadingIcon = { Icon(Icons.Filled.TableChart, contentDescription = null) },
                onClick = { open = false; onTable() },
            )
            DropdownMenuItem(
                text = { Text("Photo from phone") },
                leadingIcon = { Icon(Icons.Filled.AddAPhoto, contentDescription = null) },
                onClick = { open = false; onPhotoFromPhone() },
            )
        }
    }
}

/** Top-bar menu listing the note's layers, top layer first. */
@Composable
internal fun LayersMenu(viewModel: NoteEditorViewModel) {
    var open by remember { mutableStateOf(false) }
    // Layers ticked for duplicating, merging or deleting together.
    var checked by remember { mutableStateOf(emptySet<String>()) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.Layers, contentDescription = "Layers")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false; checked = emptySet() }) {
            Text(
                "Layers · you draw on the selected one · tick to act on several",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            viewModel.layers.asReversed().forEach { layer ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { viewModel.selectLayer(layer.id) }
                        .padding(start = 4.dp, end = 4.dp),
                ) {
                    Checkbox(
                        checked = layer.id in checked,
                        onCheckedChange = { checked = if (it) checked + layer.id else checked - layer.id },
                    )
                    RadioButton(selected = layer.id == viewModel.activeLayerId, onClick = { viewModel.selectLayer(layer.id) })
                    Text(layer.name, modifier = Modifier.width(120.dp))
                    IconButton(onClick = { viewModel.setLayerVisible(layer.id, !layer.visible) }) {
                        Icon(
                            if (layer.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (layer.visible) "Hide ${layer.name}" else "Show ${layer.name}",
                        )
                    }
                    IconButton(onClick = { viewModel.deleteLayer(layer.id); checked = checked - layer.id }, enabled = viewModel.layers.size > 1) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete ${layer.name}")
                    }
                }
            }
            if (checked.isNotEmpty()) {
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("${checked.size} ticked", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp))
                    TextButton(onClick = { viewModel.duplicateLayers(checked); checked = emptySet() }) { Text("Duplicate") }
                    TextButton(onClick = { viewModel.mergeLayers(checked); checked = emptySet() }, enabled = checked.size > 1) { Text("Merge") }
                    TextButton(onClick = { viewModel.deleteLayers(checked); checked = emptySet() }) { Text("Delete") }
                }
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Add layer") },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = viewModel::addLayer,
            )
        }
    }
}

@Composable
internal fun PaperMenu(selected: PaperStyle, onSelect: (PaperStyle) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.GridOn, contentDescription = "Paper style")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                Triple(PaperStyle.LINED, "Lined", Icons.AutoMirrored.Filled.Notes),
                Triple(PaperStyle.GRID, "Grid", Icons.Filled.GridOn),
                Triple(PaperStyle.DOTS, "Dots", Icons.Filled.Apps),
                Triple(PaperStyle.BLANK, "Blank", Icons.AutoMirrored.Filled.InsertDriveFile),
            ).forEach { (style, label, icon) ->
                DropdownMenuItem(
                    text = { Text(if (style == selected) "$label  ✓" else label) },
                    leadingIcon = { Icon(icon, contentDescription = null) },
                    onClick = {
                        onSelect(style)
                        open = false
                    },
                )
            }
        }
    }
}

/** Ways to add pages, offered under the last page. */
internal class AddPageActions(
    /** A blank page after the page on screen. */
    val blank: () -> Unit,
    val blankAtEnd: () -> Unit,
    val pdf: () -> Unit,
    val images: () -> Unit,
    val scan: () -> Unit,
)

/** A small handle under the last page; tapping it slides up the ways to add pages. */
@Composable
internal fun AddPagesHandle(actions: AddPageActions) {
    var open by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                fun pick(action: () -> Unit) = { open = false; action() }
                AssistChip(onClick = pick(actions.blank), label = { Text("Blank page") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null) })
                AssistChip(onClick = pick(actions.blankAtEnd), label = { Text("Blank page at the end") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null) })
                AssistChip(onClick = pick(actions.pdf), label = { Text("PDF") },
                    leadingIcon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null) })
                AssistChip(onClick = pick(actions.images), label = { Text("Images") },
                    leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) })
                AssistChip(onClick = pick(actions.scan), label = { Text("Scan") },
                    leadingIcon = { Icon(Icons.Filled.DocumentScanner, contentDescription = null) })
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(width = 56.dp, height = 28.dp).clip(CircleShape).clickable { open = !open },
        ) {
            Icon(
                if (open) Icons.Filled.KeyboardArrowDown else Icons.Filled.Add,
                contentDescription = if (open) "Hide add page options" else "Add pages",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
