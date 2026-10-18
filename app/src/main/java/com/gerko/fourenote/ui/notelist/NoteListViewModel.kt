package com.gerko.fourenote.ui.notelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gerko.fourenote.data.Note
import com.gerko.fourenote.data.NoteRepository
import com.gerko.fourenote.sync.DriveSyncManager
import com.gerko.fourenote.sync.SyncUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoteListViewModel(
    private val repository: NoteRepository,
    private val driveSyncManager: DriveSyncManager,
) : ViewModel() {
    val notes: StateFlow<List<Note>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncState: StateFlow<SyncUiState> = driveSyncManager.state
    val signedInAccountEmail: StateFlow<String?> = driveSyncManager.signedInAccountEmail

    fun createNote(): Note {
        val note = Note(title = "Untitled")
        viewModelScope.launch { repository.save(note) }
        return note
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch { repository.delete(note) }
    }

    /** Signs in (if needed), requests Drive access (if needed), and uploads every note. */
    fun syncNotes() {
        viewModelScope.launch {
            driveSyncManager.signInAndSyncAll(notes.value)
        }
    }

    fun signOutOfDrive() {
        viewModelScope.launch { driveSyncManager.signOut() }
    }

    /** Call after showing a one-shot Success/Error sync message (e.g. a Snackbar). */
    fun dismissSyncMessage() {
        driveSyncManager.consumeState()
    }

    class Factory(
        private val repository: NoteRepository,
        private val driveSyncManager: DriveSyncManager,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NoteListViewModel(repository, driveSyncManager) as T
    }
}
