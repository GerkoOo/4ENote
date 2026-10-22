package com.gerko.fourenote.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

/** Shows the selected handwriting as editable text, with alternatives, Copy and Share. */
@Composable
fun RecognitionDialog(viewModel: NoteEditorViewModel) {
    val state = viewModel.recognition ?: return
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    LaunchedEffect(state) {
        if (state is RecognitionState.Done) text = state.readings.first()
    }

    AlertDialog(
        onDismissRequest = viewModel::closeRecognition,
        title = { Text(if (viewModel.recognizingMath) "Maths to LaTeX" else "Handwriting to text") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!viewModel.recognizingMath) {
                    Text(
                        "English · open-source TrOCR model, on this device",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when (state) {
                    is RecognitionState.Working -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Text(
                            when {
                                !state.downloading -> "Reading…"
                                else -> "Downloading the ${if (viewModel.recognizingMath) "maths" else "handwriting"} model " +
                                    "(once, then it works offline)… ${((state.progress ?: 0f) * 100).toInt()}%"
                            },
                        )
                    }
                    is RecognitionState.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error)
                    is RecognitionState.Done -> {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                        )
                        if (viewModel.recognizingMath) FormulaPreview(text)
                        val alternatives = state.readings.drop(1).filter { it != text }.take(5)
                        if (alternatives.isNotEmpty()) {
                            Text("Or did you mean:", style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                alternatives.forEach { alt ->
                                    SuggestionChip(onClick = { text = alt }, label = { Text(alt) })
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (state is RecognitionState.Done) {
                FlowRow(horizontalArrangement = Arrangement.End) {
                    if (viewModel.recognizingMath) {
                        TextButton(onClick = { viewModel.placeFormula(text, replace = true) }) { Text("Replace handwriting") }
                        TextButton(onClick = { viewModel.placeFormula(text, replace = false) }) { Text("Insert below") }
                    }
                    TextButton(onClick = {
                        val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                        context.startActivity(Intent.createChooser(share, null))
                    }) { Text("Share") }
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(text))
                        viewModel.showMessage("Copied")
                        viewModel.closeRecognition()
                    }) { Text("Copy") }
                }
            }
        },
        dismissButton = { TextButton(onClick = viewModel::closeRecognition) { Text("Close") } },
    )
}

/** The LaTeX typeset as it will look on the page, updated as it's edited. */
@Composable
private fun FormulaPreview(latex: String) {
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    val textSize = with(LocalDensity.current) { 22.sp.toPx() }
    val bitmap = remember(latex, color, textSize) { renderLatex(latex, textSize, color)?.asImageBitmap() }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)) {
            if (bitmap != null) {
                Image(bitmap, contentDescription = "Formula preview")
            } else {
                Text("Not valid LaTeX yet", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
