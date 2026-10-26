package com.gerko.fourenote.data.sync

import com.gerko.fourenote.data.ink.InkSerializer
import com.gerko.fourenote.data.ink.PageImage
import com.gerko.fourenote.data.Note
import com.gerko.fourenote.data.scan.ScanInbox
import com.gerko.fourenote.data.security.LockFile
import com.gerko.fourenote.data.security.LockedNoteFile
import com.gerko.fourenote.data.security.NotesKey
import com.gerko.fourenote.data.security.NotesKeyring
import java.io.File

/** The local side of sync; implemented over Room by NoteRepository. */
interface SyncStore {
    /** Every note, including deletion tombstones, without its ink (see [withContent]). */
    suspend fun notesForSync(): List<Note>

    /** [note] with its ink and last-synced snapshot filled in, for uploading or merging. */
    suspend fun withContent(note: Note): Note = note

    /** The note's pictures and PDF pages on this device: file name to file. They never change once written. */
    fun attachments(noteId: String): Map<String, File> = emptyMap()

    /** Stores a picture or PDF page that came from the cloud. */
    suspend fun saveAttachment(noteId: String, name: String, content: ByteArray) {}

    /**
     * Photo frames filled on another device, for a note that is open here (and so not synced
     * as a whole): the open editor puts the photos into its frames straight away.
     */
    suspend fun deliverFilledFrames(noteId: String, frames: List<PageImage>) {}

    /** The home page's folders, tombstones included; null if this store has none (they don't sync). */
    suspend fun foldersForSync(): List<com.gerko.fourenote.data.Folder>? = null

    /** Stores the folders as merged with the cloud's. */
    suspend fun saveFoldersFromSync(folders: List<com.gerko.fourenote.data.Folder>) {}

    /** True while the note is open in the editor; sync leaves it alone until it closes. */
    fun isOpen(noteId: String): Boolean

    suspend fun insertFromCloud(note: Note)

    /** Replaces content unless the user edited the note after [expectedUpdatedAt]. */
    suspend fun replaceIfUnchanged(note: Note, expectedUpdatedAt: Long, dirty: Boolean): Boolean

    suspend fun markSynced(
        noteId: String,
        file: RemoteFile,
        pdfId: String?,
        snapshot: String,
        expectedUpdatedAt: Long,
    )

    suspend fun purge(noteId: String)
}

data class SyncResult(
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val merged: Int = 0,
    val deletedHere: Int = 0,
    val deletedInCloud: Int = 0,
    val skippedOpen: Int = 0,
    val errors: List<String> = emptyList(),
    /** Locked notes left alone because this device lacks the notes key (see NotesKeyring). */
    val needPassword: Int = 0,
)

/**
 * Two-way sync between the local notes and one cloud folder: an edit on any
 * device reaches all of them. For each note it compares the local "dirty" flag
 * with whether the cloud file's version moved since the last sync:
 *
 * | changed here | changed in cloud | action                         |
 * |--------------|------------------|--------------------------------|
 * | no           | no               | nothing                        |
 * | yes          | no               | upload                         |
 * | no           | yes              | download                       |
 * | yes          | yes              | merge ([NoteMerger]), upload   |
 *
 * Deletions travel as tombstones locally and as missing files in the cloud.
 * Each note also gets a PDF copy when [pdfExporter] is set, so notes can be
 * opened from any computer.
 */
