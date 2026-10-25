package com.gerko.fourenote.ui.editor

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the tablet's auto-rotate is off, kept up to date while shown. Then the editor offers a
 * button to turn the screen by hand. When auto-rotate comes back on, a turn made by hand is undone
 * so the screen follows the tablet again.
 */
@Composable
fun rememberRotationLocked(): Boolean {
    val context = LocalContext.current
    val resolver = context.contentResolver
    fun read() = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0
    var locked by remember { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                locked = read()
                if (!locked) context.findActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return locked
}

/** Turns the screen a quarter: portrait to landscape and back. */
fun rotateScreen(activity: Activity) {
    val landscape = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    activity.requestedOrientation =
        if (landscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
}

fun android.content.Context.findActivity(): Activity? =
    generateSequence(this) { (it as? android.content.ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
