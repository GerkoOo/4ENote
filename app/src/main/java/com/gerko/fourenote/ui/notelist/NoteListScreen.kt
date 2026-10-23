package com.gerko.fourenote.ui.notelist

import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import com.gerko.fourenote.data.Folder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.LocalCafe
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
    onOpenSupport: () -> Unit = {},
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
    val folders by viewModel.folders.collectAsState()
    val here = viewModel.folderId
    // Folder dialogs: naming a new one, the one long-pressed, renaming it, and what is being moved.
    var naming by remember { mutableStateOf(false) }
    var folderMenu by remember { mutableStateOf<Folder?>(null) }
    var renaming by remember { mutableStateOf<Folder?>(null) }
    var movingFolder by remember { mutableStateOf<Folder?>(null) }
    var movingNotes by remember { mutableStateOf(false) }
    // Drag and drop: the note being dragged, how far, and where things can be dropped (root pixels).
    // A drop target is a folder id, or "" for the top of the home page.
    var draggingNote by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var dragFinger by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val dropTargets = remember { androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }
    // Changes only when the finger crosses into another target, so dragging doesn't redraw the page.
    val dropTarget by remember {
        androidx.compose.runtime.derivedStateOf {
            if (draggingNote == null) null else dropTargets.entries.firstOrNull { dragFinger in it.value }?.key
        }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val saveBackup = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> if (uri != null) viewModel.backUp { context.contentResolver.openOutputStream(uri) } }
    val openBackup = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.restore { context.contentResolver.openInputStream(uri) } }
    // Back leaves selecting first, then goes up a folder, before it leaves the app.
    BackHandler(enabled = selecting) { viewModel.clearSelection() }
    BackHandler(enabled = !selecting && here != null) { viewModel.goUp() }

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
                        IconButton(onClick = { movingNotes = true }) {
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Move to a folder")
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
                // Where you are: 4ENote › School › Maths; tap a part to go back to it.
                title = {
                    val path = viewModel.pathTo(here, folders)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        Text(
                            "4ENote",
                            color = if (path.isEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .dropTarget("", dropTargets, highlighted = dropTarget == "" && path.isNotEmpty())
                                .clickable(enabled = path.isNotEmpty()) { viewModel.openFolder(null) },
                        )
                        path.forEachIndexed { i, folder ->
                            Text("  ›  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val last = i == path.lastIndex
                            Text(
                                folder.name,
                                color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .dropTarget(folder.id, dropTargets, highlighted = dropTarget == folder.id && !last)
                                    .clickable(enabled = !last) { viewModel.openFolder(folder.id) },
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (here != null) {
                        IconButton(onClick = { viewModel.goUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up one folder")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { naming = true }) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = "New folder")
                    }
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
                    // Backup, restore and updates.
                    Box {
                        var menu by remember { mutableStateOf(false) }
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Back up all notes…") },
                                leadingIcon = { Icon(Icons.Filled.Backup, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    saveBackup.launch("4ENote backup ${java.time.LocalDate.now()}.zip")
                                },
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Restore from a backup…") },
                                leadingIcon = { Icon(Icons.Filled.Restore, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    openBackup.launch(arrayOf("application/zip", "application/octet-stream"))
                                },
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Look for updates by itself") },
                                leadingIcon = {
                                    androidx.compose.material3.Checkbox(checked = viewModel.autoUpdateCheck, onCheckedChange = null)
                                },
                                onClick = { viewModel.changeAutoUpdateCheck(!viewModel.autoUpdateCheck) },
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Check for updates") },
                                leadingIcon = { Icon(Icons.Filled.SystemUpdate, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    viewModel.checkForUpdate()
                                },
                            )
                        }
                    }
                    IconButton(onClick = onOpenSupport) {
                        Icon(Icons.Filled.LocalCafe, contentDescription = "Support 4ENote")
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
        // A newer version found in the background: a small banner, until downloaded or "Later".
        viewModel.availableUpdate?.let { update ->
            androidx.compose.material3.Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                modifier = Modifier.padding(padding).padding(horizontal = 24.dp, vertical = 8.dp).fillMaxWidth().zIndex(1f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
                    Icon(Icons.Filled.SystemUpdate, contentDescription = null)
                    Text("  4ENote ${update.version} is available", modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        viewModel.dismissUpdate()
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(com.gerko.fourenote.data.update.UpdateChecker.DOWNLOAD),
                                ),
                            )
                        }
                    }) { Text("Download") }
                    TextButton(onClick = viewModel::dismissUpdate) { Text("Later") }
                }
            }
        }
        val allNotes = notes ?: return@Scaffold
        // A note whose folder is gone (deleted on another device) shows at the top.
        val folderIds = folders.mapTo(HashSet()) { it.id }
        fun placeOf(note: Note) = note.folderId?.takeIf { it in folderIds }
        val notes = allNotes.filter { placeOf(it) == here }
        val subfolders = folders.filter { it.parentId == here || (it.parentId != null && it.parentId !in folderIds && here == null) }
        if (notes.isEmpty() && subfolders.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    if (here == null) "No notes yet. Tap New note to start writing." else "This folder is empty. Tap New note to write in it.",
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
                items(subfolders, key = { "folder-" + it.id }) { folder ->
                    val count = allNotes.count { placeOf(it) == folder.id } + folders.count { it.parentId == folder.id }
                    FolderCard(
                        folder = folder,
                        count = count,
                        highlighted = dropTarget == folder.id,
                        modifier = Modifier.dropTarget(folder.id, dropTargets, highlighted = false),
                        onClick = { if (!selecting) viewModel.openFolder(folder.id) },
                        onLongClick = { if (!selecting) folderMenu = folder },
                    )
                }
                items(notes, key = { it.id }) { note ->
                    // Holding a note starts selecting; while selecting, taps tick and untick notes.
                    val dragged = draggingNote == note.id
                    NoteCard(
                        note = note,
                        backgrounds = backgrounds,
                        selected = note.id in viewModel.selected,
                        onClick = { if (selecting) viewModel.toggleSelected(note.id) else onOpenNote(note.id) },
                        onLongClick = { viewModel.toggleSelected(note.id) },
                        loadThumbnail = viewModel::thumbnail,
                        onDragStart = { finger ->
                            draggingNote = note.id
                            dragOffset = androidx.compose.ui.geometry.Offset.Zero
                            dragFinger = finger
                        },
                        onDrag = { delta, finger ->
                            dragOffset += delta
                            dragFinger = finger
                        },
                        onDrop = {
                            val target = dropTargets.entries.firstOrNull { dragFinger in it.value }?.key
                            draggingNote = null
                            if (target != null) viewModel.dropNote(note.id, target.ifEmpty { null })
                        },
                        // While dragged, the note stays faded in its place; a floating copy follows the finger.
                        modifier = Modifier.graphicsLayer { alpha = if (dragged) 0.35f else 1f },
                    )
                }
            }
        }
    }

    // The dragged note, floating above everything (the top bar too), under the finger.
    draggingNote?.let { id ->
        val note = notes?.firstOrNull { it.id == id }
        val count = if (id in viewModel.selected) viewModel.selected.size else 1
        Box(Modifier.fillMaxSize()) {
            androidx.compose.material3.Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
                modifier = Modifier.offset {
                    androidx.compose.ui.unit.IntOffset(
                        (dragFinger.x - 40.dp.toPx()).toInt(),
                        (dragFinger.y - 36.dp.toPx()).toInt(),
                    )
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Note, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "  " + (note?.title?.ifBlank { "Untitled" } ?: "Note") + if (count > 1) "  +${count - 1}" else "",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                }
            }
        }
    }
    viewModel.report?.let { report ->
        AlertDialog(
            onDismissRequest = viewModel::dismissReport,
            title = { Text(report.title) },
            text = { if (report.text.isNotEmpty()) Text(report.text) else androidx.compose.material3.LinearProgressIndicator() },
            confirmButton = {
                if (report.download != null) {
                    TextButton(onClick = {
                        viewModel.dismissReport()
                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(report.download))) }
                    }) { Text("Download") }
                } else {
                    TextButton(onClick = viewModel::dismissReport) { Text("OK") }
                }
            },
            dismissButton = { if (report.download != null) TextButton(onClick = viewModel::dismissReport) { Text("Later") } },
        )
    }
    if (naming) {
        NameDialog(title = "New folder", initial = "", confirm = "Create", onDone = { naming = false; if (it != null) viewModel.createFolder(it) })
    }
    renaming?.let { folder ->
        NameDialog(title = "Rename folder", initial = folder.name, confirm = "Rename", onDone = { renaming = null; if (it != null) viewModel.renameFolder(folder, it) })
    }
    folderMenu?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderMenu = null },
            title = { Text(folder.name) },
            text = { Text("Deleting a folder keeps everything in it: its notes and folders move up one level.") },
            confirmButton = {
                Row {
                    TextButton(onClick = { folderMenu = null; renaming = folder }) { Text("Rename") }
                    TextButton(onClick = { folderMenu = null; movingFolder = folder }) { Text("Move") }
                    TextButton(onClick = { folderMenu = null; viewModel.deleteFolder(folder) }) { Text("Delete") }
                }
            },
            dismissButton = { TextButton(onClick = { folderMenu = null }) { Text("Cancel") } },
        )
    }
    if (movingNotes) {
        MoveDialog(
            title = "Move ${viewModel.selected.size} note${if (viewModel.selected.size == 1) "" else "s"} to",
            folders = folders,
            exclude = null,
            onPick = { movingNotes = false; viewModel.moveSelected(it) },
            onDismiss = { movingNotes = false },
        )
    }
    movingFolder?.let { folder ->
        MoveDialog(
            title = "Move \"${folder.name}\" to",
            folders = folders,
            exclude = folder.id,
            onPick = { movingFolder = null; viewModel.moveFolder(folder, it) },
            onDismiss = { movingFolder = null },
        )
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
    onDragStart: (finger: androidx.compose.ui.geometry.Offset) -> Unit = {},
    onDrag: (delta: androidx.compose.ui.geometry.Offset, finger: androidx.compose.ui.geometry.Offset) -> Unit = { _, _ -> },
    onDrop: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var coordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    val currentOnClick by androidx.compose.runtime.rememberUpdatedState(onClick)
    val currentOnLongClick by androidx.compose.runtime.rememberUpdatedState(onLongClick)
    val currentOnDragStart by androidx.compose.runtime.rememberUpdatedState(onDragStart)
    val currentOnDrag by androidx.compose.runtime.rememberUpdatedState(onDrag)
    val currentOnDrop by androidx.compose.runtime.rememberUpdatedState(onDrop)
    Card(
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .padding(8.dp)
            .onGloballyPositioned { coordinates = it }
            // A tap opens; holding picks the note up: drag it onto a folder, or let go where it is
            // to tick it. A quick drag before the hold scrolls the page as usual.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                            if (change.isConsumed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                return@withTimeoutOrNull false // a scroll
                            }
                            if (!change.pressed) {
                                currentOnClick()
                                return@withTimeoutOrNull false
                            }
                        }
                        @Suppress("UNREACHABLE_CODE") false
                    } == null
                    if (!held) return@awaitEachGesture
                    // Measured on the screen, not on the card: the card moves with the finger, so its own
                    // coordinates would shift under it and the card would jump back and forth.
                    fun root(p: androidx.compose.ui.geometry.Offset) = coordinates?.localToRoot(p) ?: p
                    var last = root(down.position)
                    currentOnDragStart(last)
                    var moved = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                        val now = root(change.position)
                        val delta = now - last
                        last = now
                        moved += delta.getDistance()
                        currentOnDrag(delta, now)
                    }
                    currentOnDrop()
                    // Held without moving: tick it, as before.
                    if (moved < viewConfiguration.touchSlop) currentOnLongClick()
                }
            },
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

