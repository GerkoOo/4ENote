package com.gerko.fourenote.ui.notelist

import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.gerko.fourenote.data.Note
import com.gerko.fourenote.data.attachments.BackgroundLoader
import com.gerko.fourenote.data.ink.PageBackground
import com.gerko.fourenote.data.ink.InkSerializer
import com.gerko.fourenote.data.sync.CloudChoice
import com.gerko.fourenote.data.sync.SyncManager
import com.gerko.fourenote.data.sync.SyncStatus
import com.gerko.fourenote.ui.editor.SmartInvert
import com.gerko.fourenote.ui.editor.drawInkStroke
import com.gerko.fourenote.ui.editor.drawPaper
import com.gerko.fourenote.ui.editor.toPath

@Composable
fun NoteListScreen(
    viewModel: NoteListViewModel,
    syncManager: SyncManager,
    backgrounds: BackgroundLoader,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenCloudSettings: () -> Unit,
) {
    val notes by viewModel.notes.collectAsState()
    val cloud by syncManager.choice.collectAsState()
    val syncStatus by syncManager.status.collectAsState()
    var pendingDelete by remember { mutableStateOf<Note?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("4ENote") },
                actions = {
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            if (darkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = if (darkTheme) "Switch to light mode" else "Switch to dark mode",
                        )
                    }
                    IconButton(onClick = onOpenCloudSettings) {
                        val icon = when {
                            cloud == CloudChoice.NONE -> Icons.Filled.CloudOff
                            syncStatus is SyncStatus.Syncing -> Icons.Filled.CloudSync
                            syncStatus is SyncStatus.Failed -> Icons.Filled.SyncProblem
                            else -> Icons.Filled.CloudDone
                        }
                        Icon(icon, contentDescription = "Cloud sync")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpenNote(viewModel.createNote().id) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New note") },
            )
        },
    ) { padding ->
        val notes = notes ?: return@Scaffold
        if (notes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "No notes yet. Tap New note to start writing.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 220.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.padding(padding),
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        backgrounds = backgrounds,
                        onClick = { onOpenNote(note.id) },
                        onLongClick = { pendingDelete = note },
                    )
                }
            }
        }
    }

    pendingDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete note?") },
            text = { Text("\"${note.title.ifBlank { "Untitled" }}\" will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteNote(note)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun NoteCard(note: Note, backgrounds: BackgroundLoader, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        NoteThumbnail(note, backgrounds)
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                note.title.ifBlank { "Untitled" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                DateUtils.getRelativeTimeSpanString(note.updatedAt).toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Miniature of the note's first page, background included. */
@Composable
private fun NoteThumbnail(note: Note, backgrounds: BackgroundLoader) {
    val doc = remember(note.inkData) { InkSerializer.decode(note.inkData) }
    val first = doc.pages.first()
    val paper = doc.paper
    val paths = remember(doc) { doc.visibleStrokes(first).map { it to it.toPath() } }
    val widthPx = with(LocalDensity.current) { 240.dp.toPx() }.toInt()
    val darkPaper = MaterialTheme.colorScheme.background.luminance() < 0.5f && first.background == null
    val background by produceState<ImageBitmap?>(null, note.id, first.background) {
        value = first.background?.let { backgrounds.load(note.id, it, widthPx)?.asImageBitmap() }
    }
    val pictures by produceState(emptyMap<String, ImageBitmap>(), note.id, first.images) {
        value = first.images.map { it.file }.toSet().mapNotNull { file ->
            backgrounds.load(note.id, PageBackground.Image(file), widthPx / 2)?.let { file to it.asImageBitmap() }
        }.toMap()
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clipToBounds(),
    ) {
        val scale = size.width / first.width
        val bg = background
        if (bg != null) {
            val p = first.placement
            drawImage(
                bg,
                dstOffset = IntOffset((p.x * scale).toInt(), (p.y * scale).toInt()),
                dstSize = IntSize((size.width * p.scale).toInt(), (first.height * scale * p.scale).toInt()),
                filterQuality = FilterQuality.Medium,
                colorFilter = if (first.invertBackground) SmartInvert else null,
            )
        } else {
            drawPaper(paper, scale = scale, dark = darkPaper)
        }
        for (image in first.images) {
            val bitmap = pictures[image.file] ?: continue
            withTransform({ rotate(image.rotation, pivot = Offset(image.x * scale, image.y * scale)) }) {
                drawImage(
                    bitmap,
                    dstOffset = IntOffset(((image.x - image.width / 2) * scale).toInt(), ((image.y - image.height / 2) * scale).toInt()),
                    dstSize = IntSize((image.width * scale).toInt().coerceAtLeast(1), (image.height * scale).toInt().coerceAtLeast(1)),
                    alpha = image.opacity,
                    colorFilter = if (image.inverted) SmartInvert else null,
                )
            }
        }
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            for ((stroke, path) in paths) drawInkStroke(stroke, path, darkPaper)
        }
    }
}
