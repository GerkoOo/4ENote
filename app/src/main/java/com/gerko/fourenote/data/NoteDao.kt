package com.gerko.fourenote.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): Note?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: Note)

    /**
     * Updates only the Drive sync bookkeeping fields, deliberately leaving [Note.updatedAt]
     * untouched so recording a successful upload doesn't itself mark the note as freshly edited.
     */
    @Query("UPDATE notes SET driveFileId = :driveFileId, lastSyncedAt = :syncedAt WHERE id = :id")
    suspend fun updateSyncMetadata(id: String, driveFileId: String, syncedAt: Long)

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)
}
