package com.gerko.fourenote.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.gerko.fourenote.data.scan.FramingGuide
import com.gerko.fourenote.data.scan.EdgeModel
import com.gerko.fourenote.data.scan.PageFinder
import com.gerko.fourenote.data.scan.PageScanner
import com.gerko.fourenote.data.scan.ScanFilter
import com.gerko.fourenote.data.scan.ScanSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors

/** One photographed page: the photo, where its corners are, and how it will be turned into the final page. */
/**
 * One photo being reviewed. It starts as the whole photo, as taken; [suggested] is the page the
 * scanner found in it, one tap away.
 */
private class ScanPage(val photo: Bitmap, val preview: Bitmap, val suggested: PageFinder.Corners, settings: ScanSettings) {
    val whole = PageFinder.Corners(
        floatArrayOf(0f, 0f, photo.width.toFloat(), 0f, photo.width.toFloat(), photo.height.toFloat(), 0f, photo.height.toFloat()),
    )
    var corners by mutableStateOf(whole)
    var settings by mutableStateOf(settings)
}

/**
 * The document scanner, all on the device and open source: a live camera that outlines the
 * page it sees (green once it is framed well; the user takes the picture), then a review of the
 * pages: each starts as the photo as taken, with the found page crop and filters as suggestions
 * (drag corners with a magnifier, turn, brightness and contrast, retake or delete). [onDone]
 * gets each finished page as a JPEG.
 */
