package com.gerko.fourenote.ui.editor

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
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import kotlin.math.ln
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.AddAPhoto
import com.gerko.fourenote.ui.scan.ScanDialog
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.Title
import kotlin.math.abs
import androidx.compose.foundation.layout.heightIn
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
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Switch
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.InvertColors
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
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
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material.icons.filled.HighlightAlt
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
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
import com.gerko.fourenote.data.ink.Smoothing
import kotlin.math.roundToInt
import com.gerko.fourenote.data.ink.Tool

@Composable
fun NoteEditorScreen(
    viewModel: NoteEditorViewModel,
    backgrounds: BackgroundLoader,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val snackbar = remember { SnackbarHostState() }
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
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
                contentPadding = if (fullScreen) PaddingValues(0.dp) else PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp, top = TOOLBAR_SPACE),
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
                                .then(if (fullScreen) Modifier else Modifier.shadow(2.dp)),
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
                            onTextTap = { x, y -> viewModel.tapWithText(page.id, x, y) },
                            pointedNib = viewModel.pointedNib,
                            modifier = Modifier.matchParentSize(),
                        )
                        TextBoxLayer(
                            texts = page.texts,
                            pxPerUnit = itemWidthPx / page.width,
                            editingId = viewModel.editingText?.takeIf { it.first == page.id }?.second,
                            // The Text tool and Select both pick up typed text with a tap.
                            textTool = viewModel.tool == Tool.TEXT || viewModel.tool == Tool.LASSO,
                            onEdit = { viewModel.editText(page.id, it) },
                            onChange = { viewModel.updateText(page.id, it) },
                            onDelete = viewModel::deleteEditingText,
                        )
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
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
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
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            )
        }
        viewModel.adjustingPageId?.let { pageId ->
            AdjustImageBar(
                onFit = { viewModel.setBackgroundPlacement(pageId, BackgroundPlacement()) },
                onDone = viewModel::finishAdjustingBackground,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = barsTop),
            )
        }
        if (viewModel.selection.isNotEmpty()) {
            SelectionBar(viewModel, darkPaper, Modifier.align(Alignment.TopCenter).padding(top = barsTop))
        }
        viewModel.selectedImageObject?.let { image ->
            ImageBar(viewModel, image, Modifier.align(Alignment.TopCenter).padding(top = barsTop))
        }
    }
}

/**
 * Shown after picking a PDF: its first page twice, as it is and with colors
 * inverted for dark reading. Tapping one imports the PDF that way; it can still
 * be changed per page later from the page menu. In dark mode the inverted
 * version starts highlighted.
 */
