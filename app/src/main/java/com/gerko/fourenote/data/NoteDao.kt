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
        "remoteVersion, remotePdfId, '' AS syncedSnapshot, folderId, trashedAt, locked, rev"

@Dao
interface NoteDao {
    @Query("SELECT $META FROM notes WHERE deleted = 0 AND trashedAt IS NULL ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Note>>

    /** The notes in Deleted notes, most recently trashed first. */
    @Query("SELECT $META FROM notes WHERE deleted = 0 AND trashedAt IS NOT NULL ORDER BY trashedAt DESC")
    fun observeTrashed(): Flow<List<Note>>

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
           updatedAt = :updatedAt, dirty = :dirty, folderId = :folderId, locked = :locked, rev = :rev
           WHERE id = :id AND updatedAt = :expectedUpdatedAt""",
    )
    suspend fun replaceContentIfUnchanged(
        id: String,
        title: String,
        createdAt: Long,
        updatedAt: Long,
        dirty: Boolean,
        expectedUpdatedAt: Long,
        folderId: String?,
        locked: Boolean,
        rev: Long,
    ): Int

    /**
     * Moves notes into Deleted notes ([at] is when) or back out of it (null). This is local
     * housekeeping, not an edit: the note's content and sync state stay as they are.
     */
    @Query("UPDATE notes SET trashedAt = :at WHERE id IN (:ids)")
    suspend fun setTrashed(ids: List<String>, at: Long?)

    /** Ids of the notes trashed before [before], for deleting them for good. */
    @Query("SELECT id FROM notes WHERE deleted = 0 AND trashedAt IS NOT NULL AND trashedAt < :before")
    suspend fun idsTrashedBefore(before: Long): List<String>

    /** Locks or unlocks notes; the lock is an edit, so it syncs. */
    @Query("UPDATE notes SET locked = :locked, updatedAt = :now, dirty = 1 WHERE id IN (:ids)")
    suspend fun setLocked(ids: List<String>, locked: Boolean, now: Long)

    /** Moves notes into a folder (null: the top); a move is an edit, so it syncs. */
    @Query("UPDATE notes SET folderId = :folderId, updatedAt = :now, dirty = 1 WHERE id IN (:ids)")
    suspend fun moveToFolder(ids: List<String>, folderId: String?, now: Long)

    /**
     * Records a successful sync. The note stays dirty if it was edited while
     * the upload was in flight, so the next sync picks up that edit.
     */
    /** Another cloud was chosen: every note starts unsynced there, and uploads. */
    @Query("UPDATE notes SET driveFileId = NULL, remoteVersion = NULL, remotePdfId = NULL, lastSyncedAt = NULL, dirty = 1")
    suspend fun forgetCloud()

    @Query(
        """UPDATE notes SET driveFileId = :remoteId, remotePdfId = :remotePdfId,
           remoteVersion = :remoteVersion, lastSyncedAt = :syncedAt, rev = :rev,
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
        rev: Long,
    )
}