@Composable
fun ScanDialog(decode: (File) -> Bitmap?, onDone: (List<ByteArray>) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val edges = remember { EdgeModel(context) }
    DisposableEffect(Unit) { onDispose { edges.close() } }
    val scope = rememberCoroutineScope()
    val pages = remember { mutableStateListOf<ScanPage>() }
    var current by remember { mutableIntStateOf(0) }
    var reviewing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    // The page to replace with the next photo (Retake), or -1 to add one.
    var replacing by remember { mutableIntStateOf(-1) }
    var lastSettings by remember { mutableStateOf(ScanSettings()) }
    val dir = remember { File(context.cacheDir, "scans").apply { mkdirs() } }

    fun addPhoto(file: File) {
        scope.launch {
            busy = true
            val page = withContext(Dispatchers.Default) {
                val photo = decode(file) ?: return@withContext null
                val scale = minOf(1f, 1200f / maxOf(photo.width, photo.height))
                val preview = Bitmap.createScaledBitmap(
                    photo, (photo.width * scale).toInt().coerceAtLeast(1), (photo.height * scale).toInt().coerceAtLeast(1), true,
                )
                ScanPage(photo, preview, PageScanner.findCorners(photo, edges), lastSettings.copy(quarterTurns = 0))
            }
            busy = false
            file.delete()
            if (page == null) return@launch
            if (replacing in pages.indices) {
                pages[replacing] = page
                current = replacing
                replacing = -1
                reviewing = true
            } else {
                pages += page
                current = pages.lastIndex
            }
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val copy = File(dir, "picked-${System.nanoTime()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input -> copy.outputStream().use { input.copyTo(it) } }
        addPhoto(copy)
        reviewing = true
    }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            if (!reviewing || pages.isEmpty()) {
                CameraStage(
                    edges = edges,
                    dir = dir,
                    pageCount = pages.size,
                    lastPage = pages.lastOrNull()?.preview,
                    onPhoto = ::addPhoto,
                    onPickPhoto = { gallery.launch("image/*") },
                    onReview = { if (pages.isNotEmpty()) reviewing = true },
                    onCancel = { if (pages.isEmpty()) onCancel() else reviewing = true },
                )
            } else {
                ReviewStage(
                    pages = pages,
                    current = current.coerceIn(0, pages.lastIndex),
                    onSelect = { current = it },
                    onSettings = { lastSettings = it },
                    onRetake = { replacing = current; reviewing = false },
                    onAddMore = { replacing = -1; reviewing = false },
                    onDelete = {
                        pages.removeAt(current)
                        current = current.coerceAtMost(pages.lastIndex).coerceAtLeast(0)
                        if (pages.isEmpty()) reviewing = false
                    },
                    onCancel = onCancel,
                    busy = busy,
                    onDone = {
                        scope.launch {
                            busy = true
                            val files = withContext(Dispatchers.Default) {
                                pages.map { p ->
                                    val result = PageScanner.process(p.photo, p.corners, p.settings)
                                    ByteArrayOutputStream().also { result.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
                                        .also { result.recycle() }
                                }
                            }
                            busy = false
                            onDone(files)
                        }
                    },
                )
            }
            if (busy) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}

/**
 * The live camera. Each preview frame is checked for a page ([PageFinder] on a small copy),
 * which is outlined on screen; with Auto on, a page that holds still is captured by itself
 * ([AutoCapture]). Without camera permission it offers picking photos instead.
 */
@Composable
private fun CameraStage(
    edges: EdgeModel,
    dir: File,
    pageCount: Int,
    lastPage: Bitmap?,
    onPhoto: (File) -> Unit,
    onPickPhoto: () -> Unit,
    onReview: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var allowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    LaunchedEffect(Unit) { if (!allowed) askPermission.launch(Manifest.permission.CAMERA) }

    var outline by remember { mutableStateOf<FloatArray?>(null) }
    var frame by remember { mutableStateOf(IntSize(3, 4)) }
    // The page is framed well: the outline turns green. The picture is still taken by the user.
    var framed by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    val currentOnPhoto by rememberUpdatedState(onPhoto)

    val controller = remember { LifecycleCameraController(context) }
    val analysis = remember { Executors.newSingleThreadExecutor() }
    val guide = remember { FramingGuide() }

    fun capture() {
        if (capturing) return
        capturing = true
        val file = File(dir, "shot-${System.nanoTime()}.jpg")
        controller.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturing = false
                    currentOnPhoto(file)
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                }
            },
        )
    }

    DisposableEffect(allowed) {
        if (allowed) {
            controller.setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
            controller.imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            controller.imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
            controller.setImageAnalysisAnalyzer(analysis) { image ->
                // The trained model when it is sure, the brightness-based finder otherwise.
                val upright = runCatching { uprightBitmap(image) }.getOrNull()
                val (luma, w, h) = uprightLuma(image)
                image.close()
                val found = upright?.let { bitmap ->
                    runCatching { edges.find(bitmap) }.getOrNull()?.let { c ->
                        FloatArray(8) { if (it % 2 == 0) c.points[it] / bitmap.width else c.points[it] / bitmap.height }
                    }.also { bitmap.recycle() }
                }
                val corners = found
                    ?: PageFinder.find(luma, w, h)?.points?.let { p -> FloatArray(8) { if (it % 2 == 0) p[it] / w else p[it] / h } }
                val good = guide.onFrame(corners)
                ContextCompat.getMainExecutor(context).execute {
                    frame = IntSize(w, h)
                    outline = corners
                    framed = good
                }
            }
            controller.bindToLifecycle(lifecycle)
        }
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
        }
    }
    DisposableEffect(Unit) { onDispose { analysis.shutdown() } }

    val accent = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (allowed) {
            AndroidView(
                factory = { PreviewView(it).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; this.controller = controller } },
                modifier = Modifier.fillMaxSize(),
            )
            // The page outline, mapped from the analysed frame onto the preview (which fills the view).
            Canvas(Modifier.fillMaxSize()) {
                val p = outline ?: return@Canvas
                val scale = maxOf(size.width / frame.width, size.height / frame.height)
                val left = (size.width - frame.width * scale) / 2
                val top = (size.height - frame.height * scale) / 2
                fun at(k: Int) = Offset(left + p[2 * k] * frame.width * scale, top + p[2 * k + 1] * frame.height * scale)
                val path = Path().apply {
                    moveTo(at(0).x, at(0).y)
                    for (k in 1..3) lineTo(at(k).x, at(k).y)
                    close()
                }
                val color = if (framed) FRAMED_GREEN else Color.White
                drawPath(path, color.copy(alpha = if (framed) 0.25f else 0.12f))
                drawPath(path, color, style = Stroke(4.dp.toPx()))
            }
            if (capturing) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.4f)))
        } else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera access is needed to scan; you can also pick photos.", color = Color.White)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { askPermission.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                    Button(onClick = onPickPhoto) { Text("From photos") }
                }
            }
        }

        IconButton(onClick = onCancel, modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Close scanner", tint = Color.White)
        }
        if (allowed) {
            Text(
                if (framed) "Framed well: tap the button to take it" else "Fit the page in view and hold still",
                color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp).clip(RoundedCornerShape(20.dp))
                    .background(if (framed) FRAMED_GREEN.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onPickPhoto) {
                Icon(Icons.Filled.Image, contentDescription = null, tint = Color.White)
                Text("  Photos", color = Color.White)
            }
            // The shutter; its ring turns green while the page is framed well.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(84.dp).border(5.dp, if (framed) FRAMED_GREEN else Color.White, CircleShape),
            ) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White).clickable(enabled = allowed && !capturing) { capture() })
            }
            // The last page taken and how many so far; tap to review.
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).border(BorderStroke(2.dp, Color.White), RoundedCornerShape(8.dp))
                    .clickable(enabled = pageCount > 0, onClick = onReview),
                contentAlignment = Alignment.Center,
            ) {
                lastPage?.let { Image(it.asImageBitmap(), contentDescription = "Review scanned pages", modifier = Modifier.fillMaxSize()) }
                if (pageCount > 0) {
                    Text(
                        "$pageCount",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.clip(CircleShape).background(accent).padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

/** The frame as a small upright colour picture for the edge model. */
private fun uprightBitmap(image: ImageProxy): Bitmap {
    val frame = image.toBitmap()
    val scale = 384f / maxOf(frame.width, frame.height)
    val matrix = android.graphics.Matrix().apply {
        postScale(scale, scale)
        postRotate(image.imageInfo.rotationDegrees.toFloat())
    }
    return Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, matrix, true).also { if (it !== frame) frame.recycle() }
}

/** The frame's brightness, turned the right way up and shrunk to about 320 pixels on its long side. */
private fun uprightLuma(image: ImageProxy): Triple<IntArray, Int, Int> {
    val plane = image.planes[0]
    val buffer = plane.buffer
    val stride = plane.rowStride
    val step = maxOf(1, maxOf(image.width, image.height) / 320)
    val sw = image.width / step
    val sh = image.height / step
    val small = IntArray(sw * sh) { i -> buffer.get((i / sw) * step * stride + (i % sw) * step).toInt() and 0xFF }
    return when (image.imageInfo.rotationDegrees) {
        // Turned a quarter clockwise: the upright picture is sh wide and sw tall.
        90 -> Triple(IntArray(sw * sh) { i -> val x = i % sh; val y = i / sh; small[(sh - 1 - x) * sw + y] }, sh, sw)
        180 -> Triple(IntArray(sw * sh) { i -> small[sw * sh - 1 - i] }, sw, sh)
        270 -> Triple(IntArray(sw * sh) { i -> val x = i % sh; val y = i / sh; small[x * sw + (sw - 1 - y)] }, sh, sw)
        else -> Triple(small, sw, sh)
    }
}

@Composable
private fun ReviewStage(
    pages: List<ScanPage>,
    current: Int,
    onSelect: (Int) -> Unit,
    onSettings: (ScanSettings) -> Unit,
    onRetake: () -> Unit,
    onAddMore: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    busy: Boolean,
    onDone: () -> Unit,
) {
    val page = pages.getOrNull(current) ?: return
    fun change(settings: ScanSettings) {
        page.settings = settings
        onSettings(settings)
    }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Cancel scanning") }
            Text(
                "Page ${current + 1} of ${pages.size} · drag the corners to crop",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onDone, enabled = !busy) { Text(if (pages.size > 1) "Insert ${pages.size} scans" else "Insert scan") }
        }
        Row(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CornerEditor(page, Modifier.weight(1f).fillMaxSize())
            ResultPreview(page, Modifier.weight(1f).fillMaxSize())
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // The photo as taken, or cropped to the page the scanner found.
            FilterChip(selected = page.corners === page.whole, onClick = { page.corners = page.whole }, label = { Text("Whole photo") })
            FilterChip(
                selected = page.corners === page.suggested,
                onClick = { page.corners = page.suggested },
                label = { Text("Crop to page") },
            )
            Text("·", style = MaterialTheme.typography.titleMedium)
            ScanFilter.entries.forEach { filter ->
                FilterChip(selected = page.settings.filter == filter, onClick = { change(page.settings.copy(filter = filter)) }, label = { Text(filter.label) })
            }
            IconButton(onClick = { change(page.settings.copy(quarterTurns = (page.settings.quarterTurns + 1) % 4)) }) {
                Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Turn the page")
            }
            // Each slider stays next to its name when the row wraps.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Brightness", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = page.settings.brightness,
                    onValueChange = { change(page.settings.copy(brightness = it)) },
                    valueRange = -1f..1f,
                    modifier = Modifier.width(160.dp).padding(horizontal = 8.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Contrast", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = page.settings.contrast,
                    onValueChange = { change(page.settings.copy(contrast = it)) },
                    valueRange = -1f..1f,
                    modifier = Modifier.width(160.dp).padding(horizontal = 8.dp),
                )
            }
            TextButton(onClick = onRetake) { Text("Retake") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Remove this page") }
            TextButton(onClick = onAddMore) {
                Icon(Icons.Filled.AddAPhoto, contentDescription = null)
                Text("  Add more")
            }
        }
        // All pages; tap one to review it.
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().height(88.dp).padding(top = 8.dp)) {
            itemsIndexed(pages) { index, p ->
                val image = remember(p.preview) { p.preview.asImageBitmap() }
                Image(
                    image,
                    contentDescription = "Page ${index + 1}",
                    modifier = Modifier.size(64.dp, 80.dp).clip(RoundedCornerShape(6.dp))
                        .border(
                            if (index == current) 3.dp else 1.dp,
                            if (index == current) MaterialTheme.colorScheme.primary else Color.Gray,
                            RoundedCornerShape(6.dp),
                        )
                        .clickable { onSelect(index) },
                )
            }
        }
    }
}

