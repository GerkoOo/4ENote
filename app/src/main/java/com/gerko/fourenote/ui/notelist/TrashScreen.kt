package com.gerko.fourenote.ui.notelist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gerko.fourenote.data.Note
import com.gerko.fourenote.data.Trash

/** Deleted notes: tap to tick, then restore or delete for good. */
@Composable
fun TrashScreen(viewModel: TrashViewModel, onBack: () -> Unit) {
    val notes by viewModel.notes.collectAsState()
    val selecting = viewModel.selected.isNotEmpty()
    // What "Delete now" or "Empty" is about to delete for good, if anything.
    var confirming by remember { mutableStateOf<Confirm?>(null) }
    BackHandler(enabled = selecting) { viewModel.clearSelection() }

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Filled.Close, contentDescription = "Stop selecting")
                        }
                    },
                    title = { Text("${viewModel.selected.size} selected") },
                    actions = {
                        IconButton(onClick = viewModel::restoreSelected) {
                            Icon(Icons.Filled.Restore, contentDescription = "Restore")
                        }
                        IconButton(onClick = { confirming = Confirm.Selected(viewModel.selected.size) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete now")
                        }
                    },
                )
            } else TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Deleted notes") },
                actions = {
                    if (!notes.isNullOrEmpty()) {
                        TextButton(onClick = { confirming = Confirm.All }) { Text("Empty") }
                    }
                },
            )
        },
    ) { padding ->
        val all = notes ?: return@Scaffold
        if (all.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "No deleted notes. Deleted notes stay here for ${Trash.DAYS} days.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val now = System.currentTimeMillis()
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(all, key = { it.id }) { note ->
                    TrashRow(
                        note = note,
                        now = now,
                        selected = note.id in viewModel.selected,
                        onClick = { viewModel.toggleSelected(note.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    confirming?.let { confirm ->
        val count = when (confirm) {
            is Confirm.Selected -> confirm.count
            Confirm.All -> notes.orEmpty().size
        }
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(if (count == 1) "Delete note for good?" else "Delete $count notes for good?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    when (confirm) {
                        is Confirm.Selected -> viewModel.deleteSelected()
                        Confirm.All -> viewModel.emptyTrash()
                    }
                    confirming = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("Cancel") } },
        )
    }
}

private sealed interface Confirm {
    data class Selected(val count: Int) : Confirm
    data object All : Confirm
}

@Composable
private fun TrashRow(note: Note, now: Long, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                note.title.ifBlank { "Untitled" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                Trash.daysLeftText(note.trashedAt ?: now, now),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
