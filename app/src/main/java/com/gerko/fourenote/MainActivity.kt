package com.gerko.fourenote

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.gerko.fourenote.navigation.FourENoteNavGraph
import com.gerko.fourenote.ui.editor.PenKeys
import com.gerko.fourenote.ui.theme.FourENoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as FourENoteApp

        val prefs = getSharedPreferences("ui", MODE_PRIVATE)
        setContent {
            var darkTheme by remember { mutableStateOf(prefs.getBoolean(KEY_DARK, true)) }
            FourENoteTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
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
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean = PenKeys.dispatch(event) || super.dispatchKeyEvent(event)

    override fun onStart() {
        super.onStart()
        (application as FourENoteApp).syncManager.startAutoSync()
    }

    override fun onStop() {
        (application as FourENoteApp).syncManager.stopAutoSync()
        super.onStop()
    }

    private companion object {
        const val KEY_DARK = "dark_theme"
    }
}