@Composable
private fun PdfColorsDialog(
    uri: android.net.Uri,
    suggestInvert: Boolean,
    onKeep: () -> Unit,
    onInvert: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val preview by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                    android.graphics.pdf.PdfRenderer(fd).use { renderer ->
                        renderer.openPage(0).use { page ->
                            val width = 480
                            val bitmap = android.graphics.Bitmap.createBitmap(
                                width,
                                (width.toLong() * page.height / page.width).toInt().coerceAtLeast(1),
                                android.graphics.Bitmap.Config.ARGB_8888,
                            )
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap.asImageBitmap()
                        }
                    }
                }
            }.getOrNull()
        }
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Import PDF") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PdfChoice("Original", preview, inverted = false, highlighted = !suggestInvert, onClick = onKeep, Modifier.weight(1f))
                PdfChoice("Inverted", preview, inverted = true, highlighted = suggestInvert, onClick = onInvert, Modifier.weight(1f))
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun PdfChoice(
    label: String,
    preview: ImageBitmap?,
    inverted: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ring = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(preview?.let { it.width.toFloat() / it.height } ?: (210f / 297f))
                .border(if (highlighted) 3.dp else 1.dp, ring, RoundedCornerShape(8.dp))
                .padding(3.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (inverted) Color.Black else Color.White),
        ) {
            if (preview == null) {
                CircularProgressIndicator(Modifier.size(32.dp))
            } else {
                Image(
                    bitmap = preview,
                    contentDescription = "$label colors",
                    colorFilter = if (inverted) SmartInvert else null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

/** Floats over the pages while a picture is selected. */
@Composable
private fun ImageBar(viewModel: NoteEditorViewModel, image: PageImage, modifier: Modifier = Modifier) {
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
private val PAGE_FOOTER_HEIGHT = 28.dp
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
private fun SelectionBar(viewModel: NoteEditorViewModel, darkPaper: Boolean, modifier: Modifier = Modifier) {
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
            PenColors.forEach { c ->
                ColorSwatch(c, selected = false, darkPaper = darkPaper) { viewModel.recolorSelection(c) }
            }
            TextButton(onClick = viewModel::clearSelection) { Text("Done") }
        }
    }
}

/** Under each page: its number counted from 1, and the page's menu. */
@Composable
private fun PageFooter(
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

@Composable
private fun AddMenu(
    onBlankPage: () -> Unit,
    onBlankPageAtEnd: () -> Unit,
    onImportPdf: () -> Unit,
    onImportImages: () -> Unit,
    onScan: () -> Unit,
    onTable: () -> Unit,
    onPhotoFromPhone: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.Add, contentDescription = "Add pages")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Blank page after this one") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null) },
                onClick = { open = false; onBlankPage() },
            )
            DropdownMenuItem(
                text = { Text("Blank page at the end") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null) },
                onClick = { open = false; onBlankPageAtEnd() },
            )
            DropdownMenuItem(
                text = { Text("Import PDF") },
                leadingIcon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null) },
                onClick = { open = false; onImportPdf() },
            )
            DropdownMenuItem(
                text = { Text("Import images") },
                leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                onClick = { open = false; onImportImages() },
            )
            DropdownMenuItem(
                text = { Text("Scan document") },
                leadingIcon = { Icon(Icons.Filled.DocumentScanner, contentDescription = null) },
                onClick = { open = false; onScan() },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Table…") },
                leadingIcon = { Icon(Icons.Filled.TableChart, contentDescription = null) },
                onClick = { open = false; onTable() },
            )
            DropdownMenuItem(
                text = { Text("Photo from phone") },
                leadingIcon = { Icon(Icons.Filled.AddAPhoto, contentDescription = null) },
                onClick = { open = false; onPhotoFromPhone() },
            )
        }
    }
}

