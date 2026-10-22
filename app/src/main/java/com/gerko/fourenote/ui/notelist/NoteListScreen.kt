package com.gerko.fourenote.ui.notelist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import com.gerko.fourenote.data.ink.InkDocument
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.BackHandler
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
    var confirmingDelete by remember { mutableStateOf(false) }
    val photoRequests by viewModel.photoRequests.collectAsState()
    var showingRequests by remember { mutableStateOf(false) }
    // The frame being filled with the scanner, if any.
    var filling by remember { mutableStateOf<com.gerko.fourenote.data.NoteRepository.PhotoRequest?>(null) }
    var showingInbox by remember { mutableStateOf(false) }
    // The frame number the next scans for later are for (0: the next empty frames); null when not scanning.
    var scanningForLater by remember { mutableStateOf<Int?>(null) }
    val selecting = viewModel.selected.isNotEmpty()
    // Back leaves selecting before it leaves the app.
    BackHandler(enabled = selecting) { viewModel.clearSelection() }

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Filled.Close, contentDescription = "Stop selecting")
                        }
                    },
                    title = { Text("${viewModel.selected.size} selected") },
                    actions = {
                        IconButton(onClick = viewModel::selectAll) {
                            Icon(Icons.Filled.SelectAll, contentDescription = "Select all")
                        }
                        IconButton(onClick = viewModel::duplicateSelected) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate")
                        }
                        IconButton(onClick = { confirmingDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    },
                )
            } else TopAppBar(
                title = { Text("4ENote") },
                actions = {
                    // Frames waiting for a photo (set on another device): fill them from here.
                    if (photoRequests.isNotEmpty()) {
                        IconButton(onClick = { showingRequests = true }) {
                            BadgedBox(badge = { Badge { Text("${photoRequests.size}") } }) {
                                Icon(Icons.Filled.AddAPhoto, contentDescription = "Photos requested: ${photoRequests.size}")
                            }
                        }
                    }
                    // Scan now, for a frame whose note hasn't reached this device yet.
                    IconButton(onClick = { viewModel.refreshInbox(); showingInbox = true }) {
                        val waiting = viewModel.inboxScans.size
                        if (waiting > 0) {
                            BadgedBox(badge = { Badge { Text("$waiting") } }) {
                                Icon(Icons.Filled.DocumentScanner, contentDescription = "Scans for later: $waiting waiting")
                            }
                        } else {
                            Icon(Icons.Filled.DocumentScanner, contentDescription = "Scan for later")
                        }
                    }
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
            if (!selecting) ExtendedFloatingActionButton(
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
                    // Holding a note starts selecting; while selecting, taps tick and untick notes.
                    NoteCard(
                        note = note,
                        backgrounds = backgrounds,
                        selected = note.id in viewModel.selected,
                        onClick = { if (selecting) viewModel.toggleSelected(note.id) else onOpenNote(note.id) },
                        onLongClick = { viewModel.toggleSelected(note.id) },
                        loadThumbnail = viewModel::thumbnail,
                    )
                }
            }
        }
    }

    if (showingRequests) {
        AlertDialog(
            onDismissRequest = { showingRequests = false },
            title = { Text("Photos requested") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Frames waiting for a photo. Take it now, even offline; it reaches your other devices with the next sync.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    photoRequests.forEach { request ->
                        TextButton(onClick = { showingRequests = false; filling = request }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.AddAPhoto, contentDescription = null)
                            Text(
                                "  ${request.label.ifEmpty { "Photo" }} · ${request.noteTitle} · page ${request.pageNumber}",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showingRequests = false }) { Text("Close") } },
        )
    }
    if (showingInbox) {
        var frame by remember { mutableStateOf(0) }
        AlertDialog(
            onDismissRequest = { showingInbox = false },
            title = { Text("Scan for later") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Scan now, even before the note with the frame is on this device. Scans wait here and in your " +
                            "cloud; as soon as a device has the note, each one goes into its frame.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("For frame", style = MaterialTheme.typography.labelLarge)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (0..8).forEach { n ->
                            androidx.compose.material3.FilterChip(
                                selected = frame == n,
                                onClick = { frame = n },
                                label = { Text(if (n == 0) "Next empty" else "Photo $n") },
                            )
                        }
                    }
                    if (frame != 0) {
                        Text(
                            "More than one scan: they go into Photo $frame, Photo ${frame + 1}, …",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (viewModel.inboxScans.isNotEmpty()) {
                        Text("Waiting", style = MaterialTheme.typography.labelLarge)
                        viewModel.inboxScans.forEach { scan ->
                            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    (if (scan.frame == 0) "Next empty frame" else "Photo ${scan.frame}") + " · " +
                                        DateUtils.getRelativeTimeSpanString(scan.takenAt),
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { viewModel.discardInboxScan(scan) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete this scan")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showingInbox = false; scanningForLater = frame }) { Text("Scan") }
            },
            dismissButton = { TextButton(onClick = { showingInbox = false }) { Text("Close") } },
        )
    }
    scanningForLater?.let { frame ->
        com.gerko.fourenote.ui.scan.ScanDialog(
            decode = viewModel::decodePhoto,
            onDone = { files ->
                viewModel.addInboxScans(files, frame)
                scanningForLater = null
            },
            onCancel = { scanningForLater = null },
        )
    }
    filling?.let { request ->
        com.gerko.fourenote.ui.scan.ScanDialog(
            decode = viewModel::decodePhoto,
            onDone = { files ->
                files.firstOrNull()?.let { viewModel.fillPhotoRequest(request, it) }
                filling = null
            },
            onCancel = { filling = null },
        )
    }

    if (confirmingDelete) {
        val count = viewModel.selected.size
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(if (count == 1) "Delete note?" else "Delete $count notes?") },
            text = { Text(if (count == 1) "It will be permanently deleted." else "They will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSelected()
                    confirmingDelete = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun NoteCard(
    note: Note,
    backgrounds: BackgroundLoader,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    loadThumbnail: suspend (String) -> InkDocument?,
) {
    Card(
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .padding(8.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box {
            NoteThumbnail(note, backgrounds, loadThumbnail)
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                )
            }
        }
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
private fun NoteThumbnail(note: Note, backgrounds: BackgroundLoader, load: suspend (String) -> InkDocument?) {
    // Only the first page is read, from disk, and again only when the note has changed.
    val loaded by produceState<InkDocument?>(null, note.id, note.updatedAt) { value = load(note.id) }
    val doc = loaded ?: InkDocument()
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
