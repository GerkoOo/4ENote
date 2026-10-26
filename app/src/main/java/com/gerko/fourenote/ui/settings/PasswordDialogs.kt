package com.gerko.fourenote.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.gerko.fourenote.data.security.AppPassword
import com.gerko.fourenote.data.security.Unlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Asks for the 4ENote password whenever Unlock needs it; lives once at the top of the app. */
@Composable
fun PasswordPrompt(password: AppPassword) {
    val request by Unlock.passwordRequest.collectAsState()
    val current = request ?: return
    var text by remember(current) { mutableStateOf("") }
    var shown by remember(current) { mutableStateOf(false) }
    var checking by remember(current) { mutableStateOf(false) }
    var error by remember(current) { mutableStateOf<String?>(null) }
    var waitMs by remember(current) { mutableLongStateOf(password.waitMs()) }
    val scope = rememberCoroutineScope()
    // Counts down a wait after too many wrong tries.
    LaunchedEffect(waitMs > 0) {
        while (waitMs > 0) {
            delay(1000)
            waitMs = password.waitMs()
        }
    }

    fun submit() {
        if (checking || waitMs > 0 || text.isEmpty()) return
        checking = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.Default) { password.check(text.toCharArray()) }
            checking = false
            when (result) {
                AppPassword.Result.Right -> Unlock.finishPassword(ok = true)
                AppPassword.Result.Wrong -> error = "Wrong password"
                is AppPassword.Result.Blocked -> {
                    error = "Too many wrong tries"
                    waitMs = result.waitMs
                }
            }
            text = ""
        }
    }

    AlertDialog(
        onDismissRequest = { if (!checking) Unlock.finishPassword(ok = false) },
        title = { Text(current.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField(text, { text = it }, "4ENote password", shown, { shown = it }, enabled = !checking && waitMs == 0L, onDone = ::submit)
                when {
                    waitMs > 0 -> Text("Try again in ${formatWait(waitMs)}", color = MaterialTheme.colorScheme.error)
                    checking -> Text("Checking…")
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = ::submit, enabled = !checking && waitMs == 0L && text.isNotEmpty()) { Text("Unlock") }
        },
        dismissButton = {
            TextButton(onClick = { Unlock.finishPassword(ok = false) }, enabled = !checking) { Text("Cancel") }
        },
    )
}

/** Choosing a new 4ENote password: typed twice, checked for strength, with the warning about forgetting it. */
@Composable
fun SetPasswordDialog(onSave: (CharArray) -> Unit, onDismiss: () -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(false) }
    val problem = if (first.isEmpty()) null else AppPassword.problem(first)
    val mismatch = second.isNotEmpty() && second != first
    val ok = first.isNotEmpty() && problem == null && second == first
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("4ENote password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "At least ${AppPassword.MIN_LENGTH} characters, mixing letters, numbers and symbols. " +
                        "It opens 4ENote and your locked notes when the fingerprint or face isn't used; the tablet's PIN won't any more.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                PasswordField(first, { first = it }, "New password", shown, { shown = it })
                problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                PasswordField(second, { second = it }, "Type it again", shown, { shown = it }, onDone = { if (ok) onSave(first.toCharArray()) })
                if (mismatch) Text("The two don't match", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Text(
                    "Write it down somewhere safe. Nobody can reset it for you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(first.toCharArray()) }, enabled = ok) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    shown: Boolean,
    onShownChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    onDone: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone() }),
        trailingIcon = {
            IconButton(onClick = { onShownChange(!shown) }) {
                Icon(
                    if (shown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (shown) "Hide password" else "Show password",
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun formatWait(ms: Long): String {
    val seconds = (ms + 999) / 1000
    return if (seconds < 60) "$seconds s" else "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')} min"
}