/** Asks how many rows and columns a new table gets. */
@Composable
private fun TableDialog(onInsert: (rows: Int, columns: Int) -> Unit, onDismiss: () -> Unit) {
    var rows by rememberSaveable { mutableIntStateOf(3) }
    var columns by rememberSaveable { mutableIntStateOf(3) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert table") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Stepper("Rows", rows) { rows = it }
                Stepper("Columns", columns) { columns = it }
                Text(
                    "The table is selected after inserting: drag to move it, pull the dots to resize it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onInsert(rows, columns) }) { Text("Insert") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        IconButton(onClick = { onChange((value - 1).coerceAtLeast(1)) }, enabled = value > 1) {
            Icon(Icons.Filled.Remove, contentDescription = "Fewer $label")
        }
        Text("$value", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
        IconButton(onClick = { onChange((value + 1).coerceAtMost(MAX_TABLE_CELLS)) }, enabled = value < MAX_TABLE_CELLS) {
            Icon(Icons.Filled.Add, contentDescription = "More $label")
        }
    }
}

private const val MAX_TABLE_CELLS = 20

/** Ways to add pages, offered under the last page. */
private class AddPageActions(
    /** A blank page after the page on screen. */
    val blank: () -> Unit,
    val blankAtEnd: () -> Unit,
    val pdf: () -> Unit,
    val images: () -> Unit,
    val scan: () -> Unit,
)

/** A small handle under the last page; tapping it slides up the ways to add pages. */
@Composable
private fun AddPagesHandle(actions: AddPageActions) {
    var open by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                fun pick(action: () -> Unit) = { open = false; action() }
                AssistChip(onClick = pick(actions.blank), label = { Text("Blank page") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null) })
                AssistChip(onClick = pick(actions.blankAtEnd), label = { Text("Blank page at the end") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null) })
                AssistChip(onClick = pick(actions.pdf), label = { Text("PDF") },
                    leadingIcon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null) })
                AssistChip(onClick = pick(actions.images), label = { Text("Images") },
                    leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) })
                AssistChip(onClick = pick(actions.scan), label = { Text("Scan") },
                    leadingIcon = { Icon(Icons.Filled.DocumentScanner, contentDescription = null) })
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(width = 56.dp, height = 28.dp).clip(CircleShape).clickable { open = !open },
        ) {
            Icon(
                if (open) Icons.Filled.KeyboardArrowDown else Icons.Filled.Add,
                contentDescription = if (open) "Hide add page options" else "Add pages",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private val MAX_PAGE_WIDTH = 900.dp
private const val DOUBLE_TAP_MS = 400L

/** Pen button and multi-finger gestures, like the input settings of other note apps. */
@Composable
private fun PenSettingsDialog(viewModel: NoteEditorViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pen & gestures") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Hold a button while writing to use its tool for that stroke; click it while the pen hovers to " +
                        "switch to that tool (click again to switch back). The pen's eraser end always erases.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PenButtonChoice("Side button 1", viewModel.primaryButtonAction) { viewModel.primaryButtonAction = it }
                PenButtonChoice("Side button 2", viewModel.secondaryButtonAction) { viewModel.secondaryButtonAction = it }
                Text(
                    "Last signal from the pen: ${viewModel.lastPenSignal ?: "none yet. Press a pen button over the page."}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Nothing shows up? On Xiaomi, turn off the button shortcuts in Settings → Additional settings → Xiaomi Smart Pen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SettingSwitch("Two-finger double-tap to undo", viewModel.twoFingerDoubleTapUndo) { viewModel.twoFingerDoubleTapUndo = it }
                SettingSwitch("Three-finger double-tap to redo", viewModel.threeFingerDoubleTapRedo) { viewModel.threeFingerDoubleTapRedo = it }
                SettingSwitch("Stylus only (fingers scroll, palm ignored)", viewModel.stylusOnly) { viewModel.stylusOnly = it }
                SettingSwitch("Hold pen and finger on a picture to select it (any tool)", viewModel.penAndFingerSelect) {
                    viewModel.penAndFingerSelect = it
                }
                Text(
                    "Draw a line, circle, triangle or rectangle and hold the pen still to straighten it. " +
                        "Draw a loop around something, then hold a finger inside it to select it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "Hold two fingers still on a page, or tap the dial button, to open the quick dial.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Top-bar menu listing the note's layers, top layer first. */
@Composable
private fun LayersMenu(viewModel: NoteEditorViewModel) {
    var open by remember { mutableStateOf(false) }
    // Layers ticked for duplicating, merging or deleting together.
    var checked by remember { mutableStateOf(emptySet<String>()) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.Layers, contentDescription = "Layers")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false; checked = emptySet() }) {
            Text(
                "Layers · you draw on the selected one · tick to act on several",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            viewModel.layers.asReversed().forEach { layer ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { viewModel.selectLayer(layer.id) }
                        .padding(start = 4.dp, end = 4.dp),
                ) {
                    Checkbox(
                        checked = layer.id in checked,
                        onCheckedChange = { checked = if (it) checked + layer.id else checked - layer.id },
                    )
                    RadioButton(selected = layer.id == viewModel.activeLayerId, onClick = { viewModel.selectLayer(layer.id) })
                    Text(layer.name, modifier = Modifier.width(120.dp))
                    IconButton(onClick = { viewModel.setLayerVisible(layer.id, !layer.visible) }) {
                        Icon(
                            if (layer.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (layer.visible) "Hide ${layer.name}" else "Show ${layer.name}",
                        )
                    }
                    IconButton(onClick = { viewModel.deleteLayer(layer.id); checked = checked - layer.id }, enabled = viewModel.layers.size > 1) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete ${layer.name}")
                    }
                }
            }
            if (checked.isNotEmpty()) {
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("${checked.size} ticked", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp))
                    TextButton(onClick = { viewModel.duplicateLayers(checked); checked = emptySet() }) { Text("Duplicate") }
                    TextButton(onClick = { viewModel.mergeLayers(checked); checked = emptySet() }, enabled = checked.size > 1) { Text("Merge") }
                    TextButton(onClick = { viewModel.deleteLayers(checked); checked = emptySet() }) { Text("Delete") }
                }
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Add layer") },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = viewModel::addLayer,
            )
        }
    }
}

