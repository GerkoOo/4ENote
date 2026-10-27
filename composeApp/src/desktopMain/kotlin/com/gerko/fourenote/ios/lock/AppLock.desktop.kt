package com.gerko.fourenote.ios.lock

/**
 * The desktop build has no biometrics: it reports unavailable and lets every request through, so
 * the UI can be exercised on Linux. The enabled flag is kept in memory only.
 */
actual class AppLock actual constructor() {
    actual fun isAvailable(): Boolean = false

    actual fun authenticate(reason: String, onResult: (Boolean) -> Unit) {
        onResult(true)
    }

    actual var enabled: Boolean = false
}
