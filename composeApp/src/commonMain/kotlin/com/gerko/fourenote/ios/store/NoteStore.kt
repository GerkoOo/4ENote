package com.gerko.fourenote.ios.store

import com.gerko.fourenote.data.ink.InkDocument
import com.gerko.fourenote.data.ink.InkPage
import com.gerko.fourenote.data.ink.InkSerializer
import com.gerko.fourenote.data.newId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** A note as the iPad app stores it: a title, a modification time and its ink. */
data class StoredNote(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val document: InkDocument,
)

/**
 * The notes on this device, one JSON file per note in [files]. The ink is stored as the shared
 * serializer's text inside the JSON, so the file stays readable and the ink format is the same as
 * on Android.
 */
class NoteStore(
    private val files: NoteFiles,
    private val now: () -> Long = ::nowMillis,
) {
    /** Every readable note, newest first. A damaged file is skipped, not fatal. */
    fun all(): List<StoredNote> =
        files.list()
            .filter { it.endsWith(SUFFIX) }
            .mapNotNull { readFile(it) }
            .sortedByDescending { it.updatedAt }

    fun get(id: String): StoredNote? = readFile(fileName(id))

    fun save(note: StoredNote) {
        val json = buildJsonObject {
            put("id", note.id)
            put("title", note.title)
            put("updatedAt", note.updatedAt)
            put("ink", InkSerializer.encode(note.document))
        }
        files.write(fileName(note.id), json.toString())
    }

    fun create(title: String = "Untitled"): StoredNote {
        val note = StoredNote(
            id = newId(),
            title = title,
            updatedAt = now(),
            document = InkDocument(pages = listOf(InkPage())),
        )
        save(note)
        return note
    }

    fun rename(id: String, title: String) {
        val note = get(id) ?: return
        save(note.copy(title = title, updatedAt = now()))
    }

    fun delete(id: String) {
        files.delete(fileName(id))
    }

    private fun readFile(name: String): StoredNote? {
        val text = files.read(name) ?: return null
        return try {
            val o = Json.parseToJsonElement(text).jsonObject
            val id = o.string("id").ifEmpty { name.removeSuffix(SUFFIX) }
            val title = o.string("title").ifEmpty { "Untitled" }
            val updatedAt = (o["updatedAt"] as? JsonPrimitive)?.longOrNull ?: 0L
            val ink = o.string("ink")
            StoredNote(id, title, updatedAt, InkSerializer.decode(ink))
        } catch (e: Exception) {
            null
        }
    }

    private fun fileName(id: String) = "$id$SUFFIX"

    private fun JsonObject.string(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull ?: ""

    private companion object {
        const val SUFFIX = ".json"
    }
}

/** The current time in milliseconds since the epoch. */
expect fun nowMillis(): Long