@Composable
private fun ToolBar(
    viewModel: NoteEditorViewModel,
    darkPaper: Boolean,
    onOpenDial: () -> Unit,
    onAddColor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Floats over the pages in two rows: the tools, and under them the chosen tool's options.
    // On narrow screens each row swipes sideways for the rest.
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    FloatingRow {
            IconButton(onClick = onOpenDial) {
                Icon(Icons.Filled.DonutLarge, contentDescription = "Quick dial")
            }
            ToolChip("Pen", Icons.Filled.Create, viewModel.tool == Tool.PEN) { viewModel.selectTool(Tool.PEN) }
            ToolChip("Ink pen", Icons.Filled.HistoryEdu, viewModel.tool == Tool.INK_PEN) {
                viewModel.selectTool(Tool.INK_PEN)
            }
            ToolChip("Calligraphy", Icons.Filled.FormatItalic, viewModel.tool == Tool.CALLIGRAPHY) {
                viewModel.selectTool(Tool.CALLIGRAPHY)
            }
            ToolChip("Highlighter", Icons.Filled.BorderColor, viewModel.tool == Tool.HIGHLIGHTER) {
                viewModel.selectTool(Tool.HIGHLIGHTER)
            }
            ToolChip("Eraser", Icons.Outlined.CleaningServices, viewModel.tool == Tool.ERASER) {
                viewModel.selectTool(Tool.ERASER)
            }
            ToolChip("Select", Icons.Filled.HighlightAlt, viewModel.tool == Tool.LASSO) {
                viewModel.selectTool(Tool.LASSO)
            }
            ToolChip("Text", Icons.Filled.Title, viewModel.tool == Tool.TEXT) {
                viewModel.selectTool(Tool.TEXT)
            }
    }
    FloatingRow {
            if (viewModel.tool == Tool.LASSO) {
                SelectionOptions(viewModel)
            } else if (viewModel.tool == Tool.TEXT) {
                TextStyleBar(viewModel, darkPaper)
            } else if (viewModel.tool != Tool.ERASER) {
                if (viewModel.tool == Tool.CALLIGRAPHY) {
                    // Pointed: bold going down, thin going up (like a dip pen). Flat: a broad nib, angle on the dial.
                    FilterChip(selected = viewModel.pointedNib, onClick = { viewModel.pointedNib = true }, label = { Text("Pointed") })
                    FilterChip(selected = !viewModel.pointedNib, onClick = { viewModel.pointedNib = false }, label = { Text("Flat") })
                    VerticalDivider(Modifier.height(32.dp).padding(horizontal = 4.dp))
                }
                val highlighter = viewModel.tool == Tool.HIGHLIGHTER
                val colors = if (highlighter) HighlighterColors else PenColors
                val selectedColor = if (highlighter) viewModel.highlighterColor else viewModel.penColor
                (colors + viewModel.customColors.filter { it !in colors }).forEach { c ->
                    val custom = c in viewModel.customColors
                    ColorSwatch(
                        c,
                        selected = c == selectedColor,
                        darkPaper = darkPaper && !highlighter,
                        // Long-press one of your own colors to remove it.
                        onLongClick = if (custom) ({ viewModel.removeCustomColor(c) }) else null,
                    ) {
                        if (highlighter) viewModel.highlighterColor = c else viewModel.penColor = c
                    }
                }
                IconButton(onClick = onAddColor) {
                    Icon(Icons.Filled.Add, contentDescription = "Add a color")
                }

                VerticalDivider(Modifier.height(32.dp).padding(horizontal = 4.dp))
                val inkPen = viewModel.tool == Tool.INK_PEN
                val calligraphy = viewModel.tool == Tool.CALLIGRAPHY
                val widths = when {
                    highlighter -> HighlighterWidths
                    inkPen -> InkPenWidths
                    calligraphy -> CalligraphyWidths
                    else -> PenWidths
                }
                val selectedWidth = when {
                    highlighter -> viewModel.highlighterWidth
                    inkPen -> viewModel.inkPenWidth
                    calligraphy -> viewModel.calligraphyWidth
                    else -> viewModel.penWidth
                }
                widths.forEachIndexed { i, w ->
                    WidthDot(
                        dotSize = (6 + i * 6).dp,
                        selected = w == selectedWidth,
                        color = selectedColor,
                    ) {
                        when {
                            highlighter -> viewModel.highlighterWidth = w
                            inkPen -> viewModel.inkPenWidth = w
                            calligraphy -> viewModel.calligraphyWidth = w
                            else -> viewModel.penWidth = w
                        }
                    }
                }

                if (calligraphy) {
                    VerticalDivider(Modifier.height(32.dp).padding(horizontal = 4.dp))
                    // Flat: the nib's angle. Pointed: the slant of the thick down-strokes.
                    Text(
                        "${if (viewModel.pointedNib) "Slant" else "Nib angle"} ${viewModel.nibDegrees.roundToInt()}°",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Slider(
                        value = viewModel.nibDegrees,
                        onValueChange = { viewModel.nibDegrees = (it / 15f).roundToInt() * 15f },
                        valueRange = 0f..90f,
                        steps = 5,
                        modifier = Modifier.width(150.dp),
                    )
                }
                if (!highlighter) {
                    FilterChip(
                        selected = viewModel.scribbleToErase,
                        onClick = { viewModel.scribbleToErase = !viewModel.scribbleToErase },
                        label = { Text("Scribble or strike out to erase") },
                    )
                }
                VerticalDivider(Modifier.height(32.dp).padding(horizontal = 4.dp))
                Text("Smoothing ${viewModel.smoothing}", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = viewModel.smoothing.toFloat(),
                    onValueChange = { viewModel.smoothing = it.roundToInt() },
                    valueRange = 0f..Smoothing.MAX_LEVEL.toFloat(),
                    steps = Smoothing.MAX_LEVEL - 1,
                    modifier = Modifier.width(180.dp),
                )
            } else {
                FilterChip(
                    selected = !viewModel.eraseWholeStrokes,
                    onClick = { viewModel.eraseWholeStrokes = false },
                    label = { Text("Erase part") },
                )
                FilterChip(
                    selected = viewModel.eraseWholeStrokes,
                    onClick = { viewModel.eraseWholeStrokes = true },
                    label = { Text("Erase whole lines") },
                )
            }
    }
    }
}

