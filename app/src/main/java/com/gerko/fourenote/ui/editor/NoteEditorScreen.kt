package com.gerko.fourenote.ui.editor

import kotlin.math.ln
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Title
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AssistChip
import android.app.Activity
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material3.Switch
import androidx.compose.foundation.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.automirrored.filled.RotateRight
import com.gerko.fourenote.data.ink.PageBackground
import com.gerko.fourenote.data.ink.PageImage
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.OpenWith
import com.gerko.fourenote.data.ink.BackgroundPlacement
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.derivedStateOf
import com.gerko.fourenote.data.ink.InkDocument
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.runtime.produceState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.HighlightAlt
import androidx.compose.material3.VerticalDivider
import com.gerko.fourenote.data.ink.Smoothing
import androidx.compose.material.icons.filled.Crop
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.ui.layout.layout
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.Timer
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SelectAll
import com.gerko.fourenote.ui.scan.ScanDialog
import kotlin.math.abs
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.gerko.fourenote.data.attachments.BackgroundLoader
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.gerko.fourenote.data.ink.PaperStyle
import kotlin.math.roundToInt
import com.gerko.fourenote.data.ink.Tool

@Composable
fun NoteEditorScreen(
    viewModel: NoteEditorViewModel,
    backgrounds: BackgroundLoader,
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val snackbar = remember { SnackbarHostState() }
    // The link picker, opened from a selection or a picture.
    var linking by remember { mutableStateOf(false) }
    // Where the reader is now, for the link picker to come back to after picking objects elsewhere.
    var currentStop by remember { mutableStateOf<(String) -> TrailStop>({ TrailStop(viewModel.noteId, "", 0, 0, 1f, 0f, 0f, it) }) }
    fun scrollToPage(index: Int) {
        scope.launch { gridState.animateScrollToItem(index) }
    }

    // A picked PDF waits here while the user chooses original or inverted colors.
    var pdfToImport by remember { mutableStateOf<android.net.Uri?>(null) }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pdfToImport = uri
    }
    // Pictures go onto the page currently on screen.
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val page = viewModel.pages.getOrNull(gridState.firstVisibleItemIndex.coerceAtMost(viewModel.pages.lastIndex))
        if (uris.isNotEmpty() && page != null) {
            viewModel.insertImages(page.id, uris.map { uri -> { context.contentResolver.openInputStream(uri) } })
        }
    }
    // The page on screen: new pages go right after it.
    fun currentPageId(): String? =
        viewModel.pages.getOrNull(gridState.firstVisibleItemIndex.coerceAtMost(viewModel.pages.lastIndex))?.id

    // Our own scanner (ui/scan): photograph pages, fit their corners, add them straightened.
    var scanning by remember { mutableStateOf(false) }
    fun startScan() {
        scanning = true
    }
    if (scanning) {
        ScanDialog(
            decode = viewModel::decodePhoto,
            // Scans fill the photo frames waiting on the page on screen; any others go onto the page.
            onDone = { files ->
                scanning = false
                val selectedPage = viewModel.selectedImageObject?.let { viewModel.selectedImage?.first }
                val page = viewModel.pages.firstOrNull { it.id == selectedPage }
                    ?: viewModel.pages.getOrNull(gridState.firstVisibleItemIndex.coerceAtMost(viewModel.pages.lastIndex))
                page?.let { viewModel.insertScans(it.id, files) }
            },
            onCancel = { scanning = false },
        )
    }

    // Where two fingers were held to open the pen dial, in window pixels; null when closed.
    var dialAt by remember { mutableStateOf<Offset?>(null) }
    var pickingColor by remember { mutableStateOf(false) }
    var addingTable by remember { mutableStateOf(false) }
    // A pen button click: [at] is where the pen was, in window pixels (null for key presses, which have no position).
    fun onPenButton(action: PenButtonAction, at: Offset?) {
        when (action) {
            PenButtonAction.OPEN_DIAL -> dialAt = at ?: context.resources.displayMetrics.let { Offset(it.widthPixels / 2f, it.heightPixels / 2f) }
            PenButtonAction.LAST_TOOL -> viewModel.switchToPreviousTool()
            PenButtonAction.NONE -> Unit
            else -> action.tool?.let(viewModel::toggleTool)
        }
    }
    DisposableEffect(viewModel) {
        PenKeys.listener = { button, click, description ->
            viewModel.lastPenSignal = description
            if (click) {
                onPenButton(if (button == PenKeys.STYLUS_SECONDARY) viewModel.secondaryButtonAction else viewModel.primaryButtonAction, null)
            }
        }
        onDispose { PenKeys.listener = null }
    }
    var showingSettings by remember { mutableStateOf(false) }
    // The last multi-finger tap, to spot double taps: two fingers twice = undo, three = redo.
    var lastTap by remember { mutableStateOf(0 to 0L) }
    fun onMultiFingerTap(fingers: Int) {
        val now = System.currentTimeMillis()
        val (lastFingers, lastTime) = lastTap
        if (fingers == lastFingers && now - lastTime < DOUBLE_TAP_MS) {
            lastTap = 0 to 0L
            when {
                fingers == 2 && viewModel.twoFingerDoubleTapUndo -> viewModel.undo()
                fingers == 3 && viewModel.threeFingerDoubleTapRedo -> viewModel.redo()
            }
        } else {
            lastTap = fingers to now
        }
    }

    LaunchedEffect(viewModel.message) {
        viewModel.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    // Full screen hides the app's bars and the system's; the dial and gestures still work.
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    // With the tablet's auto-rotate off, a button next to full screen turns the screen by hand.
    val rotationLocked = rememberRotationLocked()
    val view = LocalView.current
    DisposableEffect(fullScreen) {
        val window = generateSequence(view.context) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<android.app.Activity>().firstOrNull()?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        if (fullScreen) {
            bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            bars?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { bars?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    val darkPaper = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val focusTimer = (context.applicationContext as com.gerko.fourenote.FourENoteApp).focusTimer
    var showingFocus by remember { mutableStateOf(false) }
    // Arrange pages: all pages small in a grid, to reorder them or jump to one. No zooming or turning there.
    var arranging by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (!fullScreen) TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    BasicTextField(
                        value = viewModel.title,
                        onValueChange = viewModel::rename,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                actions = {
                    IconButton(onClick = viewModel::undo, enabled = viewModel.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = viewModel::redo, enabled = viewModel.canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    AddMenu(
                        onBlankPage = { scrollToPage(viewModel.addBlankPage(afterPageId = currentPageId())) },
                        onBlankPageAtEnd = { scrollToPage(viewModel.addBlankPage()) },
                        onImportPdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                        onImportImages = { imagePicker.launch(arrayOf("image/*")) },
                        onScan = ::startScan,
                        onTable = { addingTable = true },
                        onPhotoFromPhone = {
                            viewModel.pages.getOrNull(gridState.firstVisibleItemIndex.coerceAtMost(viewModel.pages.lastIndex))
                                ?.let { viewModel.requestPhoto(it.id) }
                        },
                    )
                    IconButton(onClick = {
                        viewModel.exportPdf { file ->
                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                            val share = android.content.Intent(android.content.Intent.ACTION_SEND)
                                .setType("application/pdf")
                                .putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(android.content.Intent.createChooser(share, "Share PDF"))
                        }
                    }, enabled = !viewModel.exporting) {
                        Icon(Icons.Filled.Share, contentDescription = "Share as PDF")
                    }
                    IconButton(onClick = { showingSettings = true }) {
                        Icon(Icons.Filled.Tune, contentDescription = "Pen and gesture settings")
                    }
                    IconButton(onClick = { showingFocus = true }) {
                        Icon(Icons.Filled.Timer, contentDescription = "Focus timer")
                    }
                    if (rotationLocked) {
                        IconButton(onClick = { context.findActivity()?.let(::rotateScreen) }) {
                            Icon(Icons.Filled.ScreenRotation, contentDescription = "Rotate screen")
                        }
                    }
                    IconButton(onClick = { fullScreen = true }) {
                        Icon(Icons.Filled.Fullscreen, contentDescription = "Full screen")
                    }
                    IconToggleButton(checked = arranging, onCheckedChange = { arranging = it }) {
                        Icon(Icons.Filled.ViewModule, contentDescription = if (arranging) "Done arranging pages" else "Arrange pages")
                    }
                    LayersMenu(viewModel)
                    PaperMenu(selected = viewModel.paper, onSelect = viewModel::changePaper)
                    IconToggleButton(
                        checked = viewModel.stylusOnly,
                        onCheckedChange = { viewModel.stylusOnly = it },
                    ) {
                        Icon(
                            Icons.Filled.Draw,
                            contentDescription = if (viewModel.stylusOnly) {
                                "Stylus only: on"
                            } else {
                                "Stylus only: off"
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        // The pages run on under the navigation bar to the screen's edge (a page there isn't cut
        // off short of it); the buttons at the bottom stay above the bar.
        Column(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            if (viewModel.importing || viewModel.exporting) LinearProgressIndicator(Modifier.fillMaxWidth())
            // The tools float over the pages (see ToolBar).
            Box(Modifier.weight(1f)) {
            if (viewModel.loaded) {
                PageList(
                    viewModel, backgrounds, gridState, darkPaper,
                    onTwoFingerHold = { dialAt = it },
                    onMultiFingerTap = ::onMultiFingerTap,
                    onPenButton = ::onPenButton,
                    addPages = AddPageActions(
                        blank = { scrollToPage(viewModel.addBlankPage(afterPageId = currentPageId())) },
                        blankAtEnd = { scrollToPage(viewModel.addBlankPage()) },
                        pdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                        images = { imagePicker.launch(arrayOf("image/*")) },
                        scan = ::startScan,
                    ),
                    fullScreen = fullScreen,
                    arranging = arranging,
                    onArranged = { arranging = false },
                    onOpenNote = onOpenNote,
                    onBack = onBack,
                    onLink = { linking = true },
                    onHere = { currentStop = it },
                )
            } else {
                Box(Modifier.fillMaxSize())
            }
            if (!fullScreen && !arranging) {
                ToolBar(
                    viewModel,
                    darkPaper,
                    onOpenDial = {
                        val metrics = context.resources.displayMetrics
                        dialAt = Offset(metrics.widthPixels / 2f, metrics.heightPixels / 2f)
                    },
                    onAddColor = { pickingColor = true },
                    modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            }
        }
    }
    // The focus timer's bar: across the top, or down the side when the screen is sideways.
    val sideways = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    com.gerko.fourenote.ui.focus.FocusBar(
        timer = focusTimer,
        vertical = sideways,
        onOpen = { showingFocus = true },
        onPhase = { phase ->
            com.gerko.fourenote.ui.focus.buzz(context)
            viewModel.showMessage(
                when (phase) {
                    com.gerko.fourenote.data.focus.FocusPhase.WORK -> "Break over: back to focus"
                    com.gerko.fourenote.data.focus.FocusPhase.BREAK -> "Time for a short break"
                    com.gerko.fourenote.data.focus.FocusPhase.LONG_BREAK -> "Time for a long break"
                },
            )
        },
        modifier = Modifier
            .align(if (sideways) Alignment.CenterStart else Alignment.TopCenter)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(if (sideways) WindowInsetsSides.Start else WindowInsetsSides.Top)),
    )
    if (showingFocus) com.gerko.fourenote.ui.focus.FocusDialog(focusTimer, onDismiss = { showingFocus = false })
    if (fullScreen) {
        Row(Modifier.align(Alignment.TopEnd).padding(12.dp).alpha(0.7f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (rotationLocked) {
                FilledTonalIconButton(onClick = { context.findActivity()?.let(::rotateScreen) }) {
                    Icon(Icons.Filled.ScreenRotation, contentDescription = "Rotate screen")
                }
            }
            FilledTonalIconButton(onClick = { fullScreen = false }) {
                Icon(Icons.Filled.FullscreenExit, contentDescription = "Exit full screen")
            }
        }
    }
    dialAt?.let { PenDial(viewModel, it, darkPaper, onDismiss = { dialAt = null }, onAddColor = { pickingColor = true }) }
    if (pickingColor) {
        ColorPickerDialog(
            initial = viewModel.toolColor,
            onPick = {
                viewModel.addCustomColor(it)
                pickingColor = false
            },
            onDismiss = { pickingColor = false },
        )
    }
    if (showingSettings) PenSettingsDialog(viewModel, onDismiss = { showingSettings = false })
    if (addingTable) {
        TableDialog(
            onInsert = { rows, columns ->
                addingTable = false
                // On the page at the top of the screen.
                viewModel.pages.getOrNull(gridState.firstVisibleItemIndex)?.let { viewModel.insertTable(it.id, rows, columns) }
            },
            onDismiss = { addingTable = false },
        )
    }
    pdfToImport?.let { uri ->
        fun import(inverted: Boolean) {
            pdfToImport = null
            viewModel.importPdf({ context.contentResolver.openInputStream(uri) }, inverted, ::scrollToPage)
        }
        PdfColorsDialog(
            uri = uri,
            suggestInvert = darkPaper,
            onKeep = { import(inverted = false) },
            onInvert = { import(inverted = true) },
            onCancel = { pdfToImport = null },
        )
    }
    RecognitionDialog(viewModel)
    if (linking) {
        LinkPickerDialog(
            viewModel,
            onPick = {
                viewModel.linkSelection(it)
                linking = false
            },
            onPickObjects = { note ->
                val (pageId, strokeIds, imageIds) = viewModel.selectionAnchors() ?: return@LinkPickerDialog
                // A note open further back along the trail would be gone back to, closing this one.
                if (note.id != viewModel.noteId && LinkTrail.stops.any { it.noteId == note.id }) {
                    linking = false
                    viewModel.showMessage("That note is open behind this one: go back to it first, or link to the whole note")
                    return@LinkPickerDialog
                }
                LinkPick.pending = PendingLink(
                    sourceNoteId = viewModel.noteId,
                    sourcePageId = pageId,
                    strokeIds = strokeIds,
                    imageIds = imageIds,
                    targetNoteId = note.id,
                    back = currentStop(""),
                )
                linking = false
                viewModel.clearSelection()
                // The target note's editor picks the objects; this one stays open when it is the same note.
                if (note.id != viewModel.noteId) onOpenNote(note.id)
            },
            onDismiss = { linking = false },
        )
    }
    }
}

/**
 * The note's pages, stacked vertically and scaled to the screen width (capped
 * so pages stay a comfortable size on large screens). One finger draws; two
 * fingers scroll. In stylus-only mode one finger scrolls too.
 */
@Composable
private fun PageList(
    viewModel: NoteEditorViewModel,
    backgrounds: BackgroundLoader,
    gridState: LazyGridState,
    darkPaper: Boolean,
    onTwoFingerHold: (Offset) -> Unit,
    onMultiFingerTap: (Int) -> Unit,
    onPenButton: (PenButtonAction, Offset) -> Unit,
    addPages: AddPageActions,
    fullScreen: Boolean,
    /** Arrange pages: a grid of small pages to drag into order; a tap opens one ([onArranged]). */
    arranging: Boolean = false,
    onArranged: () -> Unit = {},
    onOpenNote: (String) -> Unit = {},
    onBack: () -> Unit = {},
    /** Opens the link picker for the selection or the selected picture. */
    onLink: () -> Unit = {},
    /** Hands the caller a way to describe where the reader is now, for the link picker. */
    onHere: ((String) -> TrailStop) -> Unit = {},
) {
    val drawingColor = if (viewModel.tool == Tool.HIGHLIGHTER) viewModel.highlighterColor else viewModel.penColor
    val drawingWidth = when (viewModel.tool) {
        Tool.HIGHLIGHTER -> viewModel.highlighterWidth
        Tool.INK_PEN -> viewModel.inkPenWidth
        Tool.CALLIGRAPHY -> viewModel.calligraphyWidth
        else -> viewModel.penWidth
    }
    // The camera: the pages are laid out once, at their normal size, and shown through it. [zoom]
    // scales the view and [panX] (layout pixels) is how far it is moved sideways. A pinch or a pan
    // only changes these two numbers, so nothing is laid out again: no jitter, nothing cut off,
    // and nothing moves when the fingers lift. Up and down is the page list's own scroll.
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    // Up and down beyond where the page list can scroll (its top, its end, or a short note zoomed
    // out), so the view never feels stuck: positive moves the pages up.
    var panY by remember { mutableFloatStateOf(0f) }
    // Full screen opens at the fitting size on the page being looked at, and gives the zoom back after.
    var zoomBeforeFullScreen by rememberSaveable { mutableFloatStateOf(1f) }
    // A zoom saved by an older version may be outside today's range.
    LaunchedEffect(Unit) { zoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM) }
    // The zoom once a pinch has stopped: ink and pictures are then drawn sharp at it.
    var settledZoom by remember { mutableFloatStateOf(zoom) }
    LaunchedEffect(zoom) {
        kotlinx.coroutines.delay(150)
        settledZoom = zoom
    }
    // How far each page is turned (degrees clockwise, by page id) by a two-finger twist on it, like
    // turning paper to write. Only the page twisted turns; it is a way of looking, not saved.
    val pageRotations = remember { mutableStateMapOf<String, Float>() }
    var viewport by remember { mutableStateOf(Rect.Zero) }
    // The width of the laid-out pages (layout pixels), from the layout, and how much of it can be
    // panned to: the page's own width, or (once a page is turned) all of it.
    var contentWidthPx by remember { mutableFloatStateOf(0f) }
    var reachWidthPx by remember { mutableFloatStateOf(0f) }
    val currentArranging by androidx.compose.runtime.rememberUpdatedState(arranging)
    fun viewZoom() = if (currentArranging) 1f else zoom
    // Arranging, the pages fill the width: no sideways position.
    fun viewPanX() = if (currentArranging) 0f else panX
    fun viewPanY() = if (currentArranging) 0f else panY
    // Moves the view up or down by [d] layout pixels: an offset past the list's ends is taken back
    // first, then the list scrolls, and what it can't scroll becomes the offset (up to half a view).
    fun panVertical(d: Float) {
        var rest = d
        if (panY != 0f && (rest > 0f) != (panY > 0f)) {
            val back = if (abs(rest) >= abs(panY)) -panY else rest
            panY += back
            rest -= back
        }
        if (rest != 0f) rest -= gridState.dispatchRawDelta(rest)
        val limit = viewport.height / viewZoom() / 2
        panY = (panY + rest).coerceIn(-limit, limit)
    }
    // Sideways: pages narrower than the view stay centred; wider ones pan between their edges.
    fun clampPan(x: Float, z: Float = viewZoom()): Float {
        val visible = viewport.width / z
        val centre = (contentWidthPx - visible) / 2
        if (reachWidthPx <= visible) return centre
        val half = (reachWidthPx - visible) / 2
        return x.coerceIn(centre - half, centre + half)
    }
    val panState = androidx.compose.foundation.gestures.rememberScrollableState { delta ->
        val before = panX
        panX = clampPan(panX + delta)
        panX - before
    }
    // Zooms by [factor] around [focus] (root pixels): the page point under the fingers stays there.
    fun zoomAround(factor: Float, focus: Offset) {
        val old = zoom
        val new = (old * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (new == old) return
        val f = focus - viewport.topLeft
        val x = panX + f.x / old
        zoom = new
        panX = clampPan(x - f.x / new, new)
        panVertical(f.y / old - f.y / new)
    }
    // Fingers moving the view (screen pixels).
    fun pan(dx: Float, dy: Float) {
        panVertical(dy / viewZoom())
        panX = clampPan(panX + dx / viewZoom())
    }
    LaunchedEffect(fullScreen) {
        val page = gridState.firstVisibleItemIndex
        if (fullScreen) {
            zoomBeforeFullScreen = zoom
            zoom = 1f
        } else {
            zoom = zoomBeforeFullScreen
        }
        gridState.scrollToItem(page)
        panY = 0f
        withFrameNanos { } // once laid out for the new mode
        // Pages wider than the view start centred.
        panX = clampPan((contentWidthPx - viewport.width / viewZoom()) / 2)
    }
    val scope = rememberCoroutineScope()
    // The page opened from Arrange pages. Scrolled to once the pages are laid out at full size:
    // scrolling straight away would use the zoomed-out layout, where every page fits on screen
    // and the scroll is clamped to the first page.
    var zoomedToPage by remember { mutableStateOf<Int?>(null) }
    // A page picked up in the overview to move: its id, the finger (root pixels) and where on the page it was held.
    var draggingPage by remember { mutableStateOf<String?>(null) }
    // A sticky note being moved or resized: the page draws it where the finger has it.
    var liveSticky by remember { mutableStateOf<com.gerko.fourenote.data.ink.StickyNote?>(null) }
    var dragFinger by remember { mutableStateOf(Offset.Zero) }
    var dragGrab by remember { mutableStateOf(Offset.Zero) }
    val pageBounds = remember { mutableStateMapOf<String, Rect>() }
    var gridBounds by remember { mutableStateOf(Rect.Zero) }
    // Moves the held page into the place of the page under the finger.
    fun dragPageTo(finger: Offset) {
        dragFinger = finger
        val dragged = draggingPage ?: return
        val visible = gridState.layoutInfo.visibleItemsInfo.mapTo(HashSet()) { it.key }
        val target = pageBounds.entries.firstOrNull { (id, bounds) -> id != dragged && id in visible && finger in bounds }?.key ?: return
        viewModel.movePage(viewModel.pages.indexOfFirst { it.id == dragged }, viewModel.pages.indexOfFirst { it.id == target })
    }
    // While a page is held near the top or bottom edge, scroll the pages along.
    val edgeDensity = LocalDensity.current
    LaunchedEffect(draggingPage) {
        if (draggingPage == null) return@LaunchedEffect
        val edge = with(edgeDensity) { 72.dp.toPx() }
        val speed = with(edgeDensity) { 12.dp.toPx() }
        while (true) {
            withFrameNanos { }
            val dy = when {
                dragFinger.y < gridBounds.top + edge -> -speed
                dragFinger.y > gridBounds.bottom - edge -> speed
                else -> 0f
            }
            if (dy != 0f && gridState.scrollBy(dy) != 0f) dragPageTo(dragFinger)
        }
    }
    LaunchedEffect(zoomedToPage) {
        val index = zoomedToPage ?: return@LaunchedEffect
        gridState.scrollToItem(index)
        panY = 0f
        withFrameNanos { }
        panX = clampPan(panX)
        zoomedToPage = null
    }

    // Links: the badge just followed pulses, and a page jumped to is ringed, both for a moment.
    var flashLinkId by remember { mutableStateOf<String?>(null) }
    var flashPageId by remember { mutableStateOf<String?>(null) }
    // The objects a link points to, ringed for a moment after following it: page id and object ids.
    var flashObjects by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    // Objects to zoom to once the pages are laid out; the page list does it, since it owns the camera.
    var zoomToObjects by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    LaunchedEffect(flashLinkId) {
        if (flashLinkId == null) return@LaunchedEffect
        kotlinx.coroutines.delay(1600)
        flashLinkId = null
    }
    LaunchedEffect(flashPageId) {
        if (flashPageId == null) return@LaunchedEffect
        kotlinx.coroutines.delay(1600)
        flashPageId = null
    }
    LaunchedEffect(flashObjects) {
        if (flashObjects == null) return@LaunchedEffect
        // Longer than the page ring: opening another note takes part of it.
        kotlinx.coroutines.delay(2500)
        flashObjects = null
    }
    // A link that leads nowhere, with the page it is on, to offer removing it.
    var broken by remember { mutableStateOf<BrokenLink?>(null) }
    // Where the reader is now, to come back to after following a link.
    fun here(linkId: String) = TrailStop(
        viewModel.noteId,
        viewModel.title.ifBlank { "Untitled" },
        gridState.firstVisibleItemIndex,
        gridState.firstVisibleItemScrollOffset,
        zoom,
        panX,
        panY,
        linkId,
    )
    SideEffect { onHere(::here) }
    fun follow(pageId: String, link: com.gerko.fourenote.data.ink.PageLink) {
        scope.launch {
            when (val r = viewModel.resolve(link)) {
                is LinkResolution.SameNote -> if (r.pageIndex >= 0) {
                    LinkTrail.stops.add(here(link.id))
                    gridState.animateScrollToItem(r.pageIndex)
                    val target = viewModel.pages[r.pageIndex]
                    if (r.objectIds.isEmpty()) {
                        flashPageId = target.id
                    } else {
                        flashObjects = target.id to r.objectIds
                        zoomToObjects = target.id to r.objectIds
                    }
                }
                is LinkResolution.OtherNote -> {
                    LinkTrail.stops.add(here(link.id))
                    LinkTrail.openPage = r.pageId?.let { OpenTarget(r.noteId, it, r.objectIds) }
                    onOpenNote(r.noteId)
                }
                else -> broken = BrokenLink(pageId, link, r)
            }
        }
    }
    // Puts the reader back where they were: page, scroll, zoom, and the badge they tapped pulses.
    suspend fun applyStop(stop: TrailStop) {
        zoom = stop.zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        gridState.scrollToItem(stop.pageIndex, stop.scrollOffset)
        panY = stop.panY
        withFrameNanos { }
        panX = clampPan(stop.panX)
        flashLinkId = stop.linkId
    }
    fun goBack() {
        val stop = LinkTrail.stops.removeLastOrNull() ?: return
        if (stop.noteId == viewModel.noteId) {
            scope.launch { applyStop(stop) }
        } else {
            // The note left behind is closed on the way; its editor puts the reader back.
            LinkTrail.restore = stop
            onBack()
        }
    }
    // Coming back to this note from another: the pages must be laid out before the spot is restored.
    LaunchedEffect(viewModel.loaded, LinkTrail.restore) {
        val stop = LinkTrail.restore
        if (viewModel.loaded && stop != null && stop.noteId == viewModel.noteId) {
            withFrameNanos { }
            applyStop(stop)
            // Cleared only now: clearing it restarts this effect, which would cancel the restore.
            LinkTrail.restore = null
        }
    }
    // A link followed into this note's page: scroll there once the pages are there.
    LaunchedEffect(viewModel.loaded) {
        val open = LinkTrail.openPage ?: return@LaunchedEffect
        if (!viewModel.loaded || open.noteId != viewModel.noteId) return@LaunchedEffect
        LinkTrail.openPage = null
        val index = viewModel.pages.indexOfFirst { it.id == open.pageId }
        if (index >= 0) {
            withFrameNanos { }
            gridState.scrollToItem(index)
            val page = viewModel.pages[index]
            if (open.objectIds.isEmpty()) {
                flashPageId = open.pageId
            } else {
                flashObjects = open.pageId to open.objectIds
                zoomToObjects = open.pageId to open.objectIds
            }
        }
    }
    androidx.activity.compose.BackHandler(enabled = LinkTrail.stops.isNotEmpty()) { goBack() }

    // Picking the objects a link points to, in the note it will point to.
    val picking = LinkPick.pending?.takeIf { it.targetNoteId == viewModel.noteId }
    LaunchedEffect(picking) {
        if (picking != null && viewModel.tool != Tool.LASSO) viewModel.selectTool(Tool.LASSO)
    }
    fun cancelPick() {
        val p = LinkPick.pending ?: return
        LinkPick.pending = null
        viewModel.clearSelection()
        if (p.sourceNoteId == viewModel.noteId) {
            scope.launch { applyStop(p.back) }
        } else {
            LinkTrail.restore = p.back
            onBack()
        }
    }
    fun finishPick() {
        val p = LinkPick.pending ?: return
        val target = viewModel.pickedTarget() ?: return
        viewModel.clearSelection()
        if (p.sourceNoteId == viewModel.noteId) {
            viewModel.addLink(p.sourcePageId, p.strokeIds, p.imageIds, target)
            LinkPick.pending = null
            scope.launch { applyStop(p.back) }
        } else {
            LinkPick.done = p to target
            LinkPick.pending = null
            LinkTrail.restore = p.back
            onBack()
        }
    }
    // Declared after the trail's handler, so it wins while picking.
    androidx.activity.compose.BackHandler(enabled = picking != null) { cancelPick() }
    // The target note's editor went away without Done or Cancel (e.g. it was locked): don't leave a stale pick.
    DisposableEffect(picking) {
        onDispose { if (picking != null && LinkPick.pending === picking) LinkPick.pending = null }
    }
    // Back in the source note: add the link the target note picked.
    LaunchedEffect(viewModel.loaded, LinkPick.done) {
        val (p, target) = LinkPick.done ?: return@LaunchedEffect
        if (!viewModel.loaded || p.sourceNoteId != viewModel.noteId) return@LaunchedEffect
        LinkPick.done = null
        viewModel.addLink(p.sourcePageId, p.strokeIds, p.imageIds, target)
    }

    // Bars for a selection sit just under the floating tool bar.
    val barsTop = if (fullScreen) 12.dp else TOOLBAR_SPACE
    val currentOnTwoFingerHold by androidx.compose.runtime.rememberUpdatedState(onTwoFingerHold)
    // Where a finger was held still beside the pages, to center the page nearest to it.
    var centerNear by remember { mutableStateOf<Offset?>(null) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // The window the pages show in stays put while they are pinched bigger or smaller.
            .clipToBounds()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .onGloballyPositioned { viewport = it.boundsInRoot() }
            // Two fingers held still beside the pages open the dial too (on a page, the page does it).
            // Only watches: scrolling there carries on as usual.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = true)
                    var held = emptyMap<androidx.compose.ui.input.pointer.PointerId, Offset>()
                    var since = 0L
                    // One finger held still: center the nearest page.
                    var oneStill = true
                    while (true) {
                        val wait = if (held.isEmpty()) {
                            if (oneStill) first.uptimeMillis + DIAL_HOLD_MS - currentEvent.changes.first().uptimeMillis else Long.MAX_VALUE
                        } else {
                            since + DIAL_HOLD_MS - System.currentTimeMillis()
                        }
                        val event = withTimeoutOrNull(wait.coerceAtLeast(1)) { awaitPointerEvent() }
                        if (event == null && held.isEmpty()) {
                            centerNear = first.position
                            while (currentEvent.changes.any { it.pressed }) awaitPointerEvent().changes.forEach { it.consume() }
                            break
                        }
                        if (event == null) {
                            val fingers = currentEvent.changes.filter { it.pressed }
                            val middle = fingers.fold(Offset.Zero) { sum, it -> sum + it.position } / fingers.size.toFloat()
                            currentOnTwoFingerHold(viewport.topLeft + middle)
                            while (currentEvent.changes.any { it.pressed }) awaitPointerEvent().changes.forEach { it.consume() }
                            break
                        }
                        if (event.changes.any { it.isConsumed }) break
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size > 1 || pressed.any { (it.position - first.position).getDistance() > DIAL_HOLD_SLOP_PX }) oneStill = false
                        when {
                            pressed.size >= 2 && held.isEmpty() -> {
                                held = pressed.associate { it.id to it.position }
                                since = System.currentTimeMillis()
                            }
                            pressed.size >= 2 -> if (pressed.any { p -> held[p.id]?.let { (p.position - it).getDistance() > DIAL_HOLD_SLOP_PX } != false }) break
                            held.isNotEmpty() -> break
                        }
                    }
                }
            },
    ) {
        // The pages scroll in one column: at 100% a page fills the width (up to a comfortable
        // maximum); zooming in makes it wider than the screen, panned sideways; zooming out keeps it
        // centred. Full screen: one page per screen, as large as fits, centred, with no gaps or footers.
        // Arranging pages: all of them small in a grid instead.
        fun fitWidth(aspect: Float) = minOf(maxWidth, maxHeight * aspect)
        val arrange = arranging
        val fullWidth = if (fullScreen && !arrange) fitWidth(A4_ASPECT) else minOf(maxWidth - 32.dp, MAX_PAGE_WIDTH)
        // Laid out once at normal size (small while arranging); the camera does the zooming.
        val pageWidth = if (arrange) fullWidth * ARRANGE_ZOOM else fullWidth
        val columns = if (arrange) maxOf(1, ((maxWidth - 16.dp) / (pageWidth + 16.dp)).toInt()) else 1
        val fullScreen = fullScreen && !arrange
        // Arranging, a tap on a page opens it at its place.
        fun openFromArrange(index: Int) {
            onArranged()
            zoomedToPage = index
        }
        // The pages are laid out in a band as wide as a page's diagonal, so a page turned to any
        // angle fits inside it and nothing of it is cut off; untouched pages sit in its middle.
        val pageHeight = pageWidth / A4_ASPECT
        val diagonal = androidx.compose.ui.unit.Dp(kotlin.math.hypot(pageWidth.value, pageHeight.value))
        val gridWidth = if (arrange) maxWidth else maxOf(maxWidth, diagonal + 32.dp)
        val anyTurned by remember { derivedStateOf { pageRotations.values.any { it != 0f } } }
        val reachWidth = if (anyTurned) gridWidth else minOf(gridWidth, maxOf(maxWidth, pageWidth + 32.dp))
        val density = LocalDensity.current
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        SideEffect {
            contentWidthPx = with(density) { gridWidth.toPx() }
            reachWidthPx = with(density) { reachWidth.toPx() }
        }
        // Where page [index] is on screen, in pixels from the top left of the view.
        fun pageRect(index: Int): Rect? {
            val item = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return null
            val page = viewModel.pages.getOrNull(index) ?: return null
            val aspect = page.width / page.height
            val w = with(density) { (if (fullScreen) fitWidth(aspect) else pageWidth).toPx() }
            val h = w / aspect
            // Item offsets start inside the grid's padding (the room for the tool bar and the side margin).
            val padStart = if (fullScreen) 0f else with(density) { 16.dp.toPx() }
            val left = padStart + item.offset.x + (item.size.width - w) / 2 - viewPanX()
            val centered = fullScreen || !arrange
            val top = gridState.layoutInfo.beforeContentPadding + item.offset.y - viewPanY() + if (centered) (item.size.height - h) / 2 else 0f
            val z = viewZoom()
            return Rect(left * z, top * z, (left + w) * z, (top + h) * z)
        }
        // Glides page [index] to the middle of the screen (only ever on request: a finger held still).
        fun centerPage(index: Int) {
            val r = pageRect(index) ?: return
            val by = (r.center - Offset(viewport.width / 2, viewport.height / 2)) / viewZoom()
            scope.launch { panState.animateScrollBy(by.x) }
            scope.launch { gridState.animateScrollBy(by.y) }
        }
        // Zooms and pans so the objects' box is centered and takes at most 70% of the view. The
        // pages are laid out at 100%, so this only moves the camera; the box is in page units.
        suspend fun showObjects(page: PageState, ids: List<String>) {
            val box = com.gerko.fourenote.data.ink.objectBounds(ids, page.strokes, page.texts, page.images) ?: return
            val index = viewModel.pages.indexOfFirst { it.id == page.id }
            if (index < 0) return
            withFrameNanos { }
            val rect = pageRect(index) ?: return
            // The page's screen rect already holds the zoom, so this is the box as seen now.
            val unit = rect.width / page.width
            val boxWidthPx = (box[2] - box[0]) * unit
            val boxHeightPx = (box[3] - box[1]) * unit
            val fit = zoom * minOf(
                viewport.width * 0.7f / boxWidthPx.coerceAtLeast(1f),
                viewport.height * 0.7f / boxHeightPx.coerceAtLeast(1f),
            )
            // Already in view at this zoom: keep it, just center the box.
            val target = if (fit >= zoom) zoom else fit.coerceIn(MIN_ZOOM, minOf(MAX_ZOOM, 2f))
            zoom = target
            withFrameNanos { }
            val after = pageRect(index) ?: return
            val boxCenter = Offset(
                after.left + (box[0] + box[2]) / 2 * (after.width / page.width),
                after.top + (box[1] + box[3]) / 2 * (after.height / page.height),
            )
            val by = (boxCenter - Offset(viewport.width / 2, viewport.height / 2)) / viewZoom()
            scope.launch { panState.animateScrollBy(by.x) }
            scope.launch { gridState.animateScrollBy(by.y) }
        }
        // A link followed to objects: zoom to them once the pages are laid out.
        LaunchedEffect(zoomToObjects) {
            val (pageId, ids) = zoomToObjects ?: return@LaunchedEffect
            zoomToObjects = null
            val page = viewModel.pages.firstOrNull { it.id == pageId } ?: return@LaunchedEffect
            showObjects(page, ids)
        }
        // A finger held still beside the pages centers the page nearest to it.
        LaunchedEffect(centerNear) {
            val at = centerNear ?: return@LaunchedEffect
            centerNear = null
            gridState.layoutInfo.visibleItemsInfo.filter { it.index < viewModel.pages.size }
                .mapNotNull { info -> pageRect(info.index)?.let { info.index to it } }
                .minByOrNull { (_, r) -> (r.center - at).getDistance() }
                ?.let { (index, _) -> centerPage(index) }
        }
        val pageWidthPx = with(density) { pageWidth.toPx() }
        // Pictures are loaded sharp for the zoom once a pinch stops, in size steps, and never huge.
        val renderZoom = if (arrange) 1f else settledZoom
        val backgroundWidthPx = (((pageWidthPx * renderZoom).coerceAtMost(4096f) / 128f).roundToInt().coerceAtLeast(1)) * 128

        Box(
            Modifier
                .fillMaxSize()
                // The camera: everything inside is drawn scaled by the zoom...
                .graphicsLayer {
                    val z = viewZoom()
                    scaleX = z
                    scaleY = z
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                // ...and laid out over the area that then fills the window exactly (bigger when
                // zoomed out, so the pages around are there too). Read here, a zoom change only
                // measures again, it doesn't rebuild anything.
                .layout { measurable, constraints ->
                    val z = viewZoom()
                    val inner = measurable.measure(
                        androidx.compose.ui.unit.Constraints.fixed(
                            (constraints.maxWidth / z).roundToInt().coerceAtLeast(1),
                            (constraints.maxHeight / z).roundToInt().coerceAtLeast(1),
                        ),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) { inner.place(0, 0) }
                }
                // One finger (where fingers don't write) drags sideways too.
                .scrollable(panState, androidx.compose.foundation.gestures.Orientation.Horizontal, reverseDirection = true)
                // Sideways position, without clipping: what lies beyond is still drawn.
                .layout { measurable, constraints ->
                    val inner = measurable.measure(constraints.copy(minWidth = 0, maxWidth = androidx.compose.ui.unit.Constraints.Infinity))
                    layout(constraints.maxWidth, constraints.maxHeight) { inner.place(-viewPanX().roundToInt(), -viewPanY().roundToInt()) }
                },
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(if (fullScreen) 0.dp else 16.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(if (fullScreen) 0.dp else 12.dp),
                // Room at the top for the floating tool bar.
                // Room at the bottom to scroll the last page and the add-pages handle above the navigation bar.
                contentPadding = if (fullScreen) PaddingValues(0.dp) else PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp + navBarBottom, top = TOOLBAR_SPACE),
                modifier = Modifier.width(gridWidth).fillMaxHeight().onGloballyPositioned { gridBounds = it.boundsInRoot() },
            ) {
                itemsIndexed(viewModel.pages, key = { _, page -> page.id }) { index, page ->
                    // Grid cells are stretched to fill their column; keep the page at its zoomed
                    // width so the ink's scale and the page's on-screen size always agree.
                    val dragged = draggingPage == page.id
                    val itemWidth = if (fullScreen) fitWidth(page.width / page.height) else pageWidth
                    val itemWidthPx = with(density) { itemWidth.toPx() }
                    // Each page sits in a square as wide as its diagonal, in the middle, so it fits whole at
                    // any angle: no corner is cut off or lies over another page, and starting to turn a
                    // page doesn't move it. Arranging packs the pages tight instead.
                    val turned = !arrange
                    val turnedSide = androidx.compose.ui.unit.Dp(kotlin.math.hypot(itemWidth.value, itemWidth.value * page.height / page.width))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            // Each page gets a whole screen in full screen, centered in it.
                            .then(if (fullScreen) Modifier.heightIn(min = this@BoxWithConstraints.maxHeight) else Modifier)
                            .then(if (turned) Modifier.heightIn(min = turnedSide) else Modifier)
                            .zIndex(if (dragged) 1f else 0f)
                            // The other pages slide out of the way of a page being moved.
                            .animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = if (dragged) null else spring()),
                        contentAlignment = if (fullScreen || turned) Alignment.Center else Alignment.TopCenter,
                    ) {
                    Column(
                        Modifier
                            // Before the graphics layer, so the bounds are the page's place in the grid, not where it's dragged.
                            .onGloballyPositioned { pageBounds[page.id] = it.boundsInRoot() }
                            .graphicsLayer {
                                if (dragged) {
                                    val home = pageBounds[page.id]?.topLeft ?: Offset.Zero
                                    translationX = dragFinger.x - dragGrab.x - home.x
                                    translationY = dragFinger.y - dragGrab.y - home.y
                                    scaleX = 1.05f
                                    scaleY = 1.05f
                                    shadowElevation = 16.dp.toPx()
                                }
                            }
                            .width(itemWidth),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Reloaded at a new size after zooming; the old copy stays up until the new one is
                        // ready, so the page never flashes blank.
                        var background by remember(page.id) { mutableStateOf<ImageBitmap?>(null) }
                        LaunchedEffect(page.background, backgroundWidthPx) {
                            background = page.background?.let { backgrounds.load(viewModel.noteId, it, backgroundWidthPx)?.asImageBitmap() }
                        }
                        val imageFiles = page.images.map { it.file }.toSet()
                        var imageBitmaps by remember(page.id) { mutableStateOf(emptyMap<String, ImageBitmap>()) }
                        LaunchedEffect(imageFiles, backgroundWidthPx) {
                            imageBitmaps = imageFiles.mapNotNull { file ->
                                backgrounds.load(viewModel.noteId, PageBackground.Image(file), backgroundWidthPx)
                                    ?.let { file to it.asImageBitmap() }
                            }.toMap()
                        }
                        val visibleStrokes by remember(page) {
                            derivedStateOf {
                                InkDocument(layers = viewModel.layers.toList()).visibleStrokes(page.toPage())
                            }
                        }
                        // The page: ink, with its text boxes over it.
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(page.width / page.height)
                                // Input inside follows the turn, so writing on a turned page lands where the pen is.
                                .graphicsLayer { rotationZ = if (arrange) 0f else pageRotations[page.id] ?: 0f }
                                .then(if (fullScreen) Modifier else Modifier.shadow(2.dp))
                                .then(if (flashPageId == page.id) Modifier.border(3.dp, MaterialTheme.colorScheme.primary) else Modifier),
                        ) {
                        InkCanvas(
                            strokes = visibleStrokes,
                            scale = itemWidthPx / page.width,
                            renderZoom = renderZoom,
                            background = background,
                            darkPaper = darkPaper,
                            selection = if (viewModel.selectionPageId == page.id) viewModel.selection else emptySet(),
                            paper = viewModel.paper,
                            tool = viewModel.tool,
                            color = drawingColor,
                            width = drawingWidth,
                            stylusOnly = viewModel.stylusOnly,
                            smoothing = viewModel.smoothing,
                            nibDegrees = viewModel.nibDegrees,
                            onStroke = { stroke, held -> viewModel.addStroke(page.id, stroke, held) },
                            onEraseStart = viewModel::beginErase,
                            onErase = { x, y -> viewModel.eraseAt(page.id, x, y) },
                            onEraseEnd = viewModel::endErase,
                            onLasso = { viewModel.selectWithLasso(page.id, it) },
                            onTransformSelection = viewModel::transformSelection,
                            onScroll = ::pan,
                            onFingerHold = { centerPage(index) },
                            onRotate = { if (!arrange) pageRotations[page.id] = (((pageRotations[page.id] ?: 0f) + it) % 360f + 360f) % 360f },
                            onZoom = { factor, focus -> if (!arrange) zoomAround(factor, focus) },
                            onTwoFingerHold = onTwoFingerHold,
                            primaryButtonAction = viewModel.primaryButtonAction,
                            secondaryButtonAction = viewModel.secondaryButtonAction,
                            onPenButtonClick = onPenButton,
                            onPenSignal = { viewModel.lastPenSignal = it },
                            onMultiFingerTap = onMultiFingerTap,
                            // Zoomed out below 100%, fingers scroll, tap and move pages; only the pen writes.
                            overview = arrange,
                            onFingerTap = { if (arrange) openFromArrange(index) },
                            onPageDragStart = { finger ->
                                viewModel.beginPageMove()
                                dragGrab = finger - (pageBounds[page.id]?.topLeft ?: finger)
                                dragFinger = finger
                                draggingPage = page.id
                            },
                            onPageDrag = ::dragPageTo,
                            loop = viewModel.recentLoop?.takeIf { it.first == page.id }?.second,
                            onLoopHold = { viewModel.loopToSelection(page.id) },
                            onPageDragEnd = {
                                draggingPage = null
                                viewModel.endPageMove()
                            },
                            styleFor = viewModel::styleFor,
                            placement = page.placement,
                            adjustingBackground = viewModel.adjustingPageId == page.id,
                            onPlacementChange = { viewModel.setBackgroundPlacement(page.id, it) },
                            invertBackground = page.invertBackground,
                            images = page.images,
                            imageBitmaps = imageBitmaps,
                            selectedImageId = viewModel.selectedImage?.takeIf { it.first == page.id }?.second,
                            penAndFingerSelect = viewModel.penAndFingerSelect,
                            onImageSelect = { viewModel.selectImage(page.id, it) },
                            onImageChange = { viewModel.updateImage(page.id, it) },
                            onTextTap = { x, y ->
                                if (viewModel.tool == Tool.STICKY) viewModel.placeSticky(page.id, x, y) else viewModel.tapWithText(page.id, x, y)
                            },
                            pointedNib = viewModel.pointedNib,
                            stickies = page.stickies.map { sticky -> liveSticky?.takeIf { it.id == sticky.id } ?: sticky },
                            modifier = Modifier.matchParentSize(),
                        )
                        TextBoxLayer(
                            texts = page.texts,
                            pxPerUnit = itemWidthPx / page.width,
                            editingId = viewModel.editingText?.takeIf { it.first == page.id && viewModel.editingSticky == null }?.second,
                            // The Text tool and Select both pick up typed text with a tap.
                            textTool = viewModel.tool == Tool.TEXT || viewModel.tool == Tool.LASSO,
                            onEdit = { viewModel.editText(page.id, it) },
                            onChange = { viewModel.updateText(page.id, it) },
                            onDelete = viewModel::deleteEditingText,
                        )
                        StickyLayer(
                            stickies = page.stickies,
                            pageWidth = page.width,
                            pageHeight = page.height,
                            pxPerUnit = itemWidthPx / page.width,
                            selectedId = viewModel.selectedSticky?.takeIf { it.first == page.id }?.second,
                            interactive = viewModel.tool == Tool.STICKY || viewModel.tool == Tool.LASSO,
                            onSelect = { viewModel.selectSticky(page.id, it) },
                            onChange = { viewModel.updateSticky(page.id, it) },
                            onToggleFold = { viewModel.toggleFold(page.id, it) },
                            onDelete = viewModel::deleteSelectedSticky,
                            onLive = { liveSticky = it },
                            editingText = viewModel.editingText
                                ?.takeIf { it.first == page.id }
                                ?.let { (_, textId) -> viewModel.editingSticky?.let { it to textId } },
                            textTool = viewModel.tool == Tool.TEXT || viewModel.tool == Tool.LASSO,
                            onEditText = { stickyId, textId -> viewModel.editStickyText(page.id, stickyId, textId) },
                            onTextChange = { stickyId, box -> viewModel.updateStickyText(page.id, stickyId, box) },
                            onTextDelete = viewModel::deleteEditingText,
                        )
                        // Badges are in the way while arranging pages, which is only for moving them.
                        if (!arrange) {
                            LinkBadges(
                                page = page,
                                pxPerUnit = itemWidthPx / page.width,
                                flashLinkId = flashLinkId,
                                // While picking, a badge would follow a link instead of picking objects.
                                onFollow = { if (picking == null) follow(page.id, it) },
                                onRemove = { viewModel.removeLink(page.id, it.id) },
                                modifier = Modifier.matchParentSize(),
                            )
                        }
                        flashObjects?.takeIf { it.first == page.id }?.let { (_, ids) ->
                            ObjectHighlight(
                                ids = ids,
                                page = page,
                                pxPerUnit = itemWidthPx / page.width,
                                modifier = Modifier.matchParentSize(),
                            )
                        }
                        }
                        if (!fullScreen) PageFooter(
                            number = index + 1,
                            count = viewModel.pages.size,
                            hasImage = page.background != null,
                            inverted = page.invertBackground,
                            onInvert = { viewModel.setBackgroundInverted(page.id, it) },
                            onInvertAll = { viewModel.setAllBackgroundsInverted(it) },
                            onAdjustImage = { viewModel.startAdjustingBackground(page.id) },
                            onFitImage = { viewModel.setBackgroundPlacement(page.id, BackgroundPlacement()) },
                            onAddAfter = { viewModel.addBlankPage(afterPageId = page.id) },
                            onClear = { viewModel.clearPage(page.id) },
                            onDelete = { viewModel.deletePage(page.id) },
                        )
                    }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AddPagesHandle(addPages)
                }
            }
        }
        // While the pages are turned: how far, and a tap to straighten them.
        // The turned page on screen, if any.
        val turnedPage by remember {
            derivedStateOf {
                gridState.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { info ->
                    viewModel.pages.getOrNull(info.index)?.id?.takeIf { (pageRotations[it] ?: 0f) != 0f }
                }
            }
        }
        if (!arrange) turnedPage?.let { id ->
            AssistChip(
                // Turns smoothly to the nearest of 0°, 45°, 90°, 135°…; from one of those, all the way straight.
                onClick = {
                    val from = pageRotations[id] ?: 0f
                    val to = straightenTarget(from)
                    // The short way round.
                    val delta = ((to - from + 540f) % 360f) - 180f
                    scope.launch {
                        androidx.compose.animation.core.animate(0f, delta, animationSpec = androidx.compose.animation.core.tween(220)) { v, _ ->
                            pageRotations[id] = ((from + v) % 360f + 360f) % 360f
                        }
                        if (to == 0f) pageRotations.remove(id) else pageRotations[id] = to
                    }
                },
                // The angle is read in here only, so a twist redraws just this text, not the pages.
                label = {
                    val now = pageRotations[id] ?: 0f
                    val shown = now.roundToInt().let { if (it > 180) it - 360 else it }
                    val target = straightenTarget(now).roundToInt().let { if (it > 180) it - 360 else it }
                    Text(if (target == 0) "$shown° · straighten" else "$shown° · to $target°")
                },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null) },
                modifier = Modifier.align(Alignment.BottomStart).windowInsetsPadding(WindowInsets.navigationBars).padding(12.dp),
            )
        }
        // Hidden in full screen; pinching still zooms.
        if (arrange) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
                    Text("Hold and drag a page to move it · tap one to open it", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onArranged) { Text("Done") }
                }
            }
        }
        if (!fullScreen && !arrange) {
            ZoomControl(
                zoom = zoom,
                onZoom = { target -> zoomAround(target / zoom, viewport.center) },
                modifier = Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.navigationBars).padding(12.dp),
            )
        }
        viewModel.adjustingPageId?.let { pageId ->
            AdjustImageBar(
                onFit = { viewModel.setBackgroundPlacement(pageId, BackgroundPlacement()) },
                onDone = viewModel::finishAdjustingBackground,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = barsTop),
            )
        }
        if (picking != null) {
            PickObjectsBar(
                canFinish = viewModel.pickedTarget() != null,
                onCancel = ::cancelPick,
                onDone = ::finishPick,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = barsTop),
            )
        } else if (viewModel.selection.isNotEmpty()) {
            SelectionBar(viewModel, darkPaper, onLink = onLink, modifier = Modifier.align(Alignment.TopCenter).padding(top = barsTop))
        }
        if (picking == null) viewModel.selectedImageObject?.let { image ->
            ImageBar(viewModel, image, onLink = onLink, modifier = Modifier.align(Alignment.TopCenter).padding(top = barsTop))
        }
        // The way back along followed links, while there is one.
        if (LinkTrail.stops.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = ::goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Back to ${LinkTrail.stops.last().title}")
                    }
                    IconButton(onClick = { LinkTrail.clear() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
            }
        }
        broken?.let { b ->
            BrokenLinkDialog(
                broken = b,
                onOpenNote = {
                    broken = null
                    // The page without the objects: the link still leads somewhere.
                    val objects = b.resolution as? LinkResolution.ObjectsNotFound
                    if (objects != null) {
                        val index = viewModel.pages.indexOfFirst { it.id == objects.pageId }
                        if (objects.noteId == viewModel.noteId) {
                            if (index >= 0) {
                                LinkTrail.stops.add(here(b.link.id))
                                scope.launch {
                                    gridState.animateScrollToItem(index)
                                    flashPageId = objects.pageId
                                }
                            }
                        } else {
                            LinkTrail.stops.add(here(b.link.id))
                            LinkTrail.openPage = OpenTarget(objects.noteId, objects.pageId)
                            onOpenNote(objects.noteId)
                        }
                        return@BrokenLinkDialog
                    }
                    val noteId = (b.resolution as? LinkResolution.PageNotFound)?.noteId
                    if (noteId != null && noteId != viewModel.noteId) {
                        LinkTrail.stops.add(here(b.link.id))
                        LinkTrail.openPage = null
                        onOpenNote(noteId)
                    }
                },
                onRemove = {
                    broken = null
                    viewModel.removeLink(b.pageId, b.link.id)
                },
                onDismiss = { broken = null },
            )
        }
    }
}

