package com.gerko.fourenote.data.sync

import com.gerko.fourenote.data.JsonReader
import com.gerko.fourenote.data.SafeName
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * The `.4enote` files a note is stored as in the cloud: the note's metadata plus its ink. Each
 * device writes only its own file of a note, "noteId.deviceId.4enote", so two devices syncing at
 * the same moment can never overwrite each other's work; the others merge it in. Named after the
 * note id, so renaming a note never creates a second copy.
 *
 * Shared by the Android app and the iPad app: the text format is the same on both, so a note
 * written on one opens on the other. The gzip wrapping of the cloud file stays on each platform.
 */
object NoteFileFormat {
    const val EXTENSION = ".4enote"
    const val MIME_TYPE = "application/json"
    private const val FORMAT = "4enote"
    private const val VERSION = 1

    fun fileName(noteId: String, deviceId: String) = "$noteId.$deviceId$EXTENSION"

    /**
     * Stands for the one shared file per note ("noteId.4enote") that versions before per-device
     * files wrote. It is read like any other device's file and never written.
     */
    const val LEGACY_DEVICE = "legacy"

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
    fun noteIdOf(fileName: String): String? = noteFileOf(fileName)?.first

    /** The note id and the device that wrote it ([LEGACY_DEVICE] for the old shared file), or null if it is not a note file. */
    fun noteFileOf(fileName: String): Pair<String, String>? {
        val stem = fileName.takeIf { it.endsWith(EXTENSION) }?.removeSuffix(EXTENSION) ?: return null
        val dot = stem.lastIndexOf('.')
        val device = stem.substring(dot + 1)
        val (noteId, deviceId) = if (dot > 0 && DeviceIds.isValid(device)) stem.substring(0, dot) to device else stem to LEGACY_DEVICE
        return (noteId to deviceId).takeIf { SafeName.isSafeFromCloud(noteId) }
    }

    /** [pdfId] is the note's PDF copy, recorded so every device updates the same PDF. */
    fun encode(content: NoteContent, pdfId: String?): String = StringBuilder().also { encodeTo(content, pdfId, it) }.toString()

    /**
     * Writes the note file into [out] as it goes. Written by hand: the ink alone can be tens of
     * millions of characters, and a JSONObject would hold it twice more (escaped, then as text).
     */
    fun encodeTo(content: NoteContent, pdfId: String?, out: Appendable, seen: Map<String, String> = emptyMap()) {
        var first = true
        fun key(name: String) {
            if (!first) out.append(',')
            first = false
            JsonReader.quote(name, out)
            out.append(':')
        }
        out.append('{')
        key("format"); JsonReader.quote(FORMAT, out)
        key("v"); out.append(VERSION.toString())
        key("id"); JsonReader.quote(content.id, out)
        key("title"); JsonReader.quote(content.title, out)
        key("createdAt"); out.append(content.createdAt.toString())
        key("updatedAt"); out.append(content.updatedAt.toString())
        // Stored as a string so the ink format can evolve independently.
        key("ink"); JsonReader.quote(content.inkData, out)
        key("pdf"); if (pdfId == null) out.append("null") else JsonReader.quote(pdfId, out)
        key("folder"); if (content.folderId == null) out.append("null") else JsonReader.quote(content.folderId, out)
        // Left out when false, so files of unlocked notes stay as they were.
        if (content.locked) { key("locked"); out.append("true") }
        key("rev"); out.append(content.rev.toString())
        // The other devices' files (device id to version) merged into this one: lets a device
        // reading it for the first time find the state both started from (see SyncEngine).
        if (seen.isNotEmpty()) {
            key("seen")
            out.append('{')
            seen.entries.sortedBy { it.key }.forEachIndexed { i, (device, version) ->
                if (i > 0) out.append(',')
                JsonReader.quote(device, out)
                out.append(':')
                JsonReader.quote(version, out)
            }
            out.append('}')
        }
        out.append('}')
    }

    /**
     * Content fields and [NoteContent.remotePdfId]; other sync bookkeeping is left for the caller.
     * The file's record of other devices' files merged into it goes into [seen], when given.
     */
    fun decode(text: String, seen: MutableMap<String, String>? = null): NoteContent {
        val reader = JsonReader(text)
        val fields = HashMap<String, JsonPrimitive?>()
        var ink = ""
        reader.obj { key ->
            when {
                key == "ink" && reader.peek() == '"' -> ink = reader.string()
                key == "seen" -> (reader.element() as? JsonObject)?.forEach { (device, version) ->
                    val v = (version as? JsonPrimitive)?.takeIf { it.isString }?.content
                    if (v != null && (DeviceIds.isValid(device) || device == LEGACY_DEVICE)) seen?.put(device, v)
                }
                else -> fields[key] = reader.element() as? JsonPrimitive
            }
        }
        fun string(key: String) = fields[key]?.takeUnless { it is JsonNull }?.content
        fun long(key: String) = fields[key]?.longOrNull ?: fields[key]?.doubleOrNull?.toLong() ?: 0L
        require(string("format") == FORMAT) { "Not a 4ENote file" }
        return NoteContent(
            id = string("id").orEmpty().also { require(SafeName.isSafeFromCloud(it)) { "Unsafe note id" } },
            title = string("title").orEmpty(),
            inkData = ink,
            createdAt = long("createdAt"),
            updatedAt = long("updatedAt"),
            remotePdfId = string("pdf")?.takeIf { it.isNotEmpty() },
            folderId = string("folder")?.takeIf { it.isNotEmpty() },
            locked = fields["locked"]?.booleanOrNull ?: false,
            rev = fields["rev"]?.longOrNull?.coerceAtLeast(0) ?: 0,
        )
    }
}
