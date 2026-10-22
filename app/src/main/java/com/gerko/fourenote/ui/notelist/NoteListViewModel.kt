package com.gerko.fourenote.ui.notelist

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gerko.fourenote.data.Note
import com.gerko.fourenote.data.NoteRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoteListViewModel(
    private val repository: NoteRepository,
    private val onChanged: () -> Unit,
) : ViewModel() {
    /** Null until the first load from the database completes. */
    val notes: StateFlow<List<Note>?> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    suspend fun thumbnail(noteId: String) = repository.loadThumbnail(noteId)

    /** Frames in any note waiting for a photo; refreshed whenever a note changes or arrives by sync. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val photoRequests: StateFlow<List<NoteRepository.PhotoRequest>> = repository.observeAll()
        .mapLatest { repository.photoRequests() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun decodePhoto(file: java.io.File) = repository.decodePhoto(file)

    /** Puts [photo] into the waiting frame; it reaches the other devices with the next sync. */
    fun fillPhotoRequest(request: NoteRepository.PhotoRequest, photo: ByteArray) {
        viewModelScope.launch {
            repository.fillPhotoRequest(request.noteId, request.frameId, photo)
            onChanged()
        }
    }

    fun createNote(): Note {
        val note = Note(title = "Untitled")
        viewModelScope.launch { repository.create(note) }
        return note
    }

    /** Ids of the notes ticked for duplicating or deleting; empty when not selecting. */
    var selected by mutableStateOf(emptySet<String>())
        private set

    fun toggleSelected(noteId: String) {
        selected = if (noteId in selected) selected - noteId else selected + noteId
    }

    fun selectAll() {
        selected = notes.value.orEmpty().mapTo(HashSet()) { it.id }
    }

    fun clearSelection() {
        selected = emptySet()
    }

    fun duplicateSelected() {
        val chosen = notes.value.orEmpty().filter { it.id in selected }
        clearSelection()
        viewModelScope.launch {
            chosen.forEach { repository.duplicate(it) }
            onChanged()
        }
    }

    fun deleteSelected() {
        val chosen = notes.value.orEmpty().filter { it.id in selected }
        clearSelection()
        viewModelScope.launch {
            chosen.forEach { repository.delete(it) }
            onChanged()
        }
    }

    class Factory(
        private val repository: NoteRepository,
        private val onChanged: () -> Unit,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NoteListViewModel(repository, onChanged) as T
    }
}