/** The photo with the page outline; the nearest corner follows a drag, with a magnifier showing it close up. */
@Composable
private fun CornerEditor(page: ScanPage, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val handle = with(LocalDensity.current) { 14.dp.toPx() }
    val grab = with(LocalDensity.current) { 64.dp.toPx() }
    val loupe = with(LocalDensity.current) { 56.dp.toPx() }
    BoxWithConstraints(modifier) {
        val photo = page.preview
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val fit = minOf(boxW / photo.width, boxH / photo.height)
        val left = (boxW - photo.width * fit) / 2
        val top = (boxH - photo.height * fit) / 2
        // Screen position of a point in the full-size photo.
        val perPhotoPixel = fit * photo.width / page.photo.width
        fun toScreen(x: Float, y: Float) = Offset(left + x * perPhotoPixel, top + y * perPhotoPixel)
        val image = remember(photo) { photo.asImageBitmap() }
        var dragging by remember { mutableIntStateOf(-1) }
        Canvas(
            Modifier.fillMaxSize().pointerInput(page) {
                detectDragGestures(
                    onDragStart = { at ->
                        val p = page.corners.points
                        fun distance(k: Int) = (toScreen(p[2 * k], p[2 * k + 1]) - at).getDistance()
                        dragging = (0 until 4).minBy(::distance).takeIf { distance(it) < grab } ?: -1
                    },
                    onDragEnd = { dragging = -1 },
                    onDragCancel = { dragging = -1 },
                    onDrag = { change, drag ->
                        if (dragging < 0) return@detectDragGestures
                        change.consume()
                        val p = page.corners.points.copyOf()
                        p[2 * dragging] = (p[2 * dragging] + drag.x / perPhotoPixel).coerceIn(0f, page.photo.width.toFloat())
                        p[2 * dragging + 1] = (p[2 * dragging + 1] + drag.y / perPhotoPixel).coerceIn(0f, page.photo.height.toFloat())
                        page.corners = PageFinder.Corners(p)
                    },
                )
            },
        ) {
            drawImage(image, dstOffset = IntOffset(left.toInt(), top.toInt()), dstSize = IntSize((photo.width * fit).toInt(), (photo.height * fit).toInt()))
            val p = page.corners.points
            val outline = Path().apply {
                for (k in 0 until 4) {
                    val o = toScreen(p[2 * k], p[2 * k + 1])
                    if (k == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
                }
                close()
            }
            drawPath(outline, accent.copy(alpha = 0.15f))
            drawPath(outline, accent, style = Stroke(3.dp.toPx()))
            for (k in 0 until 4) {
                val o = toScreen(p[2 * k], p[2 * k + 1])
                drawCircle(Color.White, radius = handle + 3f, center = o)
                drawCircle(accent, radius = handle, center = o)
            }
            // Magnifier: the area around the corner being dragged, three times bigger, clear of the finger.
            if (dragging >= 0) {
                val corner = toScreen(p[2 * dragging], p[2 * dragging + 1])
                val center = Offset(corner.x, (corner.y - loupe * 2).let { if (it < loupe) corner.y + loupe * 2 else it })
                val circle = Path().apply { addOval(Rect(center, loupe)) }
                clipPath(circle) {
                    drawRect(Color.Black, topLeft = center - Offset(loupe, loupe), size = Size(loupe * 2, loupe * 2))
                    val zoom = 3f
                    drawImage(
                        image,
                        dstOffset = IntOffset((center.x - (corner.x - left) * zoom).toInt(), (center.y - (corner.y - top) * zoom).toInt()),
                        dstSize = IntSize((photo.width * fit * zoom).toInt(), (photo.height * fit * zoom).toInt()),
                    )
                }
                drawCircle(Color.White, radius = loupe, center = center, style = Stroke(3.dp.toPx()))
                drawLine(accent, center - Offset(10f, 0f), center + Offset(10f, 0f), strokeWidth = 2f)
                drawLine(accent, center - Offset(0f, 10f), center + Offset(0f, 10f), strokeWidth = 2f)
            }
        }
    }
}

/** What the page will look like: straightened and cleaned, worked out on the small preview. */
@Composable
private fun ResultPreview(page: ScanPage, modifier: Modifier) {
    val result by produceState<Bitmap?>(null, page, page.corners, page.settings) {
        value = withContext(Dispatchers.Default) {
            val factor = page.preview.width.toFloat() / page.photo.width
            PageScanner.process(page.preview, page.corners.scaled(factor), page.settings, maxSide = 1200)
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        result?.let { bitmap ->
            val image = remember(bitmap) { bitmap.asImageBitmap() }
            Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                val fit = minOf(size.width / bitmap.width, size.height / bitmap.height)
                drawImage(
                    image,
                    dstOffset = IntOffset(((size.width - bitmap.width * fit) / 2).toInt(), ((size.height - bitmap.height * fit) / 2).toInt()),
                    dstSize = IntSize((bitmap.width * fit).toInt(), (bitmap.height * fit).toInt()),
                )
            }
        } ?: CircularProgressIndicator(Modifier.size(32.dp))
    }
}

/** The scanner's "framed well" colour. */
private val FRAMED_GREEN = Color(0xFF2E7D32)