/** One floating, rounded row of the tool bar; swipes sideways when it doesn't fit. */
@Composable
private fun FloatingRow(content: @Composable RowScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 4.dp, shadowElevation = 6.dp) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun SelectionOptions(viewModel: NoteEditorViewModel) {
    Hint(
        if (viewModel.selection.isEmpty()) {
            "Draw a loop around writing to select it"
        } else {
            "Drag to move · dots stretch width, height or both"
        },
    )
}

@Composable
private fun Hint(text: String) {
    Spacer(Modifier.width(4.dp))
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The text tool's options: looks, size, colour, and whether `$…$` becomes maths while typing. */
@Composable
private fun TextStyleBar(viewModel: NoteEditorViewModel, darkPaper: Boolean) {
    val style = viewModel.textStyle
    fun change(new: com.gerko.fourenote.data.ink.TextStyleSpec) = viewModel.changeTextStyle(new)
    TextPreset.entries.forEach { preset ->
        val chosen = style.size == preset.size && style.bold == preset.bold && style.font == preset.font
        FilterChip(
            selected = chosen,
            onClick = { change(style.copy(size = preset.size, bold = preset.bold, font = preset.font)) },
            label = { Text(preset.label) },
        )
    }
    VerticalDivider(Modifier.height(32.dp).padding(horizontal = 4.dp))
    com.gerko.fourenote.data.ink.TextFont.entries.forEach { font ->
        FilterChip(
            selected = style.font == font,
            onClick = { change(style.copy(font = font)) },
            label = { Text(font.name.lowercase().replaceFirstChar { it.uppercase() }) },
        )
    }
    IconButton(onClick = { change(style.copy(size = (style.size - 2f).coerceAtLeast(8f))) }) {
        Icon(Icons.Filled.Remove, contentDescription = "Smaller text")
    }
    Text("${style.size.roundToInt()}", style = MaterialTheme.typography.labelLarge)
    IconButton(onClick = { change(style.copy(size = (style.size + 2f).coerceAtMost(96f))) }) {
        Icon(Icons.Filled.Add, contentDescription = "Bigger text")
    }
    FilterChip(selected = style.bold, onClick = { change(style.copy(bold = !style.bold)) }, label = { Text("B", fontWeight = FontWeight.Bold) })
    FilterChip(selected = style.italic, onClick = { change(style.copy(italic = !style.italic)) }, label = { Text("I", fontStyle = FontStyle.Italic) })
    VerticalDivider(Modifier.height(32.dp).padding(horizontal = 4.dp))
    viewModel.toolColors.forEach { c ->
        ColorSwatch(c, selected = Color(style.color) == c, darkPaper = darkPaper) {
            viewModel.penColor = c
            change(style.copy(color = c.toArgb()))
        }
    }
    VerticalDivider(Modifier.height(32.dp).padding(horizontal = 4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Auto LaTeX", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(8.dp))
        Switch(checked = viewModel.autoLatex, onCheckedChange = viewModel::changeAutoLatex)
    }
    if (viewModel.editingText != null) {
        TextButton(onClick = viewModel::finishTextEditing) { Text("Done") }
    }
}

@Composable
private fun ToolChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
    )
}

