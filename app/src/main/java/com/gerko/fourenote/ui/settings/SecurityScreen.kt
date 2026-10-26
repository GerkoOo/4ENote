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
import com.gerko.fourenote.data.security.Unlock
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import com.gerko.fourenote.data.security.NoteLock

@Composable
fun SecurityScreen(appLock: AppLock, noteLock: NoteLock, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val appPassword = (context.applicationContext as com.gerko.fourenote.FourENoteApp).appPassword
    var passwordSet by remember { mutableStateOf(appPassword.isSet) }
    var choosingPassword by remember { mutableStateOf(false) }
    var savingPassword by remember { mutableStateOf(false) }
    var confirmingRemove by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val canAuthenticate = passwordSet || remember { Unlock.canUnlock(context) }

    // Setting, changing or removing the password first needs the same proof as unlocking
    // (when there is anything to ask yet).
    fun proveThen(title: String, then: () -> Unit) {
        passwordError = null
        if (!Unlock.canUnlock(context)) return then()
        if (activity == null) return
        Unlock.authenticate(activity, title, onSuccess = then, onFail = { passwordError = it })
    }
    var enabled by remember { mutableStateOf(appLock.enabled) }
    var delayMs by remember { mutableStateOf(appLock.delayMs) }
    var error by remember { mutableStateOf<String?>(null) }
    var unlockAll by remember { mutableStateOf(noteLock.unlockAllAtOnce) }
    var noteError by remember { mutableStateOf<String?>(null) }

    // Turning the lock on or off both need the user to prove who they are.
    fun changeEnabled(target: Boolean) {
        if (activity == null) return
        error = null
        Unlock.authenticate(
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
                .widthIn(max = 640.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("4ENote password", style = MaterialTheme.typography.titleMedium)
            Text(
                if (passwordSet) {
                    "Set. When the fingerprint or face isn't used, this password opens 4ENote and locked notes; the tablet's PIN doesn't."
                } else {
                    "Not set. When the fingerprint or face isn't used, the tablet's screen lock (often a short PIN) opens 4ENote and locked notes. " +
                        "A password with letters, numbers and symbols is much harder to guess."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.Button(
                    onClick = { proveThen(if (passwordSet) "Change the password" else "Set a password") { choosingPassword = true } },
                    enabled = !savingPassword,
                ) { Text(if (passwordSet) "Change password" else "Set password") }
                if (passwordSet) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { proveThen("Remove the password") { confirmingRemove = true } },
                        enabled = !savingPassword,
                    ) { Text("Remove") }
                }
            }
            if (savingPassword) Text("Saving…", style = MaterialTheme.typography.bodySmall)
            passwordError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            androidx.compose.material3.HorizontalDivider()

            Text("App lock", style = MaterialTheme.typography.titleMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (passwordSet) "Ask for fingerprint, face or the 4ENote password to open 4ENote" else "Ask for fingerprint, face or PIN to open 4ENote",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (!canAuthenticate) {
                        Text(
                            "Set a 4ENote password above, or a screen lock in Android settings, first",
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
                    "fingerprint, face or " + (if (passwordSet) "4ENote password" else "PIN") + " each time it is opened, and gets no PDF copy in the cloud.",
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
                        Unlock.authenticate(
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

    if (choosingPassword) {
        SetPasswordDialog(
            onSave = { chars ->
                choosingPassword = false
                savingPassword = true
                // Hashing is slow on purpose; keep it off the main thread.
                scope.launch {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { appPassword.set(chars) }
                    chars.fill(' ')
                    savingPassword = false
                    passwordSet = true
                }
            },
            onDismiss = { choosingPassword = false },
        )
    }
    if (confirmingRemove) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmingRemove = false },
            title = { Text("Remove the 4ENote password?") },
            text = { Text("The tablet's screen lock will open 4ENote and locked notes again when the fingerprint or face isn't used.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    appPassword.remove()
                    passwordSet = false
                    confirmingRemove = false
                }) { Text("Remove") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmingRemove = false }) { Text("Cancel") } },
        )
    }
}
