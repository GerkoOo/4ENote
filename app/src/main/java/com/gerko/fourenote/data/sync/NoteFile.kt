package com.gerko.fourenote.data.sync

import com.gerko.fourenote.data.SafeName
import com.gerko.fourenote.data.Note
import org.json.JSONObject

/**
 * The `.4enote` file each note is stored as in the cloud: the note's metadata
 * plus its ink. Named after the note id so renaming a note never creates a
 * second copy.
 */
object NoteFile {
    const val EXTENSION = ".4enote"
    const val MIME_TYPE = "application/json"
    private const val FORMAT = "4enote"
    private const val VERSION = 1

    fun fileName(noteId: String) = "$noteId$EXTENSION"

    /** A note's picture or PDF page in the cloud: "noteId~fileName". */
    fun attachmentName(noteId: String, file: String) = "$noteId$ATTACHMENT_SEPARATOR$file"

    /** The note id and file name of a cloud attachment, or null if [fileName] is not one. */
    fun attachmentOf(fileName: String): Pair<String, String>? {
        val at = fileName.indexOf(ATTACHMENT_SEPARATOR)
        if (at <= 0 || at == fileName.lastIndex) return null
        val noteId = fileName.substring(0, at)
        val file = fileName.substring(at + 1)
        // Cloud names are untrusted; one that could leave the note's folder is ignored.
        if (!SafeName.isSafeFromCloud(noteId) || !SafeName.isSafeFromCloud(file)) return null
        return noteId to file
    }

    private const val ATTACHMENT_SEPARATOR = '~'

    /** The note id for a cloud file name, or null if it is not a note file. */
    fun noteIdOf(fileName: String): String? =
        fileName.takeIf { it.endsWith(EXTENSION) }?.removeSuffix(EXTENSION)?.takeIf(SafeName::isSafeFromCloud)

    /** [pdfId] is the note's PDF copy, recorded so every device updates the same PDF. */
    fun encode(note: Note, pdfId: String?): String =
        JSONObject()
            .put("format", FORMAT)
            .put("v", VERSION)
            .put("id", note.id)
            .put("title", note.title)
            .put("createdAt", note.createdAt)
            .put("updatedAt", note.updatedAt)
            // Stored as a string so the ink format can evolve independently.
            .put("ink", note.inkData)
            .put("pdf", pdfId ?: JSONObject.NULL)
            .put("folder", note.folderId ?: JSONObject.NULL)
            .toString()

    /** Content fields and [Note.remotePdfId]; other sync bookkeeping is left for the caller. */
    fun decode(text: String): Note {
        val json = JSONObject(text)
        require(json.optString("format") == FORMAT) { "Not a 4ENote file" }
        return Note(
            id = json.getString("id").also { require(SafeName.isSafeFromCloud(it)) { "Unsafe note id" } },
            title = json.optString("title"),
            inkData = json.optString("ink"),
            createdAt = json.optLong("createdAt"),
            updatedAt = json.optLong("updatedAt"),
            remotePdfId = json.optString("pdf").takeUnless { json.isNull("pdf") || it.isEmpty() },
            folderId = json.optString("folder").takeUnless { json.isNull("folder") || it.isEmpty() },
        )
    }
}
