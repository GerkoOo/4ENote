package com.gerko.fourenote.ui.editor

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.Title
import kotlin.math.abs
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
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
    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val scanned = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages.orEmpty()
        viewModel.importImages(
            scanned.map { page -> { context.contentResolver.openInputStream(page.imageUri) } },
            ::scrollToPage,
        )
    }
    fun startScan() {
        val activity = context as? Activity ?: return
        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setGalleryImportAllowed(true)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener { scanLauncher.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener { viewModel.showMessage("Scanner unavailable: ${it.message}") }
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
                        onBlankPage = { scrollToPage(viewModel.addBlankPage()) },
                        onImportPdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                        onImportImages = { imagePicker.launch(arrayOf("image/*")) },
                        onScan = ::startScan,
                        onTable = { addingTable = true },
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
                    IconButton(onClick = { fullScreen = true }) {
                        Icon(Icons.Filled.Fullscreen, contentDescription = "Full screen")
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
            if (!fullScreen) {
                ToolBar(
                    viewModel,
                    darkPaper,
                    onOpenDial = {
                        val metrics = context.resources.displayMetrics
                        dialAt = Offset(metrics.widthPixels / 2f, metrics.heightPixels / 2f)
                    },
                    onAddColor = { pickingColor = true },
                )
            }
            if (viewModel.importing || viewModel.exporting) LinearProgressIndicator(Modifier.fillMaxWidth()) else if (!fullScreen) HorizontalDivider()
            if (viewModel.loaded) {
                PageList(
                    viewModel, backgrounds, gridState, darkPaper,
                    onTwoFingerHold = { dialAt = it },
                    onMultiFingerTap = ::onMultiFingerTap,
                    onPenButton = ::onPenButton,
                    addPages = AddPageActions(
                        blank = { scrollToPage(viewModel.addBlankPage()) },
                        pdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                        images = { imagePicker.launch(arrayOf("image/*")) },
                        scan = ::startScan,
                    ),
                    fullScreen = fullScreen,
                )
            } else {
                Box(Modifier.fillMaxSize())
            }
        }
    }
    if (fullScreen) {
        FilledTonalIconButton(
            onClick = { fullScreen = false },
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).alpha(0.7f),
        ) {
            Icon(Icons.Filled.FullscreenExit, contentDescription = "Exit full screen")
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
) {
    val drawingColor = if (viewModel.tool == Tool.HIGHLIGHTER) viewModel.highlighterColor else viewModel.penColor
    val drawingWidth = when (viewModel.tool) {
        Tool.HIGHLIGHTER -> viewModel.highlighterWidth
        Tool.INK_PEN -> viewModel.inkPenWidth
        Tool.CALLIGRAPHY -> viewModel.calligraphyWidth
        else -> viewModel.penWidth
    }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    val horizontal = rememberScrollState()
    // Full screen opens at the fitting size on the page being looked at, and gives the zoom back after.
    var zoomBeforeFullScreen by rememberSaveable { mutableFloatStateOf(1f) }
    LaunchedEffect(fullScreen) {
        val page = gridState.firstVisibleItemIndex
        if (fullScreen) {
            zoomBeforeFullScreen = zoom
            zoom = 1f
        } else {
            zoom = zoomBeforeFullScreen
        }
        gridState.scrollToItem(page)
        horizontal.scrollTo(0)
    }
    // Pages wider than the screen start centered rather than pinned to the left edge.
    LaunchedEffect(zoom, fullScreen) {
        withFrameNanos { } // once laid out at the new size
        horizontal.scrollTo(horizontal.maxValue / 2)
    }
    val scope = rememberCoroutineScope()
    // The last one-finger tap on a page in the overview, to spot a double tap: page id and time.
    var lastPageTap by remember { mutableStateOf("" to 0L) }
    // The page a double tap zoomed to. Scrolled to once the grid is composed at the new zoom:
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
        horizontal.scrollTo(0)
        zoomedToPage = null
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        // At 100% a page fills the width (up to a comfortable maximum). Zooming out fits
        // several pages per row; zooming in makes pages wider than the screen, panned sideways.
        // Full screen: one page per screen, as large as fits, centered, with no gaps or footers.
        fun fitWidth(aspect: Float) = minOf(maxWidth, maxHeight * aspect)
        val fullWidth = if (fullScreen) fitWidth(A4_ASPECT) else minOf(maxWidth - 32.dp, MAX_PAGE_WIDTH)
        val pageWidth = fullWidth * zoom
        val columns = if (fullScreen) 1 else maxOf(1, ((maxWidth - 16.dp) / (pageWidth + 16.dp)).toInt())
        // Zoomed out below 100%, double-tapping a page zooms in until it just fits the screen.
        fun onPageTap(index: Int, pageId: String, aspect: Float) {
            val now = System.currentTimeMillis()
            val (lastId, lastTime) = lastPageTap
            if (lastId != pageId || now - lastTime > DOUBLE_TAP_MS) {
                lastPageTap = pageId to now
                return
            }
            lastPageTap = "" to 0L
            // Leave room for the page footer below the page.
            val fitHeight = (maxHeight - 32.dp - PAGE_FOOTER_HEIGHT) / (fullWidth / aspect)
            zoom = minOf(1f, fitHeight).coerceIn(MIN_ZOOM, MAX_ZOOM)
            zoomedToPage = index
        }
        val gridWidth = if (fullScreen) maxOf(maxWidth, pageWidth) else maxOf(maxWidth, pageWidth + 32.dp)
        // In full screen at fitting size, a scroll or fling settles on whole pages, like turning them.
        val snapping = fullScreen && zoom <= 1f
        fun snapToNearestPage() {
            if (!snapping) return
            val info = gridState.layoutInfo
            val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
            val nearest = info.visibleItemsInfo.filter { it.index < viewModel.pages.size }
                .minByOrNull { abs(it.offset.y + it.size.height / 2 - middle) } ?: return
            scope.launch { gridState.animateScrollToItem(nearest.index) }
        }
        val density = LocalDensity.current
        val pageWidthPx = with(density) { pageWidth.toPx() }
        // Backgrounds are re-rendered in size steps, not on every frame of a pinch.
        val backgroundWidthPx = ((pageWidthPx / 128f).roundToInt().coerceAtLeast(1)) * 128

        Box(Modifier.fillMaxSize().horizontalScroll(horizontal, enabled = gridWidth > maxWidth)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(if (fullScreen) 0.dp else 16.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(if (fullScreen) 0.dp else 12.dp),
                contentPadding = PaddingValues(if (fullScreen) 0.dp else 16.dp),
                flingBehavior = if (snapping) rememberSnapFlingBehavior(gridState, SnapPosition.Start) else ScrollableDefaults.flingBehavior(),
                modifier = Modifier.width(gridWidth).fillMaxHeight().onGloballyPositioned { gridBounds = it.boundsInRoot() },
            ) {
                itemsIndexed(viewModel.pages, key = { _, page -> page.id }) { index, page ->
                    // Grid cells are stretched to fill their column; keep the page at its zoomed
                    // width so the ink's scale and the page's on-screen size always agree.
                    val dragged = draggingPage == page.id
                    val itemWidth = if (fullScreen) fitWidth(page.width / page.height) * zoom else pageWidth
                    val itemWidthPx = with(density) { itemWidth.toPx() }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            // Each page gets a whole screen in full screen, centered in it.
                            .then(if (fullScreen) Modifier.heightIn(min = this@BoxWithConstraints.maxHeight) else Modifier)
                            .zIndex(if (dragged) 1f else 0f)
                            // The other pages slide out of the way of a page being moved.
                            .animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = if (dragged) null else spring()),
                        contentAlignment = if (fullScreen) Alignment.Center else Alignment.TopCenter,
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
                        val background by produceState<ImageBitmap?>(null, page.background, backgroundWidthPx) {
                            value = page.background?.let { backgrounds.load(viewModel.noteId, it, backgroundWidthPx)?.asImageBitmap() }
                        }
                        val imageFiles = page.images.map { it.file }.toSet()
                        val imageBitmaps by produceState(emptyMap<String, ImageBitmap>(), imageFiles, backgroundWidthPx) {
                            value = imageFiles.mapNotNull { file ->
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
                                .then(if (fullScreen) Modifier else Modifier.shadow(2.dp)),
                        ) {
                        InkCanvas(
                            strokes = visibleStrokes,
                            scale = itemWidthPx / page.width,
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
                            onStroke = { viewModel.addStroke(page.id, it) },
                            onEraseStart = viewModel::beginErase,
                            onErase = { x, y -> viewModel.eraseAt(page.id, x, y) },
                            onEraseEnd = viewModel::endErase,
                            onLasso = { viewModel.selectWithLasso(page.id, it) },
                            onTransformSelection = viewModel::transformSelection,
                            onScroll = { dx, dy ->
                                gridState.dispatchRawDelta(dy)
                                horizontal.dispatchRawDelta(dx)
                            },
                            onScrollEnd = ::snapToNearestPage,
                            onZoom = { factor -> zoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM) },
                            onTwoFingerHold = onTwoFingerHold,
                            primaryButtonAction = viewModel.primaryButtonAction,
                            secondaryButtonAction = viewModel.secondaryButtonAction,
                            onPenButtonClick = onPenButton,
                            onPenSignal = { viewModel.lastPenSignal = it },
                            onMultiFingerTap = onMultiFingerTap,
                            // Zoomed out below 100%, fingers scroll, tap and move pages; only the pen writes.
                            overview = zoom < 1f,
                            onFingerTap = { if (zoom < 1f) onPageTap(index, page.id, page.width / page.height) },
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
                            textTool = viewModel.tool == Tool.TEXT,
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
        // Hidden in full screen; pinching still zooms.
        if (!fullScreen) {
            ZoomControl(
                zoom = zoom,
                onZoom = { zoom = it.coerceIn(MIN_ZOOM, MAX_ZOOM) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            )
        }
        viewModel.adjustingPageId?.let { pageId ->
            AdjustImageBar(
                onFit = { viewModel.setBackgroundPlacement(pageId, BackgroundPlacement()) },
                onDone = viewModel::finishAdjustingBackground,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            )
        }
        if (viewModel.selection.isNotEmpty()) {
            SelectionBar(viewModel, darkPaper, Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
        }
        viewModel.selectedImageObject?.let { image ->
            ImageBar(viewModel, image, Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
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
    Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp, shadowElevation = 6.dp, modifier = modifier) {
        FlowRow(
            itemVerticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
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
            IconToggleButton(checked = image.inverted, onCheckedChange = { viewModel.toggleSelectedImageInverted() }) {
                Icon(Icons.Filled.InvertColors, contentDescription = if (image.inverted) "Restore colors" else "Invert colors")
            }
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

private const val MIN_ZOOM = 0.2f
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
private fun AddMenu(onBlankPage: () -> Unit, onImportPdf: () -> Unit, onImportImages: () -> Unit, onScan: () -> Unit, onTable: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.Add, contentDescription = "Add pages")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Blank page") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null) },
                onClick = { open = false; onBlankPage() },
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
private class AddPageActions(val blank: () -> Unit, val pdf: () -> Unit, val images: () -> Unit, val scan: () -> Unit)

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
) {
    // Wraps onto more rows on narrow (portrait) screens so every option stays reachable.
    Surface(tonalElevation = 2.dp) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
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
            VerticalDivider(Modifier.height(32.dp).padding(horizontal = 4.dp))

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
                    Text("Nib angle ${viewModel.nibDegrees.roundToInt()}°", style = MaterialTheme.typography.labelLarge)
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
                        label = { Text("Scribble to erase") },
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