/** Floats over the pages while the objects a link points to are being picked. */
@Composable
private fun PickObjectsBar(canFinish: Boolean, onCancel: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp, shadowElevation = 6.dp, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Icon(Icons.Filled.SelectAll, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Lasso what the link points to, then Done", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onCancel) { Text("Cancel") }
            TextButton(onClick = onDone, enabled = canFinish) { Text("Done") }
        }
    }
}

/** Rings the objects a link points to for a moment, so the reader sees where they landed. */
@Composable
private fun ObjectHighlight(ids: List<String>, page: PageState, pxPerUnit: Float, modifier: Modifier = Modifier) {
    val box = remember(ids, page.strokes.size, page.texts.size, page.images.size) {
        com.gerko.fourenote.data.ink.objectBounds(ids, page.strokes, page.texts, page.images)
    } ?: return
    val color = MaterialTheme.colorScheme.primary
    val pad = with(LocalDensity.current) { 8.dp.toPx() }
    androidx.compose.foundation.Canvas(modifier) {
        val left = box[0] * pxPerUnit - pad
        val top = box[1] * pxPerUnit - pad
        val right = box[2] * pxPerUnit + pad
        val bottom = box[3] * pxPerUnit + pad
        val corner = 8.dp.toPx()
        drawRoundRect(
            color = color.copy(alpha = 0.15f),
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()),
        )
    }
}

