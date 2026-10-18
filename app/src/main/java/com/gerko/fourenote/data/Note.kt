package com.gerko.fourenote.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Shared contract between the handwriting canvas and Drive sync features.
 * [inkData] holds serialized stroke data (format owned by the canvas feature).
 * [driveFileId]/[lastSyncedAt] are null until the sync feature uploads the note.
 */
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val inkData: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val driveFileId: String? = null,
    val lastSyncedAt: Long? = null,
)
