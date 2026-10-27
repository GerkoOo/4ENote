package com.gerko.fourenote.data.sync

/**
 * The shape of a device id: the name of the cloud files a device writes (see [NoteFileFormat]).
 * Shared, so the iPad app reads the Android app's files and the other way round.
 */
object DeviceIds {
    const val LENGTH = 8
    const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

    fun isValid(id: String) = id.length == LENGTH && id.all { it in ALPHABET }
}
