package com.gerko.fourenote.ios.lock

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSUserDefaults
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Face ID (or the passcode, if Face ID is not enrolled) through LocalAuthentication. The enabled
 * flag lives in NSUserDefaults so it survives a relaunch.
 */
@OptIn(ExperimentalForeignApi::class)
actual class AppLock actual constructor() {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun isAvailable(): Boolean = LAContext().canEvaluatePolicy(
        LAPolicyDeviceOwnerAuthentication,
        null,
    )

    actual fun authenticate(reason: String, onResult: (Boolean) -> Unit) {
        val context = LAContext()
        if (!context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, null)) {
            onResult(false)
            return
        }
        context.evaluatePolicy(LAPolicyDeviceOwnerAuthentication, reason) { success, _ ->
            // The completion handler runs on a private queue; the UI must be touched on main.
            dispatch_async(dispatch_get_main_queue()) { onResult(success) }
        }
    }

    actual var enabled: Boolean
        get() = defaults.boolForKey(KEY)
        set(value) = defaults.setBool(value, KEY)

    private companion object {
        const val KEY = "com.gerko.fourenote.ios.lock.enabled"
    }
}
