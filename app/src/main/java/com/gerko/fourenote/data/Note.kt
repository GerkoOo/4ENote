package com.gerko.fourenote.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Shared contract between the handwriting canvas and cloud sync.
 * [inkData] holds serialized stroke data (see InkSerializer).
 *
 * Sync bookkeeping:
 * - [dirty]: changed on this device since the last successful sync.
 * - [deleted]: a tombstone kept until the deletion has reached the cloud.
 * - [remoteId]/[remotePdfId]: the note's files in the connected cloud.
 * - [remoteVersion]: the cloud file's version when we last synced, so we can
 *   tell whether another device has changed it since.
 * - [syncedSnapshot]: the note file as of the last sync; the common ancestor
 *   used to merge edits made on two devices.
 */
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val inkData: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // Column keeps its original name so existing databases need no table rebuild.
    @ColumnInfo(name = "driveFileId") val remoteId: String? = null,
    val lastSyncedAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
    val remoteVersion: String? = null,
    val remotePdfId: String? = null,
    @ColumnInfo(defaultValue = "") val syncedSnapshot: String = "",
    /** The folder the note is in; null is the top of the home page. */
    val folderId: String? = null,
    /**
     * When the note was moved to Deleted notes, or null if it isn't there. Local to this device:
     * it never syncs, so a note trashed here stays out of the home page on this device only.
     */
    val trashedAt: Long? = null,
    /**
     * Needs the fingerprint, face or screen lock to open. It syncs, so a note locked on one
     * device is locked on all of them; the unlock itself is only remembered for this session.
     */
    @ColumnInfo(defaultValue = "0") val locked: Boolean = false,
)

/**
 * The note has files in the cloud: this device uploaded it ([Note.remoteId]), or got it from
 * other devices' files (a device that only reads a note has no file of its own).
 */
val Note.isInCloud: Boolean get() = remoteId != null || lastSyncedAt != null