/** Floats over the pages while a picture is selected. */
@Composable
private fun ImageBar(viewModel: NoteEditorViewModel, image: PageImage, onLink: () -> Unit, modifier: Modifier = Modifier) {
    var cropping by remember { mutableStateOf(false) }
    if (cropping) {
        CropDialog(
            file = viewModel.imageFile(image.file),
            initial = androidx.compose.ui.geometry.Rect(image.cropLeft, image.cropTop, image.cropRight, image.cropBottom),
            onDone = { kept ->
                cropping = false
                viewModel.cropSelectedImage(kept.left, kept.top, kept.right, kept.bottom)
            },
            onCancel = { cropping = false },
        )
    }
    Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp, shadowElevation = 6.dp, modifier = modifier) {
        FlowRow(
            itemVerticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            // A locked picture only offers unlocking.
            TextButton(onClick = viewModel::toggleSelectedImageLocked) {
                Icon(if (image.locked) Icons.Filled.LockOpen else Icons.Filled.Lock, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(if (image.locked) "Unlock" else "Lock")
            }
            TextButton(onClick = onLink) {
                Icon(Icons.Filled.Link, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Link to…")
            }
            if (!image.locked) {
            TextButton(onClick = viewModel::deleteSelectedImage) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Delete")
            }
            IconButton(onClick = { viewModel.rotateSelectedImage(90f) }) {
                Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate 90 degrees")
            }
            IconButton(onClick = viewModel::duplicateSelectedImage) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate picture")
            }
            IconButton(onClick = { cropping = true }) {
                Icon(Icons.Filled.Crop, contentDescription = "Crop")
            }
            // Two kinds of inverting: smart (light and dark swap, colours stay) and negative (blue becomes yellow).
            FilterChip(
                selected = image.inverted,
                onClick = { viewModel.toggleSelectedImageInverted() },
                label = { Text("Invert") },
                leadingIcon = { Icon(Icons.Filled.InvertColors, contentDescription = null) },
            )
            FilterChip(
                selected = image.negative,
                onClick = { viewModel.toggleSelectedImageNegative() },
                label = { Text("Negative") },
            )
            IconButton(onClick = viewModel::bringSelectedImageToFront) {
                Icon(Icons.Filled.FlipToFront, contentDescription = "Bring to front")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Opacity, contentDescription = null)
                Slider(
                    value = image.opacity,
                    onValueChange = viewModel::setSelectedImageOpacity,
                    onValueChangeFinished = viewModel::finishOpacityChange,
                    valueRange = 0.05f..1f,
                    modifier = Modifier.width(140.dp),
                )
                Text("${(image.opacity * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
            }
            }
            TextButton(onClick = { viewModel.selectedImage?.let { viewModel.selectImage(it.first, null) } }) { Text("Done") }
        }
    }
}

