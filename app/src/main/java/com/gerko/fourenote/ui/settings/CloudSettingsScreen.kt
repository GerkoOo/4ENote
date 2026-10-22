package com.gerko.fourenote.ui.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gerko.fourenote.data.sync.CloudChoice
import com.gerko.fourenote.data.sync.SyncManager
import com.gerko.fourenote.data.sync.SyncStatus

@Composable
fun CloudSettingsScreen(syncManager: SyncManager, onBack: () -> Unit) {
    val choice by syncManager.choice.collectAsState()
    val status by syncManager.status.collectAsState()
    val googleAccount by syncManager.googleAuth.account.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud sync") },
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
            Text(
                "Notes sync to a 4ENote folder in your own cloud. An edit on one device " +
                    "updates all of them, and every note also gets a PDF copy.",
                style = MaterialTheme.typography.bodyLarge,
            )

            Card {
                Column(Modifier.padding(vertical = 8.dp)) {
                    CloudChoice.entries.forEach { option ->
                        val available = syncManager.isAvailable(option)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = option == choice,
                                    enabled = available,
                                    onClick = { syncManager.select(option) },
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            RadioButton(
                                selected = option == choice,
                                onClick = null,
                                enabled = available,
                            )
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(option.label, style = MaterialTheme.typography.titleMedium)
                                val note = when {
                                    option == CloudChoice.GOOGLE_DRIVE && !syncManager.googleAuth.configured ->
                                        "Not set up in this build (needs a Google client id)"
                                    option == CloudChoice.GOOGLE_DRIVE && googleAccount == null ->
                                        "Sign in below first"
                                    option == CloudChoice.GOOGLE_DRIVE -> "Signed in as $googleAccount"
                                    option == CloudChoice.ONEDRIVE -> "Coming soon"
                                    else -> null
                                }
                                if (note != null) {
                                    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // Google: signs in through the browser; only the files 4ENote creates are reachable.
            if (syncManager.googleAuth.configured) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (googleAccount == null) {
                        Button(onClick = { context.startActivity(syncManager.googleAuth.signInIntent()) }) { Text("Sign in with Google") }
                        Text(
                            "4ENote only gets access to the files it creates itself, in a \"4ENote\" folder.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text("Google: $googleAccount", style = MaterialTheme.typography.bodyMedium)
                        androidx.compose.material3.OutlinedButton(onClick = syncManager::signOutOfGoogle) { Text("Sign out") }
                    }
                }
            }

            if (choice != CloudChoice.NONE) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = syncManager::requestSync,
                        enabled = status != SyncStatus.Syncing,
                    ) { Text("Sync now") }
                    Text(statusText(status), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun statusText(status: SyncStatus): String = when (status) {
    SyncStatus.Idle -> "Not synced yet"
    SyncStatus.Syncing -> "Syncing…"
    is SyncStatus.Failed -> "Sync failed: ${status.message}"
    is SyncStatus.Done -> {
        val r = status.result
        val when_ = DateUtils.getRelativeTimeSpanString(status.at).toString()
        val parts = buildList {
            if (r.uploaded > 0) add("${r.uploaded} uploaded")
            if (r.downloaded > 0) add("${r.downloaded} downloaded")
            if (r.merged > 0) add("${r.merged} merged")
            if (r.deletedHere + r.deletedInCloud > 0) add("${r.deletedHere + r.deletedInCloud} deleted")
            if (r.errors.isNotEmpty()) add("${r.errors.size} failed: ${r.errors.first()}")
        }
        "Synced $when_ · " + parts.ifEmpty { listOf("everything up to date") }.joinToString(", ")
    }
}
