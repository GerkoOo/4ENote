package com.gerko.fourenote.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.gerko.fourenote.data.security.AppLock
import com.gerko.fourenote.data.security.Biometric
import com.gerko.fourenote.data.security.NoteLock

@Composable
fun SecurityScreen(appLock: AppLock, noteLock: NoteLock, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val canAuthenticate = remember { Biometric.canAuthenticate(context) }
    var enabled by remember { mutableStateOf(appLock.enabled) }
    var delayMs by remember { mutableStateOf(appLock.delayMs) }
    var error by remember { mutableStateOf<String?>(null) }
    var unlockAll by remember { mutableStateOf(noteLock.unlockAllAtOnce) }
    var noteError by remember { mutableStateOf<String?>(null) }

    // Turning the lock on or off both need the user to prove who they are.
    fun changeEnabled(target: Boolean) {
        if (activity == null) return
        error = null
        Biometric.authenticate(
            activity = activity,
            title = if (target) "Turn on the app lock" else "Turn off the app lock",
            onSuccess = {
                appLock.enabled = target
                enabled = target
            },
            onFail = { message -> error = message },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App lock") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Ask for fingerprint, face or PIN to open 4ENote",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (!canAuthenticate) {
                        Text(
                            "Set up a screen lock (PIN, pattern or password) in Android settings first",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { changeEnabled(it) },
                    enabled = canAuthenticate,
                )
            }
            error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            if (enabled) {
                Text("Ask again", style = MaterialTheme.typography.titleMedium)
                AppLock.DELAYS.forEachIndexed { i, delay ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = delay == delayMs,
                                onClick = {
                                    appLock.delayMs = delay
                                    delayMs = delay
                                },
                            )
                            .padding(vertical = 8.dp),
                    ) {
                        RadioButton(selected = delay == delayMs, onClick = null)
                        Text(
                            AppLock.DELAY_LABELS[i],
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }

            Text(
                "Off by default.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Locked notes", style = MaterialTheme.typography.titleMedium)
            Text(
                "Lock a note from the home page: select it and tap the lock. A locked note asks for your " +
                    "fingerprint, face or PIN each time it is opened, and gets no PDF copy in the cloud.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Unlock all locked notes at once", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "After one unlock, the others open too until you leave the app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = unlockAll,
                    onCheckedChange = { target ->
                        // Turning it on lets one unlock open every note, so it needs the same proof.
                        if (!target) {
                            noteLock.unlockAllAtOnce = false
                            unlockAll = false
                            return@Switch
                        }
                        if (activity == null) return@Switch
                        noteError = null
                        Biometric.authenticate(
                            activity = activity,
                            title = "Unlock all locked notes at once",
                            onSuccess = {
                                noteLock.unlockAllAtOnce = true
                                unlockAll = true
                            },
                            onFail = { message -> noteError = message },
                        )
                    },
                    enabled = canAuthenticate,
                )
            }
            noteError?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            Text(
                "Encryption of locked notes in the cloud comes in a later version.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
