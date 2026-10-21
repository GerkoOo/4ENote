package com.gerko.fourenote.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE deleted = 0 ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id AND deleted = 0")
    suspend fun getById(id: String): Note?

    /** Every note including tombstones, for sync. */
    @Query("SELECT * FROM notes")
    suspend fun getAllForSync(): List<Note>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: Note)

    @Query("UPDATE notes SET deleted = 1, dirty = 1, updatedAt = :now WHERE id = :id")
    suspend fun markDeleted(id: String, now: Long)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun purge(id: String)

    /**
     * Replaces a note's content with a version from the cloud, but only if the
     * user has not edited it since sync read it ([expectedUpdatedAt]).
     * Returns the number of rows changed.
     */
    @Query(
        """UPDATE notes SET title = :title, inkData = :inkData, createdAt = :createdAt,
           updatedAt = :updatedAt, dirty = :dirty
           WHERE id = :id AND updatedAt = :expectedUpdatedAt""",
    )
    suspend fun replaceContentIfUnchanged(
        id: String,
        title: String,
        inkData: String,
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
           remoteVersion = :remoteVersion, syncedSnapshot = :snapshot, lastSyncedAt = :syncedAt,
           dirty = CASE WHEN updatedAt = :expectedUpdatedAt THEN 0 ELSE 1 END
           WHERE id = :id""",
    )
    suspend fun markSynced(
        id: String,
        remoteId: String,
        remotePdfId: String?,
        remoteVersion: String,
        snapshot: String,
        syncedAt: Long,
        expectedUpdatedAt: Long,
    )
}
