package com.gerko.fourenote.ui.notelist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gerko.fourenote.sync.SyncUiState

@Composable
fun NoteListScreen(
    viewModel: NoteListViewModel,
    onOpenNote: (String) -> Unit,
) {
    val notes by viewModel.notes.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(syncState) {
        when (val state = syncState) {
            is SyncUiState.Success -> {
                val message = if (state.syncedCount == 1) {
                    "Synced 1 note to Google Drive"
                } else {
                    "Synced ${state.syncedCount} notes to Google Drive"
                }
                snackbarHostState.showSnackbar(message)
                viewModel.dismissSyncMessage()
            }
            is SyncUiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.dismissSyncMessage()
            }
            else -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("4ENote") },
                actions = {
                    IconButton(
                        onClick = { viewModel.syncNotes() },
                        enabled = syncState !is SyncUiState.InProgress,
                    ) {
                        if (syncState is SyncUiState.InProgress) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "Sync with Google Drive")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenNote(viewModel.createNote().id) }) {
                Icon(Icons.Filled.Add, contentDescription = "New note")
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(notes, key = { it.id }) { note ->
                ListItem(
                    headlineContent = { Text(note.title.ifBlank { "Untitled" }) },
                    modifier = Modifier.clickable { onOpenNote(note.id) },
                )
            }
        }
    }
}