/** − / percentage / + ; tapping the percentage returns to 100%. Pinching zooms too. */
@Composable
private fun ZoomControl(zoom: Float, onZoom: (Float) -> Unit, modifier: Modifier = Modifier) {
    // Small and slightly see-through, so it covers as little of the page as possible.
    Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 6.dp, shadowElevation = 2.dp, modifier = modifier.alpha(0.85f)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(36.dp)) {
            ZoomButton(Icons.Filled.ZoomOut, "Zoom out") { onZoom(zoom / ZOOM_STEP) }
            Text(
                "${(zoom * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onZoom(1f) }.padding(horizontal = 6.dp, vertical = 4.dp),
            )
            ZoomButton(Icons.Filled.ZoomIn, "Zoom in") { onZoom(zoom * ZOOM_STEP) }
        }
    }
}

@Composable
private fun ZoomButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
    }
}

// One column: zooming out stops at half size (the grid of all pages is Arrange pages).
private const val MIN_ZOOM = 0.5f
/** How small pages are while arranging them. */
private const val ARRANGE_ZOOM = 0.28f
private const val A4_ASPECT = 210f / 297f
private const val MAX_ZOOM = 3f
private const val ZOOM_STEP = 1.25f

/** Floats over the pages while an image is being moved or resized. */
@Composable
private fun AdjustImageBar(onFit: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp, shadowElevation = 6.dp, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text("Drag to move · pinch or corner dot to resize", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onFit) { Text("Fit page") }
            TextButton(onClick = onDone) { Text("Done") }
        }
    }
}

