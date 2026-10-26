package com.gerko.fourenote.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.gerko.fourenote.data.Note
import com.gerko.fourenote.data.ink.InkPage
import com.gerko.fourenote.data.ink.LinkTarget

/**
 * Picks where a link goes: a note, then (optionally) one of its pages. The note being edited is
 * listed first, since linking within a note is the common case.
 */
@Composable
fun LinkPickerDialog(viewModel: NoteEditorViewModel, onPick: (LinkTarget) -> Unit, onDismiss: () -> Unit) {
    val notes by produceState<List<Note>?>(null) { value = viewModel.linkableNotes() }
    var query by remember { mutableStateOf("") }
    // The note whose pages are being chosen; null shows the list of notes.
    var chosen by remember { mutableStateOf<Note?>(null) }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.8f).dp

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp, shadowElevation = 6.dp) {
            Column(Modifier.padding(16.dp).heightIn(max = maxHeight)) {
                val note = chosen
                if (note == null) {
                    Text("Link to…", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.size(12.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search notes") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.size(8.dp))
                    val all = notes
                    if (all == null) {
                        Text("Loading…", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        // This note first, then the rest by title.
                        val sorted = all.sortedWith(compareBy({ it.id != viewModel.noteId }, { it.title.lowercase() }))
                        val shown = sorted.filter { it.title.contains(query, ignoreCase = true) }
                        LazyColumn(Modifier.weight(1f, fill = false)) {
                            items(shown, key = { it.id }) { n ->
                                val title = n.title.ifBlank { "Untitled" }
                                Text(
                                    if (n.id == viewModel.noteId) "$title (this note)" else title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { chosen = n }
                                        .padding(horizontal = 8.dp, vertical = 12.dp),
                                )
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { chosen = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to notes")
                        }
                        Text(
                            note.title.ifBlank { "Untitled" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(LinkTarget(note.id)) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                    ) {
                        Icon(Icons.Filled.Description, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text("Whole note", style = MaterialTheme.typography.bodyLarge)
                    }
                    HorizontalDivider()
                    val pages by produceState<Pair<Boolean, List<InkPage>>?>(null, note.id) {
                        val loaded = viewModel.linkablePages(note.id)
                        value = (loaded != null) to (loaded ?: emptyList())
                    }
                    val loaded = pages
                    when {
                        loaded == null -> Text("Loading…", style = MaterialTheme.typography.bodyMedium)
                        !loaded.first -> Text(
                            "This note is locked: you can link to the whole note.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                        )
                        else -> LazyVerticalGrid(
                            columns = GridCells.Adaptive(88.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            // Without the weight the grid would take all the height and push Cancel out.
                            modifier = Modifier.weight(1f, fill = false).padding(top = 8.dp),
                        ) {
                            itemsIndexed(loaded.second, key = { _, page -> page.id }) { index, page ->
                                val number = index + 1
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(3f / 4f)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                        .clickable { onPick(LinkTarget(note.id, pageId = page.id)) },
                                ) {
                                    Text("Page $number", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.size(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    }
}
