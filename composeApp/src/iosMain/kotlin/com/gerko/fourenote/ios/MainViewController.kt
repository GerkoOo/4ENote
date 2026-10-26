package com.gerko.fourenote.ios

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Called from Swift (iosApp/iosApp/FourENoteApp.swift) to host the Compose UI. */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
