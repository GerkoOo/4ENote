package com.gerko.fourenote.data.sync

import com.gerko.fourenote.data.security.NotesKey

/** The notes key as sync needs it (NotesKeyring on Android). */
interface SyncKeys {
    /** The notes key, or null when this device doesn't have it (yet). */
    fun key(): NotesKey?

    /** The key to encrypt uploads with, or null while the cloud's lock file isn't up to date. */
    fun keyForUpload(): NotesKey?

    /** No 4ENote password is set: locked notes go to the cloud unencrypted. */
    val isOff: Boolean

    /** Takes the cloud's lock file (null: none yet); returns a lock file to upload, if any. */
    fun reconcile(cloud: String?): String?

    /** [text] (from [reconcile]) has reached the cloud. */
    fun uploaded(text: String)

    /** "keyId:version" of the note's cloud file as last encrypted with the key. */
    fun encryptedVersion(noteId: String): String?

    fun encryptedVersion(noteId: String, version: String)

    /** How many locked notes are waiting for the key. */
    fun reportWaiting(count: Int)
}
