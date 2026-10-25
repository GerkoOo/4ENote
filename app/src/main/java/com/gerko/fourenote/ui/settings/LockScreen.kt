package com.gerko.fourenote.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.gerko.fourenote.data.security.AppLock
import com.gerko.fourenote.data.security.Biometric

/**
 * Covers the whole app while [AppLock.locked] is true. It is opaque and swallows every touch,
 * so nothing underneath can be tapped; Back sends the app to the background instead of
 * revealing the screens below.
 */
@Composable
fun LockScreen(appLock: AppLock, onBack: () -> Unit) {
    val activity = LocalContext.current as? FragmentActivity
    var error by remember { mutableStateOf<String?>(null) }
    var prompting by remember { mutableStateOf(false) }

    fun prompt() {
        if (activity == null || prompting) return
        prompting = true
        error = null
        Biometric.authenticate(
            activity = activity,
            title = "Unlock 4ENote",
            onSuccess = {
                prompting = false
                appLock.unlocked()
            },
            onFail = { message ->
                prompting = false
                error = message
            },
        )
    }

    // Ask as soon as the lock appears; the button is for trying again.
    LaunchedEffect(Unit) { prompt() }

    BackHandler { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Swallow every touch so the screens underneath can't be reached.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
            Text("4ENote is locked", style = MaterialTheme.typography.headlineSmall)
            error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = { prompt() }) { Text("Unlock") }
        }
    }
}
