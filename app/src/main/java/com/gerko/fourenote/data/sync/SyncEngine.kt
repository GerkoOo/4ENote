package com.gerko.fourenote.data.sync

import com.gerko.fourenote.data.ink.InkSerializer
import com.gerko.fourenote.data.ink.PageImage
import com.gerko.fourenote.data.Note
import com.gerko.fourenote.data.isInCloud
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

    /** [note] with its ink filled in, for uploading or merging. */
    suspend fun withContent(note: Note): Note = note

    /**
     * The note as this device last uploaded it (its own cloud file); empty if there is none. Read
     * only when needed: for a long note it is as big as the note.
     */
    suspend fun syncedSnapshot(note: Note): String = note.syncedSnapshot

    /** Other devices' files of the note as last merged here: device id to cloud version. */
    suspend fun peerVersions(noteId: String): Map<String, String> = emptyMap()

    /** Another device's file of the note as last merged here, the base for merging its next version; null if none. */
    suspend fun peerSnapshot(noteId: String, deviceId: String): String? = null

    /** Records another device's file (gzip-compressed, as it came) as merged here at [version]. */
    suspend fun savePeer(noteId: String, deviceId: String, version: String, compressed: ByteArray) {}

    /** The other device's file is gone from the cloud. */
    suspend fun forgetPeer(noteId: String, deviceId: String) {}

    /** The note's pictures and PDF pages on this device: file name to file. They never change once written. */
    fun attachments(noteId: String): Map<String, File> = emptyMap()

    /** An attachment's plain content (a locked note's pictures are encrypted on the device). */
    fun attachmentBytes(noteId: String, file: File): ByteArray = file.readBytes()

    /** An attachment ready to upload as it is; big files (PDFs) are streamed from the file. */
    fun attachmentUpload(noteId: String, file: File): UploadContent = UploadContent.of(file)

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

    /** A note new to this device, put together from the other devices' files (recorded with [savePeer]). */
    suspend fun insertFromCloud(note: Note)

    /** Replaces content unless the user edited the note after [expectedUpdatedAt]. */
    suspend fun replaceIfUnchanged(note: Note, expectedUpdatedAt: Long, dirty: Boolean): Boolean

    suspend fun markSynced(
        noteId: String,
        file: RemoteFile,
        pdfId: String?,
        /** This device's file as uploaded, gzip-compressed. */
        snapshot: ByteArray,
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
 * Two-way sync between the local notes and one cloud folder: an edit on any device reaches all of
 * them. Every device writes only its own file of a note (see [NoteFile]); cloud services replace a
 * file whole, so two devices sharing one file would overwrite each other when they sync at the
 * same moment. For each note:
 *  - every other device's file whose version moved since it was last merged here is merged in
 *    ([NoteMerger], with that file as last merged here as the base);
 *  - this device's file is uploaded when the note was edited here, or a merge changed it from
 *    what was last uploaded (so each device's file keeps up with the note).
 *
 * Deletions travel as tombstones locally and as missing files in the cloud: deleting a note
 * removes every device's file of it.
 * Each note also gets a PDF copy when [pdfExporter] is set, so notes can be
 * opened from any computer.
 */
class SyncEngine(
    private val store: SyncStore,
    private val provider: CloudProvider,
    /** Writes the note's PDF copy to a temporary file (deleted after upload), or returns null to skip it. */
    private val pdfExporter: ((Note) -> File?)? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    /** This device's id (see [DeviceIdentity]): it writes only the cloud files named after it. */
    private val deviceId: String,
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
        val cloudNotes = HashMap<String, CloudFiles>()
        for (file in listed) {
            val (noteId, device) = NoteFile.noteFileOf(file.name) ?: continue
            val files = cloudNotes.getOrPut(noteId) { CloudFiles() }
            if (device == deviceId) files.own = file else files.peers[device] = file
        }
        val localNotes = store.notesForSync()

        // Filled photo frames for open notes, handed over once their photos have downloaded.
        val fills = mutableListOf<Pair<String, List<PageImage>>>()
        for (note in localNotes) {
            if (store.isOpen(note.id)) {
                val files = cloudNotes[note.id]
                val changed = runCatching { files?.let { changedPeers(note, it) }.orEmpty() }.getOrDefault(emptyMap())
                // An open note can still be uploaded (that only reads it), so a frame set on the tablet
                // reaches the phone at once. Anything that would change it here waits until it is closed,
                // except photos for its frames, which the editor takes in itself.
                val uploadOnly = !note.deleted && note.dirty && changed.isEmpty() && !(files == null && wasSynced(note))
                if (uploadOnly) {
                    runCatching { syncNote(note, files) }
                        .onFailure { errors += "${note.title.ifBlank { "Untitled" }}: ${it.message}" }
                    unsettled += note.id
                    continue
                }
                unsettled += note.id
                skippedOpen++
                val filled = changed.values.flatMap { file -> runCatching { filledFrames(file, note.id) }.getOrDefault(emptyList()) }
                if (filled.isNotEmpty()) fills += note.id to filled
                continue
            }
            runCatching { syncNote(note, cloudNotes[note.id]) }
                .onFailure {
                    unsettled += note.id
                    errors += "${note.title.ifBlank { "Untitled" }}: ${it.message}"
                }
        }

        val localIds = localNotes.mapTo(HashSet()) { it.id }
        for ((id, files) in cloudNotes) {
            if (id in localIds) continue
            runCatching { downloadNew(id, files) }
                .onFailure { errors += "${NoteFile.fileName(id, deviceId)}: ${it.message}" }
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
     * A note from the cloud, its file compressed (kept as the base for the next merge), and the
     * other devices' files that went into it (device id to version).
     */
    private class CloudNote(val note: Note, val snapshot: ByteArray, val seen: Map<String, String>)

    /** One note's files in the cloud: this device's own, and the other devices' by device id. */
    private class CloudFiles(var own: RemoteFile? = null, val peers: MutableMap<String, RemoteFile> = HashMap())

    // Synced before: a note whose files have all gone from the cloud was deleted on another device.
    private fun wasSynced(note: Note) = note.isInCloud

    /** The other devices' files of [note] not merged here at their current version. */
    private suspend fun changedPeers(note: Note, files: CloudFiles): Map<String, RemoteFile> {
        if (files.peers.isEmpty()) return emptyMap()
        val seen = store.peerVersions(note.id).toMutableMap()
        // A note last synced with the one shared file of older versions: that file, as it was then,
        // is the base for what other devices write into it later.
        val legacy = files.peers[NoteFile.LEGACY_DEVICE]
        if (legacy != null && NoteFile.LEGACY_DEVICE !in seen && note.remoteId == legacy.id && note.remoteVersion != null) {
            val snapshot = store.syncedSnapshot(note)
            if (snapshot.isNotEmpty()) {
                store.savePeer(note.id, NoteFile.LEGACY_DEVICE, note.remoteVersion, NoteFile.toCloud(snapshot))
                seen[NoteFile.LEGACY_DEVICE] = note.remoteVersion
            }
        }
        for (device in seen.keys - files.peers.keys) store.forgetPeer(note.id, device)
        return files.peers.filter { (device, file) -> seen[device] != file.version }
    }

    /**
     * The note in [cloud], or null when it is encrypted with a key this device lacks (the note is
     * then left alone and counted in [needKey]). The note's text lives only in here: a long note's
     * text is as big as its ink, and keeping both after decoding could run out of memory.
     */
    private suspend fun readNote(cloud: RemoteFile, noteId: String): CloudNote? {
        var compressed = provider.download(cloud.id)
        var text = NoteFile.fromCloud(compressed)
        if (LockedNoteFile.isLocked(text)) {
            val key = keys?.key()?.takeIf { it.id == LockedNoteFile.keyId(text) }
            if (key == null) {
                needKey += noteId
                unsettled += noteId
                return null
            }
            compressed = LockedNoteFile.decrypt(text, key)
            text = NoteFile.fromCloud(compressed)
        }
        val seen = HashMap<String, String>()
        val note = NoteFile.decode(text, seen)
        // The id inside is the one the file was written (or encrypted) for; a file copied over another note's name is refused.
        require(note.id == noteId) { "Note file under the wrong name" }
        // Files from older versions aren't compressed yet.
        return CloudNote(note, if (NoteFile.isGzip(compressed)) compressed else NoteFile.toCloud(text), seen)
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
                    val sealed = key.encrypt(store.attachmentBytes(note.id, file), cloudName)
                    provider.upload(cloudName, "application/octet-stream", UploadContent.of(sealed), null)
                } else {
                    provider.upload(NoteFile.attachmentName(note.id, name), mimeType(name), store.attachmentUpload(note.id, file), null)
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
        val note = readNote(cloud, noteId)?.note ?: return emptyList()
        return InkSerializer.decode(note.inkData).pages.flatMap { page -> page.images.filter { it.requested && it.file.isNotEmpty() } }
    }

    private suspend fun syncNote(note: Note, files: CloudFiles?) {
        when {
            note.deleted -> {
                val ids = files?.let { listOfNotNull(it.own) + it.peers.values }.orEmpty().map { it.id }
                for (id in (ids + listOfNotNull(note.remoteId)).toSet()) provider.delete(id)
                note.remotePdfId?.let { provider.delete(it) }
                store.purge(note.id)
                if (ids.isNotEmpty()) deletedInCloud++
            }

            files == null && wasSynced(note) && !note.dirty -> {
                // Deleted on another device, and not edited here since.
                store.purge(note.id)
                deletedHere++
            }

            // New note, or deleted elsewhere but edited here: the edit wins and it uploads again.
            files == null -> if (upload(note, remoteId = null)) uploaded++

            else -> settle(note, files)
        }
    }

    /** Merges in what other devices changed, then uploads this device's file if it is behind. */
    private suspend fun settle(note: Note, files: CloudFiles) {
        val own = files.own
        val changed = changedPeers(note, files)
        // Up to date with every device: upload only an edit made here, or catch up on a PDF copy the
        // note is still missing (for instance one skipped by an older version of the app), or
        // encrypt a locked note that went up readable before the 4ENote password was set.
        if (changed.isEmpty()) {
            val catchUp = own != null && ((pdfExporter != null && note.remotePdfId == null && !note.locked) || needsSealing(note, own))
            if ((note.dirty || catchUp) && upload(note, own?.id)) uploaded++
            return
        }

        var result = store.withContent(note)
        val merges = mutableListOf<Triple<String, String, ByteArray>>()
        for ((device, file) in changed) {
            val read = readNote(file, note.id) ?: return
            val base = commonBase(note, device, read.seen)
            result = NoteMerger.merge(base, result, read.note, remoteFirst = device < deviceId)
                .copy(remotePdfId = result.remotePdfId ?: read.note.remotePdfId, trashedAt = note.trashedAt)
            merges += Triple(device, file.version, read.snapshot)
        }
        // The note is stored before the other devices' files are recorded as merged: if anything
        // stops in between, the same files are merged again next time, which changes nothing.
        if (!store.replaceIfUnchanged(result, note.updatedAt, dirty = note.dirty)) return
        for ((device, version, snapshot) in merges) store.savePeer(note.id, device, version, snapshot)
        if (note.dirty) merged++ else downloaded++

        // This device's file keeps up with the note, so a device joining later reads no stale
        // copy. A device that never wrote the note has no file of it and stays a reader.
        val behind = when {
            note.dirty -> true
            own == null -> false
            own.version != note.remoteVersion -> true
            else -> !sameContent(store.syncedSnapshot(note), result)
        }
        if (behind) upload(result, own?.id)
    }

    /**
     * The base for merging [device]'s file of the note: everything both sides had already merged,
     * found from the file's record of what went into it ([seen]): that device's previous file,
     * this device's own last upload if it was seen at that version, and other devices' files it saw
     * at the version stored here. Something in there that one side no longer has was removed on
     * that side. Where they differ, this device's upload counts first for single values (a title,
     * a picture's place), then that device's previous file. Without any, the merge keeps all.
     */
    private suspend fun commonBase(note: Note, device: String, seen: Map<String, String>): Note? {
        var base: Note? = null
        fun add(text: String?) {
            if (text.isNullOrEmpty()) return
            val next = NoteFile.decode(text)
            base = base?.let { NoteMerger.union(it, next) } ?: next
        }
        if (note.remoteVersion != null && seen[deviceId] == note.remoteVersion) add(store.syncedSnapshot(note))
        add(store.peerSnapshot(note.id, device))
        val versions = store.peerVersions(note.id)
        for ((other, version) in seen) {
            if (other != deviceId && other != device && versions[other] == version) add(store.peerSnapshot(note.id, other))
        }
        return base
    }

    // Whether the note last uploaded from here ([uploaded], plain file text) already holds [note].
    // Merges order things the same way on every device, so once a merge brings nothing new the two
    // match and devices stop sending each other the same note back and forth.
    private fun sameContent(uploaded: String, note: Note): Boolean {
        if (uploaded.isEmpty()) return false
        val last = NoteFile.decode(uploaded)
        return last.title == note.title && last.locked == note.locked && last.folderId == note.folderId &&
            InkSerializer.encode(InkSerializer.decode(last.inkData)) == InkSerializer.encode(InkSerializer.decode(note.inkData))
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
        val key = (sealing as? Sealing.With)?.key
        // Written straight into the compressed file: the whole text never exists at once.
        val compressed = NoteFile.toCloud(note, pdfId, seen = store.peerVersions(note.id))
        val file = if (key != null) {
            val sealed = LockedNoteFile.encode(note.id, compressed, key)
            provider.upload(NoteFile.fileName(note.id, deviceId), NoteFile.MIME_TYPE, UploadContent.of(sealed.encodeToByteArray()), remoteId)
        } else {
            provider.upload(NoteFile.fileName(note.id, deviceId), NoteFile.CLOUD_MIME_TYPE, UploadContent.of(compressed), remoteId)
        }
        if (key != null) keys?.encryptedVersion(note.id, file.version)
        // The snapshot kept here (the base for merging) is the plain note file, compressed.
        store.markSynced(note.id, file, pdfId, compressed, expectedUpdatedAt = note.updatedAt)
        return true
    }

    /** A note this device doesn't have yet: put together from every device's file of it. */
    private suspend fun downloadNew(noteId: String, files: CloudFiles) {
        var result: Note? = null
        val reads = mutableListOf<Triple<String, String, ByteArray>>()
        val done = mutableListOf<Triple<String, String, CloudNote>>()
        // In device id order, the order every device's merges agree on.
        val all = (files.peers + listOfNotNull(files.own?.let { deviceId to it })).toSortedMap()
        for ((device, file) in all) {
            val read = readNote(file, noteId) ?: return
            // As in commonBase: files already put in that this one saw, and this one if one of them saw it.
            var base: Note? = null
            for ((other, version, earlier) in done) {
                if (read.seen[other] == version) base = base?.let { NoteMerger.union(it, earlier.note) } ?: earlier.note
                if (earlier.seen[device] == file.version) base = base?.let { NoteMerger.union(it, read.note) } ?: read.note
            }
            result = result?.let {
                NoteMerger.merge(base, it, read.note).copy(remotePdfId = it.remotePdfId ?: read.note.remotePdfId)
            } ?: read.note
            done += Triple(device, file.version, read)
            if (device != deviceId) reads += Triple(device, file.version, read.snapshot)
        }
        store.insertFromCloud(
            result!!.copy(
                // This device's own file, if one was left from before (it is behind now and goes up next time).
                remoteId = files.own?.id,
                remoteVersion = null,
                lastSyncedAt = clock(),
                dirty = false,
                // A note that was trashed here and deleted in the cloud comes back untrashed.
                trashedAt = null,
            ),
        )
        for ((device, version, snapshot) in reads) store.savePeer(noteId, device, version, snapshot)
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
