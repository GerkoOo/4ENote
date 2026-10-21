package com.gerko.fourenote.ui.notelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gerko.fourenote.data.Note
import com.gerko.fourenote.data.NoteRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoteListViewModel(
    private val repository: NoteRepository,
    private val onDeleted: () -> Unit,
) : ViewModel() {
    /** Null until the first load from the database completes. */
    val notes: StateFlow<List<Note>?> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun createNote(): Note {
        val note = Note(title = "Untitled")
        viewModelScope.launch { repository.save(note) }
        return note
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            repository.delete(note)
            onDeleted()
        }
    }

    class Factory(
        private val repository: NoteRepository,
        private val onDeleted: () -> Unit,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NoteListViewModel(repository, onDeleted) as T
    }
}
