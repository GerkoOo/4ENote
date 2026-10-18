package com.gerko.fourenote.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.ink.strokes.Stroke
import com.gerko.fourenote.data.Note
import com.gerko.fourenote.data.NoteRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TITLE_SAVE_DEBOUNCE_MS = 500L
private const val PERIODIC_AUTOSAVE_INTERVAL_MS = 5_000L

/**
 * Owns the note being edited by [NoteEditorScreen]: loads it by [noteId], tracks in-memory title
 * and stroke edits, and persists them back through [repository].
 *
 * Saving strategy (there's no single Compose "on navigate away" hook that's guaranteed to run
 * after [androidx.lifecycle.ViewModel.onCleared] cancels [viewModelScope], so this combines three
 * paths rather than relying on just one):
 *  - every finished stroke is saved immediately (stroke completion is an infrequent, discrete
 *    event, so there's no need to debounce it),
 *  - title edits are saved after a short debounce so we're not hitting Room on every keystroke,
 *  - a periodic autosave runs in the background as a safety net while the screen is open.
 * [NoteEditorScreen] additionally calls [saveNow] synchronously when the user taps the back
 * button, which is the common "done editing" path.
 */
class NoteEditorViewModel(
    private val repository: NoteRepository,
    private val noteId: String,
) : ViewModel() {

    var title by mutableStateOf("")
        private set

    var strokes by mutableStateOf<List<Stroke>>(emptyList())
        private set

    var isLoaded by mutableStateOf(false)
        private set

    private var loadedNote: Note = Note(id = noteId, title = "")
    private var titleSaveJob: Job? = null

    init {
        viewModelScope.launch {
            val existing = repository.getById(noteId)
            loadedNote = existing ?: Note(id = noteId, title = "")
            title = loadedNote.title
            strokes = InkSerialization.decode(loadedNote.inkData)
            isLoaded = true
        }
        viewModelScope.launch {
            while (isActive) {
                delay(PERIODIC_AUTOSAVE_INTERVAL_MS)
                saveNow()
            }
        }
    }

    fun onTitleChanged(newTitle: String) {
        title = newTitle
        titleSaveJob?.cancel()
        titleSaveJob = viewModelScope.launch {
            delay(TITLE_SAVE_DEBOUNCE_MS)
            saveNow()
        }
    }

    /** Called when the authoring surface finishes one or more strokes (pen/finger lifted). */
    fun onStrokesFinished(newStrokes: List<Stroke>) {
        if (newStrokes.isEmpty()) return
        strokes = strokes + newStrokes
        saveNow()
    }

    fun clearCanvas() {
        strokes = emptyList()
        saveNow()
    }

    fun undoLastStroke() {
        if (strokes.isEmpty()) return
        strokes = strokes.dropLast(1)
        saveNow()
    }

    /** Persists the current title + strokes immediately, cancelling any pending debounce. */
    fun saveNow() {
        if (!isLoaded) return
        titleSaveJob?.cancel()
        val toSave = loadedNote.copy(title = title, inkData = InkSerialization.encode(strokes))
        loadedNote = toSave
        viewModelScope.launch { repository.save(toSave) }
    }

    class Factory(
        private val repository: NoteRepository,
        private val noteId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NoteEditorViewModel(repository, noteId) as T
    }
}
