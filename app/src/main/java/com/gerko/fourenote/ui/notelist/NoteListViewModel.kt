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

class NoteListViewModel(private val repository: NoteRepository) : ViewModel() {
    val notes: StateFlow<List<Note>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createNote(): Note {
        val note = Note(title = "Untitled")
        viewModelScope.launch { repository.save(note) }
        return note
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch { repository.delete(note) }
    }

    class Factory(private val repository: NoteRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NoteListViewModel(repository) as T
    }
}
