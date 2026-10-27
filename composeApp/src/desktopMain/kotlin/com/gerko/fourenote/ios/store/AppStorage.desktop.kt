package com.gerko.fourenote.ios.store

import okio.Path
import okio.Path.Companion.toPath

/** The desktop build only lets Linux compile and preview the iPad UI. */
actual fun appDataDir(): Path = (System.getProperty("user.home") + "/.4enote-ios-preview").toPath()

actual fun excludeFromBackup(file: Path) {}

actual object Secrets {
    private val values = HashMap<String, String>()

    actual fun get(key: String): String? = values[key]

    actual fun set(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
}
