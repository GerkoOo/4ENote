package com.gerko.fourenote.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * The note's ink lives in files (see InkStore). The old `inkData` and `syncedSnapshot` columns
 * are only read in pieces, to move notes from older versions over, because Android can't read
 * a row bigger than about 2 MB at once. Every other query leaves them out.
 */
private const val META =
    "id, title, '' AS inkData, createdAt, updatedAt, driveFileId, lastSyncedAt, deleted, dirty, " +
        "remoteVersion, remotePdfId, '' AS syncedSnapshot"

@Dao
interface NoteDao {
    @Query("SELECT $META FROM notes WHERE deleted = 0 ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Note>>

    @Query("SELECT $META FROM notes WHERE id = :id AND deleted = 0")
    suspend fun getById(id: String): Note?

    /** Every note including tombstones, for sync. */
    @Query("SELECT $META FROM notes")
    suspend fun getAllForSync(): List<Note>

    /** Saves an edit made in the editor: the ink itself is already in its files. */
    @Query("UPDATE notes SET title = :title, updatedAt = :now, dirty = 1 WHERE id = :id")
    suspend fun markEdited(id: String, title: String, now: Long): Int

    /** Notes from older versions that still keep ink or a sync snapshot in the database. */
    @Query("SELECT id FROM notes WHERE length(inkData) > 0 OR length(syncedSnapshot) > 0")
    suspend fun idsWithLegacyInk(): List<String>

    @Query("SELECT length(inkData) FROM notes WHERE id = :id")
    suspend fun legacyInkLength(id: String): Int?

    @Query("SELECT substr(inkData, :start, :count) FROM notes WHERE id = :id")
    suspend fun legacyInkPart(id: String, start: Int, count: Int): String?

    @Query("SELECT length(syncedSnapshot) FROM notes WHERE id = :id")
    suspend fun legacySnapshotLength(id: String): Int?

    @Query("SELECT substr(syncedSnapshot, :start, :count) FROM notes WHERE id = :id")
    suspend fun legacySnapshotPart(id: String, start: Int, count: Int): String?

    @Query("UPDATE notes SET inkData = '', syncedSnapshot = '' WHERE id = :id")
    suspend fun clearLegacyInk(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: Note)

    @Query("UPDATE notes SET deleted = 1, dirty = 1, updatedAt = :now WHERE id = :id")
    suspend fun markDeleted(id: String, now: Long)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun purge(id: String)

    /**
     * Replaces a note's details with a version from the cloud, but only if the
     * user has not edited it since sync read it ([expectedUpdatedAt]). The caller writes the ink.
     * Returns the number of rows changed.
     */
    @Query(
        """UPDATE notes SET title = :title, createdAt = :createdAt,
           updatedAt = :updatedAt, dirty = :dirty
           WHERE id = :id AND updatedAt = :expectedUpdatedAt""",
    )
    suspend fun replaceContentIfUnchanged(
        id: String,
        title: String,
        createdAt: Long,
        updatedAt: Long,
        dirty: Boolean,
        expectedUpdatedAt: Long,
    ): Int

    /**
     * Records a successful sync. The note stays dirty if it was edited while
     * the upload was in flight, so the next sync picks up that edit.
     */
    @Query(
        """UPDATE notes SET driveFileId = :remoteId, remotePdfId = :remotePdfId,
           remoteVersion = :remoteVersion, lastSyncedAt = :syncedAt,
           dirty = CASE WHEN updatedAt = :expectedUpdatedAt THEN 0 ELSE 1 END
           WHERE id = :id""",
    )
    suspend fun markSynced(
        id: String,
        remoteId: String,
        remotePdfId: String?,
        remoteVersion: String,
        syncedAt: Long,
        expectedUpdatedAt: Long,
    )
}
