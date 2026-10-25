package com.gerko.fourenote.ui.notelist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gerko.fourenote.data.Note
import com.gerko.fourenote.data.NoteRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Deleted notes: restore them, or delete them for good. */
class TrashViewModel(
    private val repository: NoteRepository,
    private val onChanged: () -> Unit,
) : ViewModel() {
    /** Null until the first load from the database completes. */
    val notes: StateFlow<List<Note>?> = repository.observeTrashed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Ids of the notes ticked; empty when not selecting. */
    var selected by mutableStateOf(emptySet<String>())
        private set

    fun toggleSelected(noteId: String) {
        selected = if (noteId in selected) selected - noteId else selected + noteId
    }

    fun clearSelection() {
        selected = emptySet()
    }

    fun restoreSelected() {
        val chosen = selected
        clearSelection()
        viewModelScope.launch {
            repository.restoreFromTrash(chosen)
            onChanged()
        }
    }

    /** Deletes the ticked notes for good. */
    fun deleteSelected() {
        val chosen = notes.value.orEmpty().filter { it.id in selected }
        clearSelection()
        viewModelScope.launch {
            repository.deleteForever(chosen)
            onChanged()
        }
    }

    /** Deletes every note in Deleted notes for good. */
    fun emptyTrash() {
        val all = notes.value.orEmpty()
        clearSelection()
        viewModelScope.launch {
            repository.deleteForever(all)
            onChanged()
        }
    }

    class Factory(
        private val repository: NoteRepository,
        private val onChanged: () -> Unit,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TrashViewModel(repository, onChanged) as T
    }
}
