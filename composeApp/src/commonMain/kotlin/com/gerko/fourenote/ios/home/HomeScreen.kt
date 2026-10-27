package com.gerko.fourenote.ios.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gerko.fourenote.ios.PageView
import com.gerko.fourenote.ios.store.NoteStore
import com.gerko.fourenote.ios.store.StoredNote
import com.gerko.fourenote.ios.store.nowMillis

/**
 * The home screen: a grid of note thumbnails, a button to make a new note, and a way into
 * settings. Long-pressing a note offers Rename and Delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    store: NoteStore,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
) {
    var notes by remember { mutableStateOf(store.all()) }
    var renaming by remember { mutableStateOf<StoredNote?>(null) }
    var deleting by remember { mutableStateOf<StoredNote?>(null) }

    // Read again each time the home page comes back (after the editor saved, for instance).
    // Every note is read whole: fine for a preview, a list of titles will be needed for many notes.

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("4ENote") },
                actions = {
                    TextButton(onClick = onSettings) { Text("Settings") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val note = store.create()
                    notes = store.all()
                    onOpen(note.id)
                },
                icon = { Text("+", style = MaterialTheme.typography.titleLarge) },
                text = { Text("New note") },
            )
        },
    ) { padding ->
        if (notes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No notes yet. Tap New note to start one.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(220.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        onOpen = { onOpen(note.id) },
                        onRename = { renaming = note },
                        onDelete = { deleting = note },
                    )
                }
            }
        }
    }

    renaming?.let { note ->
        RenameDialog(
            initial = note.title,
            onDismiss = { renaming = null },
            onConfirm = { title ->
                store.rename(note.id, title)
                notes = store.all()
                renaming = null
            },
        )
    }

    deleting?.let { note ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this note?") },
            text = { Text("\"${note.title}\" will be removed from this iPad.") },
            confirmButton = {
                TextButton(onClick = {
                    store.delete(note.id)
                    notes = store.all()
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun NoteCard(
    note: StoredNote,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val page = note.document.pages.firstOrNull()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true }),
    ) {
        Box {
            if (page != null) {
                PageView(
                    page = page,
                    paper = note.document.paper,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(page.width / page.height)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .background(Color.White),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = { menuOpen = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
        Column(Modifier.padding(12.dp)) {
            Text(
                text = note.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "edited ${relativeTime(note.updatedAt, nowMillis())}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename note") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim().ifEmpty { "Untitled" }) },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** "just now", "5 min ago", "3 h ago", "2 d ago", or a date for anything older. */
internal fun relativeTime(then: Long, now: Long): String {
    val seconds = ((now - then) / 1000).coerceAtLeast(0)
    return when {
        seconds < 60 -> "just now"
        seconds < 3600 -> "${seconds / 60} min ago"
        seconds < 86_400 -> "${seconds / 3600} h ago"
        seconds < 30L * 86_400 -> "${seconds / 86_400} d ago"
        else -> "${seconds / (30L * 86_400)} mo ago"
    }
}
