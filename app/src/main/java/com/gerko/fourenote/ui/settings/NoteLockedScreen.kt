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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.gerko.fourenote.data.security.Unlock

/**
 * Stands in for the editor while a locked note hasn't been unlocked in this session. It looks
 * like [LockScreen] and asks for the fingerprint, face or screen lock as soon as it appears.
 */
@Composable
fun NoteLockedScreen(title: String, onUnlocked: () -> Unit, onBack: () -> Unit) {
    val activity = LocalContext.current as? FragmentActivity
    var error by remember { mutableStateOf<String?>(null) }
    var prompting by remember { mutableStateOf(false) }

    fun prompt() {
        if (activity == null || prompting) return
        prompting = true
        error = null
        Unlock.authenticate(
            activity = activity,
            title = "Unlock note",
            onSuccess = {
                prompting = false
                onUnlocked()
            },
            onFail = { message ->
                prompting = false
                error = message
            },
        )
    }

    // Ask as soon as the note is opened; the button is for trying again.
    LaunchedEffect(Unit) { prompt() }

    BackHandler { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
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
            Text(
                title.ifBlank { "Untitled" },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text("This note is locked", style = MaterialTheme.typography.headlineSmall)
            error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = { prompt() }) { Text("Unlock") }
        }
    }
}
