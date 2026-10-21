package com.gerko.fourenote.data

import com.gerko.fourenote.data.attachments.AttachmentStore
import com.gerko.fourenote.data.sync.RemoteFile
import com.gerko.fourenote.data.sync.SyncStore
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap

class NoteRepository(
    private val dao: NoteDao,
    private val attachments: AttachmentStore,
) : SyncStore {
    private val openNotes = ConcurrentHashMap.newKeySet<String>()

    fun observeAll(): Flow<List<Note>> = dao.observeAll()

    suspend fun getById(id: String): Note? = dao.getById(id)

    /** Saves a local edit; it will upload on the next sync. */
    suspend fun save(note: Note) = dao.upsert(note.copy(updatedAt = System.currentTimeMillis(), dirty = true))

    /**
     * A note that never reached the cloud is removed at once. Otherwise a
     * tombstone stays so the deletion reaches the cloud; sync removes it afterwards.
     */
    suspend fun delete(note: Note) {
        if (note.remoteId == null) purge(note.id) else dao.markDeleted(note.id, System.currentTimeMillis())
    }

    /** Called by the editor; sync skips open notes so it never overwrites ink being drawn. */
    fun markOpen(noteId: String) {
        openNotes.add(noteId)
    }

    fun markClosed(noteId: String) {
        openNotes.remove(noteId)
    }

    override suspend fun notesForSync(): List<Note> = dao.getAllForSync()

    override fun isOpen(noteId: String): Boolean = noteId in openNotes

    override suspend fun insertFromCloud(note: Note) = dao.upsert(note)

    override suspend fun replaceIfUnchanged(note: Note, expectedUpdatedAt: Long, dirty: Boolean): Boolean =
        dao.replaceContentIfUnchanged(
            id = note.id,
            title = note.title,
            inkData = note.inkData,
            createdAt = note.createdAt,
            updatedAt = note.updatedAt,
            dirty = dirty,
            expectedUpdatedAt = expectedUpdatedAt,
        ) > 0

    override suspend fun markSynced(
        noteId: String,
        file: RemoteFile,
        pdfId: String?,
        snapshot: String,
        expectedUpdatedAt: Long,
    ) = dao.markSynced(
        id = noteId,
        remoteId = file.id,
        remotePdfId = pdfId,
        remoteVersion = file.version,
        snapshot = snapshot,
        syncedAt = System.currentTimeMillis(),
        expectedUpdatedAt = expectedUpdatedAt,
    )

    override suspend fun purge(noteId: String) {
        dao.purge(noteId)
        attachments.deleteAll(noteId)
    }
}
