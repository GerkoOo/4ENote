package com.gerko.fourenote.data.sync

/**
 * What a note file holds: the note's metadata and its ink, as it travels between devices through
 * the cloud. Shared by the Android app and the iPad app, so both read and write the same files.
 *
 * The sync bookkeeping that stays on each device (dirty, deleted, remote ids, snapshots) is not
 * part of it: only what every device must agree on.
 */
data class NoteContent(
    val id: String,
    val title: String = "",
    val inkData: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val folderId: String? = null,
    val locked: Boolean = false,
    val rev: Long = 0,
    val remotePdfId: String? = null,
)
