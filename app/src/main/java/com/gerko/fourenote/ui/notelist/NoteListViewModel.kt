package com.gerko.fourenote.ui.notelist

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gerko.fourenote.data.Folder
import com.gerko.fourenote.data.Note
import com.gerko.fourenote.data.NoteRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.gerko.fourenote.data.scan.ScanInbox

class NoteListViewModel(
    private val repository: NoteRepository,
    private val onChanged: () -> Unit,
    private val inbox: ScanInbox,
) : ViewModel() {
    /** Null until the first load from the database completes. */
    val notes: StateFlow<List<Note>?> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    suspend fun thumbnail(noteId: String) = repository.loadThumbnail(noteId)

    /** Frames in any note waiting for a photo; refreshed whenever a note changes or arrives by sync. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val photoRequests: StateFlow<List<NoteRepository.PhotoRequest>> = repository.observeAll()
        .mapLatest { refreshInbox(); repository.photoRequests() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun decodePhoto(file: java.io.File) = repository.decodePhoto(file)

    /** Puts [photo] into the waiting frame; it reaches the other devices with the next sync. */
    fun fillPhotoRequest(request: NoteRepository.PhotoRequest, photo: ByteArray) {
        viewModelScope.launch {
            repository.fillPhotoRequest(request.noteId, request.frameId, photo)
            onChanged()
        }
    }

    /** Scans taken for later, waiting for the note with their frame. */
    var inboxScans by mutableStateOf(emptyList<ScanInbox.Scan>())
        private set

    fun refreshInbox() {
        inboxScans = inbox.pending()
    }

    /**
     * Keeps scans for later: for frames [firstFrame], [firstFrame] + 1, ... (0: the next empty
     * frames). Any whose frame is already on this device goes into it now; the rest sync.
     */
    fun addInboxScans(photos: List<ByteArray>, firstFrame: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                photos.forEachIndexed { i, bytes -> inbox.add(bytes, if (firstFrame == 0) 0 else firstFrame + i, System.currentTimeMillis() + i) }
            }
            repository.placeInboxScans(inbox)
            refreshInbox()
            onChanged()
        }
    }

    fun discardInboxScan(scan: ScanInbox.Scan) {
        inbox.discard(scan.name)
        refreshInbox()
        onChanged()
    }

    /** All folders (not deleted). */
    val folders: StateFlow<List<Folder>> = repository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The folder being looked at; null is the top of the home page. */
    var folderId by mutableStateOf<String?>(null)
        private set

    fun openFolder(id: String?) {
        clearSelection()
        folderId = id
    }

    /** The folders from the top down to [id], for the breadcrumb. */
    fun pathTo(id: String?, all: List<Folder>): List<Folder> {
        val byId = all.associateBy { it.id }
        val path = ArrayList<Folder>()
        var at = id
        while (at != null && path.size < 64) {
            val folder = byId[at] ?: break
            path.add(0, folder)
            at = folder.parentId
        }
        return path
    }

    /** Goes up one folder; false if already at the top. */
    fun goUp(): Boolean {
        val current = folderId ?: return false
        openFolder(folders.value.firstOrNull { it.id == current }?.parentId)
        return true
    }

    fun createFolder(name: String) {
        val parent = folderId
        viewModelScope.launch {
            repository.createFolder(name, parent)
            onChanged()
        }
    }

    fun renameFolder(folder: Folder, name: String) {
        viewModelScope.launch {
            repository.renameFolder(folder, name)
            onChanged()
        }
    }

    fun deleteFolder(folder: Folder) {
        viewModelScope.launch {
            repository.deleteFolder(folder)
            onChanged()
        }
    }

    fun moveFolder(folder: Folder, parentId: String?) {
        viewModelScope.launch {
            repository.moveFolder(folder, parentId)
            onChanged()
        }
    }

    /** Moves the ticked notes into [target] (null: the top). */
    fun moveSelected(target: String?) {
        val chosen = selected
        clearSelection()
        viewModelScope.launch {
            repository.moveNotes(chosen, target)
            onChanged()
        }
    }

    fun createNote(): Note {
        val note = Note(title = "Untitled", folderId = folderId)
        viewModelScope.launch { repository.create(note) }
        return note
    }

    /** Ids of the notes ticked for duplicating or deleting; empty when not selecting. */
    var selected by mutableStateOf(emptySet<String>())
        private set

    fun toggleSelected(noteId: String) {
        selected = if (noteId in selected) selected - noteId else selected + noteId
    }

    /** Ticks every note in the folder being looked at. */
    fun selectAll() {
        selected = notes.value.orEmpty().filter { it.folderId == folderId }.mapTo(HashSet()) { it.id }
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
        private val inbox: ScanInbox,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NoteListViewModel(repository, onChanged, inbox) as T
    }
}