@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    darkPaper: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val ring = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .border(3.dp, ring, CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(6.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape),
    )
}

@Composable
private fun WidthDot(dotSize: androidx.compose.ui.unit.Dp, selected: Boolean, color: Color, onClick: () -> Unit) {
    val ring = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .border(3.dp, ring, CircleShape)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.size(dotSize).clip(CircleShape).background(color))
    }
}

@Composable
private fun PaperMenu(selected: PaperStyle, onSelect: (PaperStyle) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.GridOn, contentDescription = "Paper style")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                Triple(PaperStyle.LINED, "Lined", Icons.AutoMirrored.Filled.Notes),
                Triple(PaperStyle.GRID, "Grid", Icons.Filled.GridOn),
                Triple(PaperStyle.DOTS, "Dots", Icons.Filled.Apps),
                Triple(PaperStyle.BLANK, "Blank", Icons.AutoMirrored.Filled.InsertDriveFile),
            ).forEach { (style, label, icon) ->
                DropdownMenuItem(
                    text = { Text(if (style == selected) "$label  ✓" else label) },
                    leadingIcon = { Icon(icon, contentDescription = null) },
                    onClick = {
                        onSelect(style)
                        open = false
                    },
                )
            }
        }
    }
}

/** One pen button's action, as a dropdown. */
@Composable
private fun PenButtonChoice(label: String, action: PenButtonAction, onChange: (PenButtonAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }) { Text(action.label) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                PenButtonAction.entries.forEach { choice ->
                    DropdownMenuItem(text = { Text(choice.label) }, onClick = { onChange(choice); open = false })
                }
            }
        }
    }
}

/** The floating tool bar's height plus its margins: the pages start below it. */
private val TOOLBAR_SPACE = 136.dp

// Two fingers held this long, and this still, beside the pages open the dial (as on a page).
private const val DIAL_HOLD_MS = 450L
private const val DIAL_HOLD_SLOP_PX = 24f

/** Where the straighten chip turns a page at [degrees]: the nearest multiple of 45°, or 0° if it is already on one. */
private fun straightenTarget(degrees: Float): Float {
    val nearest = ((degrees / 45f).roundToInt() * 45f) % 360f
    return if (abs(degrees - nearest) < 0.5f || abs(degrees - nearest - 360f) < 0.5f) 0f else nearest
}
