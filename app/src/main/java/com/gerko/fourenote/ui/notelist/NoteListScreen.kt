package com.gerko.fourenote.ui.notelist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

@Composable
fun NoteListScreen(
    viewModel: NoteListViewModel,
    onOpenNote: (String) -> Unit,
) {
    val notes by viewModel.notes.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("4ENote") }) },
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
