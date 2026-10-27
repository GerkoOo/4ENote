package com.gerko.fourenote.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.gerko.fourenote.platform.nowMillis
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * A folder on the home page. Folders can hold notes ([Note.folderId]) and other folders
 * ([parentId]; null is the top level). A deleted folder stays as a tombstone ([deleted]) so the
 * deletion reaches the other devices; [updatedAt] decides which device's change wins.
 */
@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val parentId: String? = null,
    val updatedAt: Long = nowMillis(),
    val deleted: Boolean = false,
)

/** All the folders as one small file in the cloud, merged folder by folder (newer change wins). */
object FolderFile {
    const val NAME = "folders.4enote-folders"
    const val MIME_TYPE = "application/json"

    fun encode(folders: List<Folder>): String = buildJsonArray {
        folders.sortedBy { it.id }.forEach { f ->
            addJsonObject {
                put("id", f.id)
                put("name", f.name)
                put("parent", f.parentId)
                put("updatedAt", f.updatedAt)
                put("deleted", f.deleted)
            }
        }
    }.toString()

    fun decode(text: String): List<Folder> = runCatching {
        Json.parseToJsonElement(text).jsonArray.map { element ->
            val o = element.jsonObject
            fun primitive(name: String) = o[name] as? JsonPrimitive
            Folder(
                id = primitive("id")!!.content,
                name = primitive("name")?.takeIf { it !is JsonNull }?.content.orEmpty(),
                parentId = primitive("parent")?.takeIf { it !is JsonNull }?.content?.ifEmpty { null },
                updatedAt = primitive("updatedAt")?.longOrNull ?: 0L,
                deleted = primitive("deleted")?.booleanOrNull ?: false,
            )
        }
    }.getOrDefault(emptyList())

    /** Every folder from both sides; for one on both, the more recently changed version. */
    fun merge(local: List<Folder>, remote: List<Folder>): List<Folder> {
        val byId = LinkedHashMap<String, Folder>()
        for (f in local + remote) {
            val known = byId[f.id]
            if (known == null || f.updatedAt > known.updatedAt) byId[f.id] = f
        }
        return byId.values.sortedBy { it.id }
    }
}
