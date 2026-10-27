package com.gerko.fourenote.ios.lock

/**
 * The device's biometric lock (Face ID on the iPad). The desktop build has no biometrics, so it
 * reports unavailable and lets every request through; that keeps the UI testable on Linux.
 */
expect class AppLock() {
    /** True if the device can check the user's identity (Face ID or Touch ID). */
    fun isAvailable(): Boolean

    /** Asks the user to prove who they are; [onResult] is called with the outcome. */
    fun authenticate(reason: String, onResult: (Boolean) -> Unit)

    /** Whether the app should ask for Face ID before showing notes. Persisted across launches. */
    var enabled: Boolean
}