/** Floats over the pages while something is lasso-selected, so its actions are always in reach. */
@Composable
private fun SelectionBar(viewModel: NoteEditorViewModel, darkPaper: Boolean, onLink: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        FlowRow(
            itemVerticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text("${viewModel.selection.size} selected", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = viewModel::deleteSelection) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Delete")
            }
            TextButton(onClick = viewModel::recognizeSelection) {
                Icon(Icons.Filled.TextFields, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("To text")
            }
            TextButton(onClick = viewModel::recognizeSelectionAsMath) {
                Icon(Icons.Filled.Functions, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("To LaTeX")
            }
            TextButton(onClick = viewModel::duplicateSelection) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Duplicate")
            }
            TextButton(onClick = onLink) {
                Icon(Icons.Filled.Link, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Link to…")
            }
            PenColors.forEach { c ->
                ColorSwatch(c, selected = false, darkPaper = darkPaper) { viewModel.recolorSelection(c) }
            }
            TextButton(onClick = viewModel::clearSelection) { Text("Done") }
        }
    }
}

private val MAX_PAGE_WIDTH = 900.dp
private const val DOUBLE_TAP_MS = 400L

// Two fingers held this long, and this still, beside the pages open the dial (as on a page).
private const val DIAL_HOLD_MS = 450L
private const val DIAL_HOLD_SLOP_PX = 24f

/** Where the straighten chip turns a page at [degrees]: the nearest multiple of 45°, or 0° if it is already on one. */
private fun straightenTarget(degrees: Float): Float {
    val nearest = ((degrees / 45f).roundToInt() * 45f) % 360f
    return if (abs(degrees - nearest) < 0.5f || abs(degrees - nearest - 360f) < 0.5f) 0f else nearest
}
