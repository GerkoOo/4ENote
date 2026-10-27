package com.gerko.fourenote.data

import android.util.Log
import com.gerko.fourenote.data.attachments.AttachmentStore
import com.gerko.fourenote.data.diagnostics.CrashLog
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Notes: their details in Room, their ink in files ([InkStore]). Ink saved in the database by
 * older versions is moved to files once, in the background, before anything reads it.
 */
class NoteRepository(
    private val dao: NoteDao,
    private val attachments: AttachmentStore,
    private val ink: InkStore,
    scope: CoroutineScope,
    private val folders: FolderDao? = null,
) : SyncStore {
    private val openNotes = ConcurrentHashMap.newKeySet<String>()

    private val legacyMoved: Deferred<Unit> = scope.async {
        moveLegacyInk()
        sealToMatch()
    }

    fun observeAll(): Flow<List<Note>> = dao.observeAll()

    /** The notes in Deleted notes, most recently trashed first. */
    fun observeTrashed(): Flow<List<Note>> = dao.observeTrashed()

    /** The home page's folders (not the deleted ones). */
    fun observeFolders(): Flow<List<Folder>> = folders?.observeAll() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun createFolder(name: String, parentId: String?): Folder {
        val folder = Folder(name = name.trim().ifEmpty { "New folder" }, parentId = parentId)
        folders?.upsert(folder)
        return folder
    }

    suspend fun renameFolder(folder: Folder, name: String) {
        folders?.upsert(folder.copy(name = name.trim().ifEmpty { folder.name }, updatedAt = System.currentTimeMillis()))
    }

    /**
     * Deletes a folder without losing anything: its notes and folders move up into the folder it
     * was in. It stays as a tombstone so the deletion syncs.
     */
    suspend fun deleteFolder(folder: Folder) {
        val dao = folders ?: return
        val now = System.currentTimeMillis()
        dao.getAll().filter { it.parentId == folder.id && !it.deleted }
            .forEach { dao.upsert(it.copy(parentId = folder.parentId, updatedAt = now)) }
        val inside = this.dao.getAllForSync().filter { it.folderId == folder.id && !it.deleted }.map { it.id }
        if (inside.isNotEmpty()) this.dao.moveToFolder(inside, folder.parentId, now)
        dao.upsert(folder.copy(deleted = true, updatedAt = now))
    }

    /** Locks or unlocks notes; the lock syncs, so it reaches the other devices. */
    suspend fun setLocked(noteIds: Collection<String>, locked: Boolean) {
        if (noteIds.isEmpty()) return
        dao.setLocked(noteIds.toList(), locked, System.currentTimeMillis())
        for (id in noteIds) seal(id, locked)
    }

    /** Encrypts locked notes' ink and pictures on this device and decrypts unlocked ones. */
    private suspend fun seal(noteId: String, locked: Boolean) {
        try {
            ink.setSealed(noteId, locked)
            attachments.setSealed(noteId, locked)
        } catch (e: Exception) {
            // Left as it is; the next start tries again.
            Log.w(TAG, "Couldn't ${if (locked) "encrypt" else "decrypt"} note $noteId", e)
            CrashLog.record("Couldn't ${if (locked) "encrypt" else "decrypt"} note $noteId", e)
        }
    }

    /** Brings every note's files in line with its lock: after a crash halfway, or an older version. */
    private suspend fun sealToMatch() {
        for (note in dao.getAllForSync()) seal(note.id, note.locked)
    }

    /** Moves notes into [folderId] (null: the top of the home page). */
    suspend fun moveNotes(noteIds: Collection<String>, folderId: String?) {
        if (noteIds.isNotEmpty()) dao.moveToFolder(noteIds.toList(), folderId, System.currentTimeMillis())
    }

    /** Moves notes to Deleted notes, where they stay for [Trash.DAYS] days. */
    suspend fun moveToTrash(noteIds: Collection<String>) {
        if (noteIds.isNotEmpty()) dao.setTrashed(noteIds.toList(), System.currentTimeMillis())
    }

    /**
     * Takes notes back out of Deleted notes. A note whose folder is gone (deleted here or on
     * another device) goes to the top of the home page instead of disappearing.
     */
    suspend fun restoreFromTrash(noteIds: Collection<String>) {
        if (noteIds.isEmpty()) return
        val live = folders?.getAll()?.filterNot { it.deleted }?.mapTo(HashSet()) { it.id }.orEmpty()
        for (id in noteIds) {
            val note = dao.getById(id) ?: continue
            if (note.folderId != null && note.folderId !in live) dao.moveToFolder(listOf(id), null, note.updatedAt)
        }
        dao.setTrashed(noteIds.toList(), null)
    }

    /** Deletes notes for good, from Deleted notes or anywhere else. */
    suspend fun deleteForever(notes: Collection<Note>) {
        notes.forEach { delete(it) }
    }

    /** Deletes for good the notes that have been in Deleted notes for [Trash.DAYS] days. */
    suspend fun purgeExpiredTrash() {
        val now = System.currentTimeMillis()
        val expired = dao.idsTrashedBefore(now - Trash.DAYS * 24L * 60 * 60 * 1000)
        if (expired.isEmpty()) return
        val byId = dao.getAllForSync().associateBy { it.id }
        deleteForever(expired.mapNotNull { byId[it] })
    }

    /** Moves a folder into another (null: the top), unless that would put it inside itself. */
    suspend fun moveFolder(folder: Folder, parentId: String?) {
        val dao = folders ?: return
        val all = dao.getAll().associateBy { it.id }
        var at = parentId
        while (at != null) {
            if (at == folder.id) return
            at = all[at]?.parentId
        }
        dao.upsert(folder.copy(parentId = parentId, updatedAt = System.currentTimeMillis()))
    }

    /** Backups of all notes and folders (see Backup). */
    val backup = com.gerko.fourenote.data.backup.Backup(
        inkRoot = ink.root,
        attachmentsRoot = attachments.root,
        notes = { legacyMoved.await(); dao.getAllForSync() },
        folders = { folders?.getAll().orEmpty() },
        saveNote = { dao.upsert(it) },
        saveFolders = { folders?.upsertAll(it) },
        forgetInk = { id -> ink.forget(id) },
        readInk = { id, file -> ink.plainBytes(id, file) },
        openAttachment = { file -> attachments.openPlain(file) },
        sealInk = { id, locked -> seal(id, locked) },
    )

    override suspend fun foldersForSync(): List<Folder>? = folders?.getAll()

    override suspend fun saveFoldersFromSync(folders: List<Folder>) {
        this.folders?.upsertAll(folders)
    }

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

    // One writer at a time per note between reading its ink and stamping the edit: sync's merge,
    // a photo put into a frame from the home page, an editor save. Otherwise one could write over
    // what another just wrote (sync's check on updatedAt only sees an edit once it is stamped).
    private val noteLocks = ConcurrentHashMap<String, Mutex>()

    private suspend fun <T> withNoteLock(noteId: String, block: suspend () -> T): T =
        noteLocks.getOrPut(noteId) { Mutex() }.withLock { block() }

    /** Saves an edit from the editor; it will upload on the next sync. */
    suspend fun saveDocument(noteId: String, title: String, doc: InkDocument) {
        legacyMoved.await()
        withNoteLock(noteId) {
            ink.save(noteId, doc)
            if (dao.markEdited(noteId, title, System.currentTimeMillis()) == 0) {
                dao.upsert(Note(id = noteId, title = title))
            }
        }
    }

    /** Saves a new note's details (it has no ink yet). */
    suspend fun create(note: Note) = dao.upsert(note.copy(updatedAt = System.currentTimeMillis(), dirty = true))

    /**
     * A note that never reached the cloud is removed at once. Otherwise a
     * tombstone stays so the deletion reaches the cloud; sync removes it afterwards.
     */
    suspend fun delete(note: Note) {
        // Decided on the note as stored now: the caller's copy may be from before a sync uploaded it.
        val current = dao.getById(note.id) ?: return
        if (!current.isInCloud) purge(note.id) else dao.markDeleted(note.id, System.currentTimeMillis())
    }

    /** A new note with the same pages, ink and pictures, titled as a copy. Uploads on the next sync. */
    suspend fun duplicate(note: Note): Note {
        legacyMoved.await()
        val copy = Note(
            title = "${note.title.ifBlank { "Untitled" }} (copy)",
            folderId = note.folderId,
            locked = note.locked,
        )
        ink.copy(note.id, copy.id)
        attachments.copyAll(note.id, copy.id)
        dao.upsert(copy)
        return copy
    }

    /** An empty frame somewhere in the notes, waiting for a photo. */
    data class PhotoRequest(
        val noteId: String,
        val noteTitle: String,
        val pageNumber: Int,
        val frameId: String,
        val aspect: Float,
        val label: String = "",
    )

    /** Every frame waiting for a photo, in all notes. Reads only the notes' outlines, not their ink. */
    suspend fun photoRequests(): List<PhotoRequest> {
        legacyMoved.await()
        val notes = dao.getAllForSync().filterNot { it.deleted || it.trashedAt != null }
        return notes.flatMap { note ->
            val outline = ink.loadOutline(note.id) ?: return@flatMap emptyList()
            outline.pages.withIndex().flatMap { (index, page) ->
                page.images.filter { it.waitingForPhoto }.map {
                    PhotoRequest(note.id, note.title.ifBlank { "Untitled" }, index + 1, it.id, it.width / it.height, it.label)
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
        val stored = attachments.storeImage(noteId, photo.inputStream())
        val aspect = stored.widthPx.toFloat() / stored.heightPx.coerceAtLeast(1)
        fun fill(frame: com.gerko.fourenote.data.ink.PageImage): com.gerko.fourenote.data.ink.PageImage {
            val width = minOf(frame.width, frame.height * aspect)
            return frame.copy(file = stored.file, width = width, height = width / aspect)
        }
        // A note open in the editor is saved by the editor; it takes the photo into its frame itself.
        if (noteId in openNotes) {
            val frame = ink.loadOutline(noteId)?.pages?.flatMap { it.images }?.firstOrNull { it.id == frameId } ?: return
            frameFills.emit(noteId to listOf(fill(frame)))
            return
        }
        withNoteLock(noteId) {
            val doc = ink.load(noteId) ?: return@withNoteLock
            val pages = doc.pages.map { page -> page.copy(images = page.images.map { if (it.id == frameId) fill(it) else it }) }
            ink.save(noteId, doc.copy(pages = pages))
            // The title as it is now: it may have been renamed while the photo was stored.
            dao.markEdited(noteId, dao.getById(noteId)?.title ?: note.title, System.currentTimeMillis())
        }
    }

    /**
     * Puts waiting inbox scans into their frames, in any note on this device: a scan for
     * "Photo N" goes into the empty frame named Photo N, a scan for any frame into the first empty
     * one (lowest number); the most recently edited note goes first. Returns how many were placed.
     */
    suspend fun placeInboxScans(inbox: com.gerko.fourenote.data.scan.ScanInbox): Int {
        val scans = inbox.pending()
        if (scans.isEmpty()) return 0
        val notes = dao.getAllForSync().filterNot { it.deleted || it.trashedAt != null }.associateBy { it.id }
        val open = photoRequests()
            .sortedWith(
                compareByDescending<PhotoRequest> { notes[it.noteId]?.updatedAt ?: 0L }
                    .thenBy { com.gerko.fourenote.data.scan.ScanInbox.frameNumber(it.label) ?: Int.MAX_VALUE },
            )
            .toMutableList()
        var placed = 0
        // Scans for a named frame first, so a scan for any frame doesn't take their place.
        for (scan in scans.sortedByDescending { it.frame != 0 }) {
            val request = open.firstOrNull {
                scan.frame == 0 || com.gerko.fourenote.data.scan.ScanInbox.frameNumber(it.label) == scan.frame
            } ?: continue
            val bytes = runCatching { scan.file.readBytes() }.getOrNull() ?: continue
            fillPhotoRequest(request.noteId, request.frameId, bytes)
            inbox.markUsed(scan.name)
            open.remove(request)
            placed++
        }
        return placed
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

    override suspend fun withContent(note: Note): Note = note.copy(inkData = InkSerializer.encode(loadDocument(note.id)))

    override suspend fun syncedSnapshot(note: Note): String = ink.readSnapshot(note.id).orEmpty()

    override suspend fun sentSnapshot(note: Note, version: String): String? =
        if (version == note.remoteVersion) syncedSnapshot(note).takeIf { it.isNotEmpty() } else ink.readSent(note.id, version)

    override suspend fun peerVersions(noteId: String): Map<String, String> = ink.peerVersions(noteId)

    override suspend fun peerSnapshot(noteId: String, deviceId: String): String? = ink.readPeer(noteId, deviceId)

    override suspend fun savePeer(noteId: String, deviceId: String, version: String, compressed: ByteArray) =
        ink.writePeer(noteId, deviceId, version, compressed)

    /**
     * Another cloud was chosen. Its notes have nothing to do with what was synced before: without
     * this, notes missing there would count as deleted on another device and be removed here.
     */
    suspend fun forgetCloud() {
        dao.forgetCloud()
        for (note in dao.getAllForSync()) ink.forgetSync(note.id)
    }

    override suspend fun forgetPeer(noteId: String, deviceId: String) {
        ink.forgetPeer(noteId, deviceId)
    }

    override fun isOpen(noteId: String): Boolean = noteId in openNotes

    /** Photo frames filled elsewhere for notes open in the editor: note id and the filled frames. */
    val frameFills = kotlinx.coroutines.flow.MutableSharedFlow<Pair<String, List<com.gerko.fourenote.data.ink.PageImage>>>(extraBufferCapacity = 16)

    override suspend fun deliverFilledFrames(noteId: String, frames: List<com.gerko.fourenote.data.ink.PageImage>) {
        frameFills.emit(noteId to frames)
    }

    override fun attachments(noteId: String): Map<String, java.io.File> =
        attachments.dir(noteId).listFiles().orEmpty()
            // Files being written start with a dot.
            .filter { it.isFile && !it.name.startsWith(".") }
            .associateBy { it.name }

    override fun attachmentBytes(noteId: String, file: java.io.File): ByteArray = attachments.readPlain(file)

    override fun attachmentUpload(noteId: String, file: java.io.File): com.gerko.fourenote.data.sync.UploadContent =
        // A sealed file goes up as its plain content, streamed (a PDF can be huge).
        if (attachments.isSealedFile(file)) com.gerko.fourenote.data.sync.UploadContent(attachments.plainLength(file)) { attachments.openPlain(file) }
        else com.gerko.fourenote.data.sync.UploadContent.of(file)

    override suspend fun saveAttachment(noteId: String, name: String, content: ByteArray) {
        attachments.saveDownloaded(noteId, name, content)
    }

    override suspend fun insertFromCloud(note: Note) {
        // Sealed before the first byte is written, if it comes locked.
        seal(note.id, note.locked)
        ink.save(note.id, InkSerializer.decode(note.inkData))
        dao.upsert(note.copy(inkData = "", syncedSnapshot = ""))
    }

    override suspend fun replaceIfUnchanged(note: Note, expectedUpdatedAt: Long, dirty: Boolean): Boolean = withNoteLock(note.id) {
        // Deleted notes are local to this device: a cloud copy must not bring one back.
        val trashedAt = dao.getById(note.id)?.trashedAt
        val replaced = dao.replaceContentIfUnchanged(
            id = note.id,
            title = note.title,
            createdAt = note.createdAt,
            updatedAt = note.updatedAt,
            dirty = dirty,
            expectedUpdatedAt = expectedUpdatedAt,
            folderId = note.folderId,
            locked = note.locked,
            rev = note.rev,
            remotePdfId = note.remotePdfId,
        ) > 0
        if (replaced) {
            seal(note.id, note.locked)
            ink.save(note.id, InkSerializer.decode(note.inkData))
            if (trashedAt != null) dao.setTrashed(listOf(note.id), trashedAt)
        }
        replaced
    }

    /** See [InkStore.syncHistoryBytes]. */
    suspend fun syncHistoryBytes(): Long = ink.syncHistoryBytes()

    override fun reportProblem(noteId: String, message: String) {
        CrashLog.record("Sync, note ${noteId.take(8)}", IllegalStateException(message))
    }

    override suspend fun purgeIfUnchanged(noteId: String, expectedUpdatedAt: Long): Boolean = withNoteLock(noteId) {
        val current = dao.getById(noteId)
        if (current == null || current.dirty || current.updatedAt != expectedUpdatedAt) return@withNoteLock false
        purge(noteId)
        true
    }

    override suspend fun markSynced(
        noteId: String,
        file: RemoteFile,
        pdfId: String?,
        snapshot: ByteArray,
        expectedUpdatedAt: Long,
        rev: Long,
    ) {
        ink.writeCompressedSnapshot(noteId, snapshot)
        ink.recordSent(noteId, file.version, snapshot)
        dao.markSynced(
            id = noteId,
            remoteId = file.id,
            remotePdfId = pdfId,
            remoteVersion = file.version,
            syncedAt = System.currentTimeMillis(),
            expectedUpdatedAt = expectedUpdatedAt,
            rev = rev,
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
                CrashLog.record("Couldn't move note $id to files", e)
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
