package com.gerko.fourenote.ios

import androidx.compose.ui.window.ComposeUIViewController
import com.gerko.fourenote.platform.CryptoSelfTest
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIViewController
import platform.posix.fflush
import platform.posix.stdout

/** Called from Swift (iosApp/iosApp/FourENoteApp.swift) to host the Compose UI. */
fun MainViewController(): UIViewController {
    if ("-selftest" in NSProcessInfo.processInfo.arguments) selfTest()
    return ComposeUIViewController { App() }
}

/** The crypto known answers, printed for the iOS CI (it launches the app with -selftest). */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun selfTest() {
    val failures = CryptoSelfTest.failures()
    println(if (failures.isEmpty()) "SELFTEST OK" else "SELFTEST FAILED: ${failures.joinToString("; ")}")
    fflush(stdout)
}
