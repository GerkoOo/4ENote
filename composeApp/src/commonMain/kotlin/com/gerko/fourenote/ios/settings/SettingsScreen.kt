package com.gerko.fourenote.ios.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gerko.fourenote.ios.lock.AppLock

/** Settings: what is coming, the Face ID lock, and the version. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(lock: AppLock, onBack: () -> Unit) {
    var enabled by remember { mutableStateOf(lock.enabled) }
    val available = remember { lock.isAvailable() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("‹ Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Section("Cloud sync")
            Text(
                "Google Drive and Nextcloud sync is coming to the iPad app.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.padding(top = 24.dp))
            HorizontalDivider()
            Spacer(Modifier.padding(top = 24.dp))

            Section("Privacy")
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Lock with Face ID", style = MaterialTheme.typography.bodyLarge)
                    if (!available) {
                        Text(
                            "Face ID is not available on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked = enabled,
                    enabled = available,
                    onCheckedChange = { want ->
                        if (!want) {
                            lock.enabled = false
                            enabled = false
                        } else {
                            // Turning the lock on requires proving it works first.
                            lock.authenticate("Enable Face ID lock") { ok ->
                                if (ok) {
                                    lock.enabled = true
                                    enabled = true
                                }
                            }
                        }
                    },
                )
            }

            Spacer(Modifier.padding(top = 24.dp))
            HorizontalDivider()
            Spacer(Modifier.padding(top = 24.dp))

            Section("About")
            Text("4ENote for iPad, preview", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