/** A folder tile on the home page: tap to open it, hold for rename, move and delete. */
@Composable
private fun FolderCard(
    folder: Folder,
    count: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Card(
        // Lights up while a note is dragged over it.
        border = if (highlighted) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .padding(8.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Icon(
                Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Text(folder.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (count == 1) "1 item" else "$count items",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Asks for a name; [onDone] gets it, or null if cancelled. */
@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onDone: (String?) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(title) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Folder name") },
            )
        },
        confirmButton = { TextButton(onClick = { onDone(name) }, enabled = name.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel") } },
    )
}

/** Picks where to move something: the top of the home page or any folder (indented by depth). */
@Composable
private fun MoveDialog(title: String, folders: List<Folder>, exclude: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    // The folder tree in order, without [exclude] and what is inside it.
    val children = folders.groupBy { it.parentId }
    val rows = ArrayList<Pair<Folder, Int>>()
    fun walk(parent: String?, depth: Int) {
        for (f in children[parent].orEmpty()) {
            if (f.id == exclude) continue
            rows += f to depth
            walk(f.id, depth + 1)
        }
    }
    walk(null, 0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextButton(onClick = { onPick(null) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Home, contentDescription = null)
                    Text("  Home (top)", modifier = Modifier.weight(1f))
                }
                rows.forEach { (folder, depth) ->
                    TextButton(
                        onClick = { onPick(folder.id) },
                        modifier = Modifier.fillMaxWidth().padding(start = (depth * 20).dp),
                    ) {
                        Icon(Icons.Filled.Folder, contentDescription = null)
                        Text("  ${folder.name}", modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Marks a place a dragged note can be dropped on ([key]: a folder id, "" for the top). */
@Composable
private fun Modifier.dropTarget(
    key: String,
    targets: MutableMap<String, androidx.compose.ui.geometry.Rect>,
    highlighted: Boolean,
): Modifier {
    androidx.compose.runtime.DisposableEffect(key) { onDispose { targets.remove(key) } }
    return this
        .onGloballyPositioned { targets[key] = it.boundsInRoot() }
        .then(
            if (highlighted) {
                Modifier.background(MaterialTheme.colorScheme.primaryContainer, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            } else {
                Modifier
            },
        )
}
