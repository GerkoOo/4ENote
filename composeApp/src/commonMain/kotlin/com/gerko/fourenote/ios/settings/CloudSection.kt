package com.gerko.fourenote.ios.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gerko.fourenote.ios.sync.SyncController
import com.gerko.fourenote.ios.sync.SyncStatus
import com.gerko.fourenote.ios.sync.WebDavAccount
import kotlinx.coroutines.launch

/** Nextcloud / WebDAV: the account form, or the connected account with its last sync. */
@Composable
fun CloudSection(sync: SyncController) {
    val status by sync.status.collectAsState()
    if (status == SyncStatus.NotConnected) {
        ConnectForm(sync)
        return
    }
    val account = remember(status) { sync.account() }
    Text("Nextcloud / WebDAV", style = MaterialTheme.typography.bodyLarge)
    Text(
        "${account?.user.orEmpty()} at ${account?.url.orEmpty()}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.padding(top = 8.dp))
    Text(
        when (val s = status) {
            SyncStatus.Syncing -> "Syncing…"
            is SyncStatus.Done -> when {
                s.at == 0L -> "Not synced yet in this session."
                s.problems.isEmpty() -> "Synced."
                else -> "Synced, with problems: ${s.problems.joinToString("; ").take(300)}"
            }
            is SyncStatus.Failed -> s.message
            SyncStatus.NotConnected -> ""
        },
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.padding(top = 8.dp))
    Row {
        Button(onClick = { sync.syncNow() }, enabled = status != SyncStatus.Syncing) { Text("Sync now") }
        Spacer(Modifier.width(12.dp))
        OutlinedButton(onClick = { sync.disconnect() }) { Text("Disconnect") }
    }
}

@Composable
private fun ConnectForm(sync: SyncController) {
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Text(
        "Sync with Nextcloud or any WebDAV server. Notes go into a folder named 4ENote; " +
            "the Android app syncs with the same folder. Google Drive is coming.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("WebDAV address") },
            placeholder = { Text("https://cloud.example.com/remote.php/dav/files/me") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text("User name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password (an app password is best)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
    error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
    }
    Button(
        onClick = {
            busy = true
            error = null
            scope.launch {
                val trimmed = url.trim()
                error = when {
                    !trimmed.startsWith("https://") && !trimmed.startsWith("http://") -> "The address starts with https://"
                    else -> sync.connect(WebDavAccount(trimmed, user.trim(), password))
                }
                busy = false
            }
        },
        enabled = !busy && url.isNotBlank() && user.isNotBlank() && password.isNotEmpty(),
        modifier = Modifier.padding(top = 12.dp),
    ) { Text(if (busy) "Connecting…" else "Connect") }
}
