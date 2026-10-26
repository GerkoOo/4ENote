package com.gerko.fourenote.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

internal val PAGE_FOOTER_HEIGHT = 28.dp

/** Under each page: its number counted from 1, and the page's menu. */
@Composable
internal fun PageFooter(
    number: Int,
    count: Int,
    hasImage: Boolean,
    inverted: Boolean,
    onInvert: (Boolean) -> Unit,
    onInvertAll: (Boolean) -> Unit,
    onAdjustImage: () -> Unit,
    onFitImage: () -> Unit,
    onAddAfter: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    // Kept low so it takes little room between pages.
    Box(Modifier.fillMaxWidth().height(PAGE_FOOTER_HEIGHT)) {
        Text(
            "$number / $count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Center),
        )
        Box(Modifier.align(Alignment.CenterEnd)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(PAGE_FOOTER_HEIGHT).clip(CircleShape).clickable { open = true },
            ) {
                Icon(
                    Icons.Filled.MoreHoriz,
                    contentDescription = "Page $number options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                if (hasImage) {
                    DropdownMenuItem(
                        text = { Text("Invert colors") },
                        leadingIcon = { Icon(Icons.Filled.InvertColors, contentDescription = null) },
                        trailingIcon = { Checkbox(checked = inverted, onCheckedChange = null) },
                        onClick = { open = false; onInvert(!inverted) },
                    )
                    DropdownMenuItem(
                        text = { Text(if (inverted) "Restore colors on all PDF pages" else "Invert all PDF pages") },
                        leadingIcon = { Icon(Icons.Filled.InvertColors, contentDescription = null) },
                        onClick = { open = false; onInvertAll(!inverted) },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Move / resize image") },
                        leadingIcon = { Icon(Icons.Filled.OpenWith, contentDescription = null) },
                        onClick = { open = false; onAdjustImage() },
                    )
                    DropdownMenuItem(
                        text = { Text("Fit image to page") },
                        leadingIcon = { Icon(Icons.Filled.FitScreen, contentDescription = null) },
                        onClick = { open = false; onFitImage() },
                    )
                    HorizontalDivider()
                }
                DropdownMenuItem(
                    text = { Text("Add blank page after") },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = { open = false; onAddAfter() },
                )
                DropdownMenuItem(
                    text = { Text("Clear ink on page") },
                    leadingIcon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
                    onClick = { open = false; onClear() },
                )
                DropdownMenuItem(
                    text = { Text("Delete page") },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = { open = false; onDelete() },
                )
            }
        }
    }
}
