package com.gerko.fourenote.data

import android.util.Log
import com.gerko.fourenote.data.attachments.AttachmentStore
import com.gerko.fourenote.data.ink.InkDocument
import com.gerko.fourenote.data.ink.InkSerializer
import com.gerko.fourenote.data.ink.InkStore
import com.gerko.fourenote.data.sync.RemoteFile
import com.gerko.fourenote.data.sync.SyncStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap

/**
 * Notes: their details in Room, their ink in files ([InkStore]). Ink saved in the database by
 * older versions is moved to files once, in the background, before anything reads it.
 */
class NoteRepository(
    private val dao: NoteDao,
    private val attachments: AttachmentStore,
    private val ink: InkStore,
    scope: CoroutineScope,
) : SyncStore {
    private val openNotes = ConcurrentHashMap.newKeySet<String>()

    private val legacyMoved: Deferred<Unit> = scope.async { moveLegacyInk() }

    fun observeAll(): Flow<List<Note>> = dao.observeAll()

    suspend fun getById(id: String): Note? = dao.getById(id)

    /** The note's pages and ink; an empty note if it has none yet. */
    suspend fun loadDocument(noteId: String): InkDocument {
        legacyMoved.await()
        return ink.load(noteId) ?: readLegacyDocument(noteId) ?: InkDocument()
    }

    /** Paper, layers and the first page only, for the notes list. */
    suspend fun loadThumbnail(noteId: String): InkDocument? {
        legacyMoved.await()
        return ink.loadFirstPage(noteId)
    }

    /** Saves an edit from the editor; it will upload on the next sync. */
    suspend fun saveDocument(noteId: String, title: String, doc: InkDocument) {
        legacyMoved.await()
        ink.save(noteId, doc)
        if (dao.markEdited(noteId, title, System.currentTimeMillis()) == 0) {
            dao.upsert(Note(id = noteId, title = title))
        }
    }

    /** Saves a new note's details (it has no ink yet). */
    suspend fun create(note: Note) = dao.upsert(note.copy(updatedAt = System.currentTimeMillis(), dirty = true))

    /**
     * A note that never reached the cloud is removed at once. Otherwise a
     * tombstone stays so the deletion reaches the cloud; sync removes it afterwards.
     */
    suspend fun delete(note: Note) {
        if (note.remoteId == null) purge(note.id) else dao.markDeleted(note.id, System.currentTimeMillis())
    }

    /** A new note with the same pages, ink and pictures, titled as a copy. Uploads on the next sync. */
    suspend fun duplicate(note: Note): Note {
        legacyMoved.await()
        val copy = Note(title = "${note.title.ifBlank { "Untitled" }} (copy)")
        ink.copy(note.id, copy.id)
        attachments.copyAll(note.id, copy.id)
        dao.upsert(copy)
        return copy
    }

    /** An empty frame somewhere in the notes, waiting for a photo. */
    data class PhotoRequest(val noteId: String, val noteTitle: String, val pageNumber: Int, val frameId: String, val aspect: Float)

    /** Every frame waiting for a photo, in all notes. Reads only the notes' outlines, not their ink. */
    suspend fun photoRequests(): List<PhotoRequest> {
        legacyMoved.await()
        val notes = dao.getAllForSync().filterNot { it.deleted }
        return notes.flatMap { note ->
            val outline = ink.loadOutline(note.id) ?: return@flatMap emptyList()
            outline.pages.withIndex().flatMap { (index, page) ->
                page.images.filter { it.waitingForPhoto }.map {
                    PhotoRequest(note.id, note.title.ifBlank { "Untitled" }, index + 1, it.id, it.width / it.height)
                }
            }
        }
    }

    /**
     * Fills a waiting frame with a photo (JPEG bytes): the photo is stored with the note and
     * takes the frame's place, as large as fits inside it. Saved like any edit, so it syncs.
     */
    suspend fun fillPhotoRequest(noteId: String, frameId: String, photo: ByteArray) {
        legacyMoved.await()
        val note = dao.getById(noteId) ?: return
        val doc = ink.load(noteId) ?: return
        val stored = attachments.storeImage(noteId, photo.inputStream())
        val aspect = stored.widthPx.toFloat() / stored.heightPx.coerceAtLeast(1)
        val pages = doc.pages.map { page ->
            page.copy(
                images = page.images.map { frame ->
                    if (frame.id != frameId) return@map frame
                    val width = minOf(frame.width, frame.height * aspect)
                    frame.copy(file = stored.file, width = width, height = width / aspect)
                },
            )
        }
        ink.save(noteId, doc.copy(pages = pages))
        dao.markEdited(noteId, note.title, System.currentTimeMillis())
    }

    /** A photo for the scanner, the right way up and at most 3000 pixels on its long side. */
    fun decodePhoto(file: java.io.File) = attachments.decodeUpright(file)

    /** Called by the editor; sync skips open notes so it never overwrites ink being drawn. */
    fun markOpen(noteId: String) {
        openNotes.add(noteId)
    }

    fun markClosed(noteId: String) {
        openNotes.remove(noteId)
    }

    override suspend fun notesForSync(): List<Note> {
        legacyMoved.await()
        return dao.getAllForSync()
    }

    override suspend fun withContent(note: Note): Note = note.copy(
        inkData = InkSerializer.encode(loadDocument(note.id)),
        syncedSnapshot = ink.readSnapshot(note.id).orEmpty(),
    )

    override fun isOpen(noteId: String): Boolean = noteId in openNotes

    override fun attachments(noteId: String): Map<String, java.io.File> =
        attachments.dir(noteId).listFiles().orEmpty()
            // Files being written start with a dot.
            .filter { it.isFile && !it.name.startsWith(".") }
            .associateBy { it.name }

    override suspend fun saveAttachment(noteId: String, name: String, content: ByteArray) {
        attachments.saveDownloaded(noteId, name, content)
    }

    override suspend fun insertFromCloud(note: Note) {
        ink.save(note.id, InkSerializer.decode(note.inkData))
        if (note.syncedSnapshot.isNotEmpty()) ink.writeSnapshot(note.id, note.syncedSnapshot)
        dao.upsert(note.copy(inkData = "", syncedSnapshot = ""))
    }

    override suspend fun replaceIfUnchanged(note: Note, expectedUpdatedAt: Long, dirty: Boolean): Boolean {
        val replaced = dao.replaceContentIfUnchanged(
            id = note.id,
            title = note.title,
            createdAt = note.createdAt,
            updatedAt = note.updatedAt,
            dirty = dirty,
            expectedUpdatedAt = expectedUpdatedAt,
        ) > 0
        if (replaced) ink.save(note.id, InkSerializer.decode(note.inkData))
        return replaced
    }

    override suspend fun markSynced(
        noteId: String,
        file: RemoteFile,
        pdfId: String?,
        snapshot: String,
        expectedUpdatedAt: Long,
    ) {
        ink.writeSnapshot(noteId, snapshot)
        dao.markSynced(
            id = noteId,
            remoteId = file.id,
            remotePdfId = pdfId,
            remoteVersion = file.version,
            syncedAt = System.currentTimeMillis(),
            expectedUpdatedAt = expectedUpdatedAt,
        )
    }

    override suspend fun purge(noteId: String) {
        dao.purge(noteId)
        ink.delete(noteId)
        attachments.deleteAll(noteId)
    }

    /**
     * Moves ink and sync snapshots that older versions kept in the database into files. A note's
     * database copy is only cleared once its files have been written and read back identical.
     */
    private suspend fun moveLegacyInk() {
        for (id in dao.idsWithLegacyInk()) {
            try {
                val text = readLegacy(id, dao::legacyInkLength, dao::legacyInkPart)
                if (text.isNotBlank() && !ink.exists(id)) {
                    val doc = InkSerializer.decode(text)
                    // The decoder falls back to an empty note on damaged text; keep such a note where it is.
                    check(doc.pages.any { it.strokes.isNotEmpty() || it.background != null || it.images.isNotEmpty() } ||
                        text.length < 4096) { "ink could not be read" }
                    ink.save(id, doc)
                    val back = ink.load(id)
                    check(back != null && back.pages.size == doc.pages.size &&
                        back.pages.indices.all { back.pages[it].strokes.size == doc.pages[it].strokes.size }) {
                        "ink read back differently"
                    }
                }
                val snapshot = readLegacy(id, dao::legacySnapshotLength, dao::legacySnapshotPart)
                if (snapshot.isNotEmpty() && ink.readSnapshot(id) == null) ink.writeSnapshot(id, snapshot)
                dao.clearLegacyInk(id)
            } catch (e: Exception) {
                // Left in the database; loadDocument still reads it from there.
                Log.w(TAG, "Couldn't move note $id to files", e)
            }
        }
    }

    private suspend fun readLegacyDocument(noteId: String): InkDocument? =
        readLegacy(noteId, dao::legacyInkLength, dao::legacyInkPart).takeIf { it.isNotBlank() }?.let(InkSerializer::decode)

    // Reads a big text column in pieces small enough for Android's database cursor.
    private suspend fun readLegacy(
        id: String,
        length: suspend (String) -> Int?,
        part: suspend (String, Int, Int) -> String?,
    ): String {
        val total = length(id) ?: return ""
        val text = StringBuilder(total)
        var start = 1 // SQL counts from 1
        while (start <= total) {
            text.append(part(id, start, LEGACY_PART_CHARS).orEmpty())
            start += LEGACY_PART_CHARS
        }
        return text.toString()
    }

    private companion object {
        const val TAG = "NoteRepository"
        const val LEGACY_PART_CHARS = 256 * 1024
    }
}
