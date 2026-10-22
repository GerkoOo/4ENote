package com.gerko.fourenote.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A folder on the home page. Folders can hold notes ([Note.folderId]) and other folders
 * ([parentId]; null is the top level). A deleted folder stays as a tombstone ([deleted]) so the
 * deletion reaches the other devices; [updatedAt] decides which device's change wins.
 */
@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val parentId: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE deleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Folder>>

    @Query("SELECT * FROM folders")
    suspend fun getAll(): List<Folder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: Folder)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(folders: List<Folder>)
}

/** All the folders as one small file in the cloud, merged folder by folder (newer change wins). */
object FolderFile {
    const val NAME = "folders.4enote-folders"
    const val MIME_TYPE = "application/json"

    fun encode(folders: List<Folder>): String = JSONArray().apply {
        folders.sortedBy { it.id }.forEach { f ->
            put(
                JSONObject().put("id", f.id).put("name", f.name).put("parent", f.parentId ?: JSONObject.NULL)
                    .put("updatedAt", f.updatedAt).put("deleted", f.deleted),
            )
        }
    }.toString()

    fun decode(text: String): List<Folder> = runCatching {
        val array = JSONArray(text)
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            Folder(
                id = o.getString("id"),
                name = o.optString("name"),
                parentId = if (o.isNull("parent")) null else o.optString("parent").ifEmpty { null },
                updatedAt = o.optLong("updatedAt"),
                deleted = o.optBoolean("deleted"),
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
