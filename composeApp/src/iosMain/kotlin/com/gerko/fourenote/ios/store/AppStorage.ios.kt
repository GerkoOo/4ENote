@file:OptIn(ExperimentalForeignApi::class)

package com.gerko.fourenote.ios.store

import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

actual fun appDataDir(): Path {
    val support = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
    return "$support/4ENote".toPath()
}

actual fun excludeFromBackup(file: Path) {
    NSURL.fileURLWithPath(file.toString()).setResourceValue(NSNumber(bool = true), forKey = NSURLIsExcludedFromBackupKey, error = null)
}

/** The Keychain, through Swift (FourENoteApp.swift sets [bridge]): its C API is clumsy from Kotlin. */
interface SecretBridge {
    fun get(key: String): String?
    fun set(key: String, value: String?)
}

actual object Secrets {
    var bridge: SecretBridge? = null

    actual fun get(key: String): String? = bridge?.get(key)

    actual fun set(key: String, value: String?) {
        checkNotNull(bridge) { "Keychain not installed (see FourENoteApp.swift)" }.set(key, value)
    }
}
