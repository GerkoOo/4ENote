package com.gerko.fourenote.data.sync

import com.gerko.fourenote.data.Note

/**
 * The `.4enote` files a note is stored as in the cloud. The text format itself lives in the
 * shared module ([NoteFileFormat]), so the iPad app reads and writes the same files; this is the
 * Android side of it: the note type, and the gzip wrapping of the cloud file.
 */
object NoteFile {
    const val EXTENSION = NoteFileFormat.EXTENSION
    const val MIME_TYPE = NoteFileFormat.MIME_TYPE

    fun fileName(noteId: String, deviceId: String) = NoteFileFormat.fileName(noteId, deviceId)

    /**
     * Stands for the one shared file per note ("noteId.4enote") that versions before per-device
     * files wrote. It is read like any other device's file and never written.
     */
    const val LEGACY_DEVICE = NoteFileFormat.LEGACY_DEVICE

    /** A note's picture or PDF page in the cloud: "noteId~fileName". */
    fun attachmentName(noteId: String, file: String) = NoteFileFormat.attachmentName(noteId, file)

    /** The note id and file name of a cloud attachment, or null if [fileName] is not one. */
    fun attachmentOf(fileName: String): Pair<String, String>? = NoteFileFormat.attachmentOf(fileName)

    /** The note id for a cloud file name, or null if it is not a note file. */
    fun noteIdOf(fileName: String): String? = NoteFileFormat.noteIdOf(fileName)

    /** The note id and the device that wrote it ([LEGACY_DEVICE] for the old shared file), or null if it is not a note file. */
    fun noteFileOf(fileName: String): Pair<String, String>? = NoteFileFormat.noteFileOf(fileName)

    /** [pdfId] is the note's PDF copy, recorded so every device updates the same PDF. */
    fun encode(note: Note, pdfId: String?): String = NoteFileFormat.encode(note.toContent(), pdfId)

    /**
     * Writes the note file into [out] as it goes. Written by hand: the ink alone can be tens of
     * millions of characters, and a JSONObject would hold it twice more (escaped, then as text).
     */
    fun encodeTo(note: Note, pdfId: String?, out: Appendable, seen: Map<String, String> = emptyMap()) =
        NoteFileFormat.encodeTo(note.toContent(), pdfId, out, seen)

    /**
     * Content fields and [Note.remotePdfId]; other sync bookkeeping is left for the caller. The
     * file's record of other devices' files merged into it goes into [seen], when given.
     */
    fun decode(text: String, seen: MutableMap<String, String>? = null): Note {
        val content = NoteFileFormat.decode(text, seen)
        return Note(
            id = content.id,
            title = content.title,
            inkData = content.inkData,
            createdAt = content.createdAt,
            updatedAt = content.updatedAt,
            remotePdfId = content.remotePdfId,
            folderId = content.folderId,
            locked = content.locked,
            rev = content.rev,
        )
    }

    /** MIME type of a note file in the cloud, which is gzip-compressed (see [toCloud]). */
    const val CLOUD_MIME_TYPE = "application/gzip"

    /**
     * The note file as stored in the cloud: gzip-compressed, often 5 times smaller. Written in
     * pieces, so the text is never copied whole into bytes.
     */
    fun toCloud(text: String): ByteArray = compressed { it.write(text) }

    /** The note file, written straight into its compressed cloud form. */
    fun toCloud(note: Note, pdfId: String?, seen: Map<String, String> = emptyMap()): ByteArray =
        compressed { NoteFileFormat.encodeTo(note.toContent(), pdfId, it, seen) }

    private fun compressed(write: (java.io.Writer) -> Unit): ByteArray {
        val bytes = java.io.ByteArrayOutputStream(64 * 1024)
        java.io.BufferedWriter(java.io.OutputStreamWriter(java.util.zip.GZIPOutputStream(bytes, 64 * 1024), Charsets.UTF_8), 64 * 1024)
            .use(write)
        return bytes.toByteArray()
    }

    /** The text of a cloud note file, compressed (this version) or not (older versions). */
    fun fromCloud(bytes: ByteArray): String =
        if (isGzip(bytes)) java.util.zip.GZIPInputStream(bytes.inputStream(), 64 * 1024).readLimited().decodeToString()
        else bytes.decodeToString()

    fun isGzip(bytes: ByteArray) = bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
}
