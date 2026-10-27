package com.gerko.fourenote.ios

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.gerko.fourenote.ios.editor.EditorState
import com.gerko.fourenote.ios.editor.InkEditor
import com.gerko.fourenote.ios.home.HomeScreen
import com.gerko.fourenote.ios.home.LockGate
import com.gerko.fourenote.ios.lock.AppLock
import com.gerko.fourenote.ios.settings.SettingsScreen
import com.gerko.fourenote.ios.store.NoteFiles
import com.gerko.fourenote.ios.store.NoteStore
import com.gerko.fourenote.ios.store.StoredNote
import com.gerko.fourenote.ios.store.nowMillis

private sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data class Editor(val noteId: String) : Screen
}

/** The iPad app: the lock (if on), then the notes, a note in the editor, or the settings. */
@Composable
fun App(
    files: NoteFiles = remember { NoteFiles() },
    lock: AppLock = remember { AppLock() },
) {
    val store = remember(files) { NoteStore(files).also { addSamplesOnce(files, it) } }
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            LockGate(lock) {
                var screen by remember { mutableStateOf<Screen>(Screen.Home) }
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    when (val current = screen) {
                        Screen.Home -> HomeScreen(
                            store = store,
                            onOpen = { screen = Screen.Editor(it) },
                            onSettings = { screen = Screen.Settings },
                        )
                        Screen.Settings -> SettingsScreen(lock = lock, onBack = { screen = Screen.Home })
                        is Screen.Editor -> NoteEditorScreen(store, current.noteId, onBack = { screen = Screen.Home })
                    }
                }
            }
        }
    }
}

/** A note open in the editor; every change is saved to the store straight away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditorScreen(store: NoteStore, noteId: String, onBack: () -> Unit) {
    val note = remember(noteId) { store.get(noteId) }
    if (note == null) {
        // Deleted meanwhile: back to the list.
        LaunchedEffect(noteId) { onBack() }
        return
    }
    val state = remember(noteId) {
        var saved: StoredNote = note
        EditorState(note.document) { document ->
            saved = saved.copy(document = document, updatedAt = nowMillis())
            store.save(saved)
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(note.title) },
            navigationIcon = { TextButton(onClick = onBack) { Text("‹ Notes") } },
        )
        InkEditor(state)
    }
}

/** The sample notes, on the very first start only (deleting them all doesn't bring them back). */
private fun addSamplesOnce(files: NoteFiles, store: NoteStore) {
    if (files.read(SAMPLES_ADDED) != null) return
    if (store.all().isEmpty()) {
        for (sample in SampleNotes.all) store.save(store.create(sample.title).copy(document = sample.document))
    }
    files.write(SAMPLES_ADDED, "1")
}

private const val SAMPLES_ADDED = ".samples-added"
