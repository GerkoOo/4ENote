package com.gerko.fourenote.ios.store

import okio.Path

/** The app's own folder: notes, sync state and settings (Application Support on the iPad). */
expect fun appDataDir(): Path

/** Keeps [file] out of device backups (a restored copy must not reuse this device's id). */
expect fun excludeFromBackup(file: Path)

/** Small secrets, like the WebDAV password: the Keychain on the iPad. */
expect object Secrets {
    fun get(key: String): String?

    /** Stores [value], or removes the secret when it is null. */
    fun set(key: String, value: String?)
}
