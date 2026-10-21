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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = option == choice,
                                    enabled = option.available,
                                    onClick = { syncManager.select(option) },
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            RadioButton(
                                selected = option == choice,
                                onClick = null,
                                enabled = option.available,
                            )
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(option.label, style = MaterialTheme.typography.titleMedium)
                                if (!option.available) {
                                    Text(
                                        "Coming soon: needs its sign-in set up first",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
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
