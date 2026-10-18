package com.gerko.fourenote.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Placeholder editor screen. The handwriting canvas feature replaces the body
 * of this screen with an androidx.ink-based drawing surface bound to
 * Note.inkData; the Drive sync feature adds a sync action to the top bar.
 */
@Composable
fun NoteEditorScreen(noteId: String) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Note") }) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text("Handwriting canvas for note $noteId goes here")
        }
    }
}
