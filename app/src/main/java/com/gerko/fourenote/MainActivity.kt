package com.gerko.fourenote

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.gerko.fourenote.navigation.FourENoteNavGraph
import com.gerko.fourenote.ui.editor.PenKeys
import com.gerko.fourenote.ui.settings.LockScreen
import com.gerko.fourenote.ui.theme.FourENoteTheme

// FragmentActivity (not ComponentActivity) because BiometricPrompt needs a FragmentManager.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as FourENoteApp

        val prefs = getSharedPreferences("ui", MODE_PRIVATE)
        setContent {
            var darkTheme by remember { mutableStateOf(prefs.getBoolean(KEY_DARK, true)) }
            val locked by app.appLock.locked.collectAsState()
            val lockEnabled by app.appLock.enabledFlow.collectAsState()
            // The recent-apps thumbnail would show notes; hide it while the lock is on.
            LaunchedEffect(lockEnabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    setRecentsScreenshotEnabled(!lockEnabled)
                }
            }
            FourENoteTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box {
                        FourENoteNavGraph(
                            repository = app.repository,
                            syncManager = app.syncManager,
                            attachments = app.attachments,
                            backgrounds = app.backgrounds,
                            toolPreferences = app.toolPreferences,
                            darkTheme = darkTheme,
                            onToggleTheme = {
                                darkTheme = !darkTheme
                                prefs.edit().putBoolean(KEY_DARK, darkTheme).apply()
                            },
                        )
                        if (locked) {
                            LockScreen(
                                appLock = app.appLock,
                                onBack = { moveTaskToBack(true) },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // While locked, pen buttons must not reach the editor underneath.
        if (appLockLocked()) return super.dispatchKeyEvent(event)
        return PenKeys.dispatch(event) || super.dispatchKeyEvent(event)
    }

    private fun appLockLocked(): Boolean = (application as FourENoteApp).appLock.locked.value

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) PenKeys.releaseAll()
    }

    override fun onStart() {
        super.onStart()
        val app = application as FourENoteApp
        app.appLock.onAppStarted(System.currentTimeMillis())
        app.syncManager.startAutoSync()
    }

    override fun onStop() {
        val app = application as FourENoteApp
        app.appLock.onAppStopped(System.currentTimeMillis())
        app.syncManager.stopAutoSync()
        super.onStop()
    }

    private companion object {
        const val KEY_DARK = "dark_theme"
    }
}