class SyncEngine(
    private val store: SyncStore,
    private val provider: CloudProvider,
    /** Writes the note's PDF copy to a temporary file (deleted after upload), or returns null to skip it. */
    private val pdfExporter: ((Note) -> File?)? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Scans waiting for their photo frame, kept in the cloud too so any device can place them. */
    private val inbox: ScanInbox? = null,
    /** Encrypts locked notes and their pictures; without it they sync readable, as before encryption. */
    private val keys: NotesKeyring? = null,
) {
    private var uploaded = 0
    private var downloaded = 0
    private var merged = 0
    private var deletedHere = 0
    private var deletedInCloud = 0
    /** Notes whose cloud copy is encrypted with a key this device lacks: nothing of theirs is touched. */
    private val needKey = HashSet<String>()
    /** Notes not brought in step with the cloud this time (open, failed, or [needKey]). */
    private val unsettled = HashSet<String>()

    suspend fun sync(): SyncResult {
        uploaded = 0
        downloaded = 0
        merged = 0
        deletedHere = 0
        deletedInCloud = 0
        needKey.clear()
        unsettled.clear()
        var skippedOpen = 0
        val errors = mutableListOf<String>()

        val listed = provider.listFiles()
        // The lock file goes first: whether locked notes may be encrypted (or read) depends on it.
        keys?.let { syncLockFile(it, listed) }
        val cloudNotes = listed
            .mapNotNull { file -> NoteFile.noteIdOf(file.name)?.let { it to file } }
            .toMap()
        val localNotes = store.notesForSync()

        // Filled photo frames for open notes, handed over once their photos have downloaded.
        val fills = mutableListOf<Pair<String, List<PageImage>>>()
        for (note in localNotes) {
            if (store.isOpen(note.id)) {
                val cloud = cloudNotes[note.id]
                // An open note can still be uploaded (that only reads it), so a frame set on the tablet
                // reaches the phone at once. Anything that would change it here waits until it is closed,
                // except photos for its frames, which the editor takes in itself.
                val uploadOnly = !note.deleted && note.dirty &&
                    (if (cloud == null) note.remoteId == null else cloud.version == note.remoteVersion)
                if (uploadOnly) {
                    runCatching { syncNote(note, cloud) }
                        .onFailure { errors += "${note.title.ifBlank { "Untitled" }}: ${it.message}" }
                    unsettled += note.id
                    continue
                }
                unsettled += note.id
                skippedOpen++
                if (cloud != null && cloud.version != note.remoteVersion) {
                    runCatching { filledFrames(cloud, note.id) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { fills += note.id to it }
                }
                continue
            }
            runCatching { syncNote(note, cloudNotes[note.id]) }
                .onFailure {
                    unsettled += note.id
                    errors += "${note.title.ifBlank { "Untitled" }}: ${it.message}"
                }
        }

        val localIds = localNotes.mapTo(HashSet()) { it.id }
        for ((id, file) in cloudNotes) {
            if (id in localIds) continue
            runCatching { downloadNew(file) }
                .onFailure { errors += "${file.name}: ${it.message}" }
        }

        runCatching { syncAttachments() }.onFailure { errors += "Pictures: ${it.message}" }
        runCatching { syncFolders() }.onFailure { errors += "Folders: ${it.message}" }
        inbox?.let { runCatching { syncInbox(it) }.onFailure { errors += "Scans: ${it.message}" } }
        for ((noteId, frames) in fills) store.deliverFilledFrames(noteId, frames)

        keys?.reportWaiting(needKey.size)
        return SyncResult(uploaded, downloaded, merged, deletedHere, deletedInCloud, skippedOpen, errors, needKey.size)
    }

    private suspend fun syncLockFile(keys: NotesKeyring, files: List<RemoteFile>) {
        val cloud = files.firstOrNull { it.name == LockFile.NAME }
        val text = cloud?.let { provider.download(it.id).decodeToString() }
        val upload = keys.reconcile(text) ?: return
        provider.upload(LockFile.NAME, LockFile.MIME_TYPE, UploadContent.of(upload.encodeToByteArray()), cloud?.id)
        keys.uploaded(upload)
    }

    /**
     * The plain note file of [cloud], or null when it is encrypted with a key this device lacks
     * (the note is then left alone and counted in [needKey]).
     */
    private suspend fun readNote(cloud: RemoteFile, noteId: String): String? {
        val text = provider.download(cloud.id).decodeToString()
        if (!LockedNoteFile.isLocked(text)) return text
        val key = keys?.key()?.takeIf { it.id == LockedNoteFile.keyId(text) }
        if (key == null) {
            needKey += noteId
            unsettled += noteId
            return null
        }
        val plain = LockedNoteFile.decode(text, key)
        // The id inside is the one the file was encrypted for; a file copied over another note's name is refused.
        require(NoteFile.decode(plain).id == noteId) { "Note file under the wrong name" }
        keys.encryptedVersion(noteId, cloud.version)
        return plain
    }

    /**
     * What a locked note is uploaded with: the notes key; null to upload it readable (no
     * 4ENote password anywhere yet); or [Skip] while the key is missing here or not in the cloud yet.
     */
    private sealed interface Sealing {
        data class With(val key: NotesKey) : Sealing
        data object Plain : Sealing
        data object Skip : Sealing
    }

    private fun sealingFor(note: Note): Sealing {
        if (!note.locked || keys == null) return Sealing.Plain
        keys.keyForUpload()?.let { return Sealing.With(it) }
        return if (keys.state.value == NotesKeyring.State.OFF) Sealing.Plain else Sealing.Skip
    }

    /**
     * Pictures and PDF pages travel as files named "noteId~fileName". Their names are unique and
     * their content never changes, so syncing them is copying whichever side lacks one; files of
     * notes that exist on neither side any more are removed from the cloud.
     */
    private suspend fun syncAttachments() {
        val files = provider.listFiles()
        val cloudNoteIds = files.mapNotNullTo(HashSet()) { NoteFile.noteIdOf(it.name) }
        val inCloud = files.mapNotNull { file -> NoteFile.attachmentOf(file.name)?.let { it to file } }
        val notes = store.notesForSync().filterNot { it.deleted }
        val localIds = notes.mapTo(HashSet()) { it.id }
        val cloudByNote = inCloud.groupBy({ it.first.first }, { it.first.second to it.second })

        for (note in notes) {
            if (note.id in needKey) continue
            val sealing = sealingFor(note)
            if (sealing == Sealing.Skip) continue
            val key = (sealing as? Sealing.With)?.key
            val local = store.attachments(note.id)
            // Encrypted copies are named "<file>.4el"; a note keeps one kind, readable or encrypted.
            val all = cloudByNote[note.id].orEmpty()
            val plainRemote = all.filterNot { it.first.endsWith(ENCRYPTED_SUFFIX) }.toMap()
            val sealedRemote = all.filter { it.first.endsWith(ENCRYPTED_SUFFIX) }.associate { it.first.removeSuffix(ENCRYPTED_SUFFIX) to it.second }
            val wanted = if (key != null) sealedRemote else plainRemote
            val other = if (key != null) plainRemote else sealedRemote
            // A note whose lock may have changed elsewhere (not in step this time) gets nothing swapped.
            val settled = note.id !in unsettled
            for ((name, file) in local) {
                if (name in wanted || (!settled && name in other)) continue
                if (key != null) {
                    val cloudName = NoteFile.attachmentName(note.id, name) + ENCRYPTED_SUFFIX
                    val sealed = key.encrypt(file.readBytes(), cloudName)
                    provider.upload(cloudName, "application/octet-stream", UploadContent.of(sealed), null)
                } else {
                    provider.upload(NoteFile.attachmentName(note.id, name), mimeType(name), UploadContent.of(file), null)
                }
                other[name]?.let { provider.delete(it.id) }
            }
            for ((name, file) in plainRemote) {
                if (name !in local) store.saveAttachment(note.id, name, provider.download(file.id))
            }
            for ((name, file) in sealedRemote) {
                if (name in local || name in plainRemote) continue
                val cloudName = NoteFile.attachmentName(note.id, name) + ENCRYPTED_SUFFIX
                val noteKey = keys?.key() ?: continue
                store.saveAttachment(note.id, name, noteKey.decrypt(provider.download(file.id), cloudName))
            }
        }
        for ((key, file) in inCloud) {
            val noteId = key.first
            if (noteId !in localIds && noteId !in cloudNoteIds) provider.delete(file.id)
        }
    }

    /**
     * Waiting scans travel as "scan-inbox_<name>". A scan placed in a frame on any device is
     * deleted from the cloud; a device that uploaded it then sees it gone and drops it too.
     */
    private suspend fun syncInbox(inbox: ScanInbox) {
        val cloud = provider.listFiles()
            .filter { it.name.startsWith(ScanInbox.CLOUD_PREFIX) }
            .associateBy { it.name.removePrefix(ScanInbox.CLOUD_PREFIX) }
        val used = inbox.used()
        for (name in used) {
            cloud[name]?.let { provider.delete(it.id) }
            inbox.forgetUsed(name)
        }
        val local = inbox.pending()
        for (scan in local) {
            when {
                scan.name in cloud -> inbox.markSynced(scan.name)
                inbox.isSynced(scan.name) -> inbox.removedElsewhere(scan.name)
                else -> {
                    provider.upload(ScanInbox.CLOUD_PREFIX + scan.name, "image/jpeg", UploadContent.of(scan.file), null)
                    inbox.markSynced(scan.name)
                }
            }
        }
        val localNames = local.mapTo(HashSet()) { it.name }
        for ((name, file) in cloud) {
            if (name in localNames || name in used || !ScanInbox.isValidName(name)) continue
            inbox.saveFromCloud(name, provider.download(file.id))
        }
    }

    /** The folders file: merged with what is here, and written back wherever it differs. */
    private suspend fun syncFolders() {
        val local = store.foldersForSync() ?: return
        val cloud = provider.listFiles().firstOrNull { it.name == com.gerko.fourenote.data.FolderFile.NAME }
        val remote = cloud?.let { com.gerko.fourenote.data.FolderFile.decode(provider.download(it.id).decodeToString()) }.orEmpty()
        val merged = com.gerko.fourenote.data.FolderFile.merge(local, remote)
        if (merged != local.sortedBy { it.id }) store.saveFoldersFromSync(merged)
        if (merged != remote.sortedBy { it.id }) {
            val text = com.gerko.fourenote.data.FolderFile.encode(merged)
            provider.upload(
                com.gerko.fourenote.data.FolderFile.NAME,
                com.gerko.fourenote.data.FolderFile.MIME_TYPE,
                UploadContent.of(text.encodeToByteArray()),
                cloud?.id,
            )
        }
    }

    private fun mimeType(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "pdf" -> PDF_MIME_TYPE
        else -> "application/octet-stream"
    }

    // The photo frames in the cloud copy of a note that have been filled.
    private suspend fun filledFrames(cloud: RemoteFile, noteId: String): List<PageImage> {
        val note = NoteFile.decode(readNote(cloud, noteId) ?: return emptyList())
        return InkSerializer.decode(note.inkData).pages.flatMap { page -> page.images.filter { it.requested && it.file.isNotEmpty() } }
    }

    private suspend fun syncNote(note: Note, cloud: RemoteFile?) {
        when {
            note.deleted -> {
                note.remoteId?.let { provider.delete(it) }
                note.remotePdfId?.let { provider.delete(it) }
                store.purge(note.id)
                if (note.remoteId != null) deletedInCloud++
            }

            cloud == null && note.remoteId != null && !note.dirty -> {
                // Deleted on another device, and not edited here since.
                store.purge(note.id)
                deletedHere++
            }

            // New note, or deleted elsewhere but edited here: the edit wins and it uploads again.
            cloud == null -> if (upload(note, remoteId = null)) uploaded++

            // Unchanged in the cloud: upload local edits, add a PDF copy the note is still missing
            // (for instance one skipped by an older version of the app), or encrypt a locked note
            // that went up readable before the 4ENote password was set.
            cloud.version == note.remoteVersion -> if (note.dirty || (pdfExporter != null && note.remotePdfId == null && !note.locked) || needsSealing(note, cloud)) {
                if (upload(note, cloud.id)) uploaded++
            }

            !note.dirty -> {
                val text = readNote(cloud, note.id) ?: return
                // The trash is local: keep it, so a sync never brings a trashed note back.
                val fromCloud = NoteFile.decode(text).copy(trashedAt = note.trashedAt)
                if (store.replaceIfUnchanged(fromCloud, note.updatedAt, dirty = false)) {
                    store.markSynced(note.id, cloud, fromCloud.remotePdfId, text, fromCloud.updatedAt)
                    downloaded++
                }
            }

            else -> {
                val fromCloud = NoteFile.decode(readNote(cloud, note.id) ?: return)
                val note = store.withContent(note)
                val base = note.syncedSnapshot.takeIf { it.isNotEmpty() }?.let(NoteFile::decode)
                val result = NoteMerger.merge(base, note, fromCloud)
                    .copy(remotePdfId = note.remotePdfId ?: fromCloud.remotePdfId, trashedAt = note.trashedAt)
                if (store.replaceIfUnchanged(result, note.updatedAt, dirty = true)) {
                    if (upload(result, cloud.id)) merged++
                }
            }
        }
    }

    // A locked note in the cloud not yet encrypted with this device's key.
    private fun needsSealing(note: Note, cloud: RemoteFile): Boolean {
        if (!note.locked || keys == null) return false
        val key = keys.keyForUpload() ?: return false
        return keys.encryptedVersion(note.id) != "${key.id}:${cloud.version}"
    }

    /** Returns false when the note waits for the notes key (see [Sealing.Skip]). */
    private suspend fun upload(listed: Note, remoteId: String?): Boolean {
        val sealing = sealingFor(listed)
        if (sealing == Sealing.Skip) {
            needKey += listed.id
            unsettled += listed.id
            return false
        }
        // A merged note already carries its ink; one listed for sync doesn't yet.
        val note = if (listed.inkData.isEmpty()) store.withContent(listed) else listed
        // A locked note gets no readable copy in the cloud; one left from before it was locked goes.
        val pdfId = if (note.locked) {
            note.remotePdfId?.let { provider.delete(it) }
            null
        } else {
            pdfExporter?.let { export ->
                export(note)?.let { pdf ->
                    try {
                        provider.upload(pdfName(note), PDF_MIME_TYPE, UploadContent.of(pdf), note.remotePdfId).id
                    } finally {
                        pdf.delete()
                    }
                }
            } ?: note.remotePdfId
        }
        val text = NoteFile.encode(note, pdfId)
        val key = (sealing as? Sealing.With)?.key
        val content = if (key != null) LockedNoteFile.encode(note.id, text, key) else text
        val file = provider.upload(NoteFile.fileName(note.id), NoteFile.MIME_TYPE, UploadContent.of(content.encodeToByteArray()), remoteId)
        if (key != null) keys?.encryptedVersion(note.id, file.version)
        // The snapshot kept here (the base for merging) is the plain note file.
        store.markSynced(note.id, file, pdfId, text, expectedUpdatedAt = note.updatedAt)
        return true
    }

    private suspend fun downloadNew(file: RemoteFile) {
        val noteId = NoteFile.noteIdOf(file.name) ?: return
        val text = readNote(file, noteId) ?: return
        val note = NoteFile.decode(text)
        store.insertFromCloud(
            note.copy(
                remoteId = file.id,
                remoteVersion = file.version,
                syncedSnapshot = text,
                lastSyncedAt = clock(),
                dirty = false,
                // A note that was trashed here and deleted in the cloud comes back untrashed.
                trashedAt = null,
            ),
        )
        downloaded++
    }

    companion object {
        const val PDF_MIME_TYPE = "application/pdf"
        /** Added to the cloud name of a locked note's picture or PDF page once it is encrypted. */
        const val ENCRYPTED_SUFFIX = ".4el"

        /** "Title (1a2b3c4d).pdf": readable, and unique even when titles repeat. */
        fun pdfName(note: Note): String {
            val title = note.title.ifBlank { "Untitled" }.replace(Regex("""[\\/:*?"<>|]"""), "_").take(80)
            return "$title (${note.id.take(8)}).pdf"
        }
    }
}
