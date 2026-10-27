package com.gerko.fourenote.ios.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import com.gerko.fourenote.ios.lock.AppLock

/**
 * Shows [content] only after the user has unlocked the app, if the Face ID lock is on. The first
 * unlock is attempted automatically, so the user usually just has to look at the iPad.
 */
@Composable
fun LockGate(lock: AppLock, content: @Composable () -> Unit) {
    var unlocked by remember { mutableStateOf(!lock.enabled) }
    var asking by remember { mutableStateOf(false) }

    fun ask() {
        if (asking) return
        asking = true
        lock.authenticate("Unlock 4ENote") { ok ->
            asking = false
            if (ok) unlocked = true
        }
    }

    LaunchedEffect(Unit) {
        if (!unlocked) ask()
    }

    if (unlocked) {
        content()
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("4ENote is locked", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Unlock with Face ID to see your notes.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
            Button(onClick = { ask() }, enabled = !asking) { Text("Unlock") }
        }
    }
}
