package com.gerko.fourenote.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gerko.fourenote.BuildConfig

/** Where the support page's buttons lead. The donation opens PayPal in the browser; nothing is paid inside the app. */
object SupportLinks {
    /** The PayPal donation link (paypal.me/… or a PayPal donate button link); the button is hidden while empty. */
    const val PAYPAL = ""
    const val GITHUB = "https://github.com/GerkoOo/4ENote"
    const val ISSUES = "https://github.com/GerkoOo/4ENote/issues"
    const val DOWNLOAD = "https://github.com/GerkoOo/4ENote/releases/latest"
}

/** "Support 4ENote": a small thank-you page with a PayPal donation and free ways to help. Only opened by the user. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    fun open(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Support 4ENote") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
        ) {
            Icon(Icons.Filled.LocalCafe, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp).height(64.dp).fillMaxWidth())
            Text("Thank you for using 4ENote!", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                "4ENote is free and open source, with no ads, no tracking and no accounts. It is made in spare " +
                    "time. If it helps you, you can buy me a coffee — it keeps the app growing.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 520.dp),
            )
            Spacer(Modifier.height(8.dp))
            val buttons = Modifier.widthIn(max = 360.dp).fillMaxWidth()
            if (SupportLinks.PAYPAL.isNotEmpty()) {
                Button(onClick = { open(SupportLinks.PAYPAL) }, modifier = buttons) {
                    Icon(Icons.Filled.LocalCafe, contentDescription = null)
                    Text("  Buy me a coffee (PayPal)")
                }
            }
            Text("Free ways to help", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            OutlinedButton(onClick = { open(SupportLinks.GITHUB) }, modifier = buttons) {
                Icon(Icons.Filled.Star, contentDescription = null)
                Text("  Star it on GitHub")
            }
            OutlinedButton(onClick = { open(SupportLinks.ISSUES) }, modifier = buttons) {
                Icon(Icons.Filled.BugReport, contentDescription = null)
                Text("  Report a bug or ask for a feature")
            }
            OutlinedButton(
                onClick = {
                    val share = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "4ENote, a free open-source handwriting notes app: ${SupportLinks.DOWNLOAD}")
                    context.startActivity(Intent.createChooser(share, "Share 4ENote"))
                },
                modifier = buttons,
            ) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Text("  Tell a friend")
            }
            Text(
                "4ENote ${BuildConfig.VERSION_NAME} · GPL-3.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
