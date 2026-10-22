package com.gerko.fourenote.data.sync

import com.gerko.fourenote.data.ink.InkSerializer
import com.gerko.fourenote.data.ink.PageImage
import com.gerko.fourenote.data.Note
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
) {
    private var uploaded = 0
    private var downloaded = 0
    private var merged = 0
    private var deletedHere = 0
    private var deletedInCloud = 0

    suspend fun sync(): SyncResult {
        uploaded = 0
        downloaded = 0
        merged = 0
        deletedHere = 0
        deletedInCloud = 0
        var skippedOpen = 0
        val errors = mutableListOf<String>()

        val cloudNotes = provider.listFiles()
            .mapNotNull { file -> NoteFile.noteIdOf(file.name)?.let { it to file } }
            .toMap()
        val localNotes = store.notesForSync()

        // Filled photo frames for open notes, handed over once their photos have downloaded.
        val fills = mutableListOf<Pair<String, List<PageImage>>>()
        for (note in localNotes) {
            if (store.isOpen(note.id)) {
                skippedOpen++
                val cloud = cloudNotes[note.id]
                if (cloud != null && cloud.version != note.remoteVersion) {
                    runCatching { filledFrames(cloud) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { fills += note.id to it }
                }
                continue
            }
            runCatching { syncNote(note, cloudNotes[note.id]) }
                .onFailure { errors += "${note.title.ifBlank { "Untitled" }}: ${it.message}" }
        }

        val localIds = localNotes.mapTo(HashSet()) { it.id }
        for ((id, file) in cloudNotes) {
            if (id in localIds) continue
            runCatching { downloadNew(file) }
                .onFailure { errors += "${file.name}: ${it.message}" }
        }

        runCatching { syncAttachments() }.onFailure { errors += "Pictures: ${it.message}" }
        for ((noteId, frames) in fills) store.deliverFilledFrames(noteId, frames)

        return SyncResult(uploaded, downloaded, merged, deletedHere, deletedInCloud, skippedOpen, errors)
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
            val local = store.attachments(note.id)
            val remote = cloudByNote[note.id].orEmpty().toMap()
            for ((name, file) in local) {
                if (name !in remote) {
                    provider.upload(NoteFile.attachmentName(note.id, name), mimeType(name), UploadContent.of(file), null)
                }
            }
            for ((name, file) in remote) {
                if (name !in local) store.saveAttachment(note.id, name, provider.download(file.id))
            }
        }
        for ((key, file) in inCloud) {
            val noteId = key.first
            if (noteId !in localIds && noteId !in cloudNoteIds) provider.delete(file.id)
        }
    }

    private fun mimeType(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "pdf" -> PDF_MIME_TYPE
        else -> "application/octet-stream"
    }

    // The photo frames in the cloud copy of a note that have been filled.
    private suspend fun filledFrames(cloud: RemoteFile): List<PageImage> {
        val note = NoteFile.decode(provider.download(cloud.id).decodeToString())
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
            cloud == null -> {
                upload(note, remoteId = null)
                uploaded++
            }

            // Unchanged in the cloud: upload local edits, or add a PDF copy the note is still missing
            // (for instance one skipped by an older version of the app).
            cloud.version == note.remoteVersion -> if (note.dirty || (pdfExporter != null && note.remotePdfId == null)) {
                upload(note, cloud.id)
                uploaded++
            }

            !note.dirty -> {
                val text = provider.download(cloud.id).decodeToString()
                val fromCloud = NoteFile.decode(text)
                if (store.replaceIfUnchanged(fromCloud, note.updatedAt, dirty = false)) {
                    store.markSynced(note.id, cloud, fromCloud.remotePdfId, text, fromCloud.updatedAt)
                    downloaded++
                }
            }

            else -> {
                val note = store.withContent(note)
                val fromCloud = NoteFile.decode(provider.download(cloud.id).decodeToString())
                val base = note.syncedSnapshot.takeIf { it.isNotEmpty() }?.let(NoteFile::decode)
                val result = NoteMerger.merge(base, note, fromCloud)
                    .copy(remotePdfId = note.remotePdfId ?: fromCloud.remotePdfId)
                if (store.replaceIfUnchanged(result, note.updatedAt, dirty = true)) {
                    upload(result, cloud.id)
                    merged++
                }
            }
        }
    }

    private suspend fun upload(listed: Note, remoteId: String?) {
        // A merged note already carries its ink; one listed for sync doesn't yet.
        val note = if (listed.inkData.isEmpty()) store.withContent(listed) else listed
        val pdfId = pdfExporter?.let { export ->
            export(note)?.let { pdf ->
                try {
                    provider.upload(pdfName(note), PDF_MIME_TYPE, UploadContent.of(pdf), note.remotePdfId).id
                } finally {
                    pdf.delete()
                }
            }
        } ?: note.remotePdfId
        val text = NoteFile.encode(note, pdfId)
        val file = provider.upload(NoteFile.fileName(note.id), NoteFile.MIME_TYPE, UploadContent.of(text.encodeToByteArray()), remoteId)
        store.markSynced(note.id, file, pdfId, text, expectedUpdatedAt = note.updatedAt)
    }

    private suspend fun downloadNew(file: RemoteFile) {
        val text = provider.download(file.id).decodeToString()
        val note = NoteFile.decode(text)
        store.insertFromCloud(
            note.copy(
                remoteId = file.id,
                remoteVersion = file.version,
                syncedSnapshot = text,
                lastSyncedAt = clock(),
                dirty = false,
            ),
        )
        downloaded++
    }

    companion object {
        const val PDF_MIME_TYPE = "application/pdf"

        /** "Title (1a2b3c4d).pdf": readable, and unique even when titles repeat. */
        fun pdfName(note: Note): String {
            val title = note.title.ifBlank { "Untitled" }.replace(Regex("""[\\/:*?"<>|]"""), "_").take(80)
            return "$title (${note.id.take(8)}).pdf"
        }
    }
}
