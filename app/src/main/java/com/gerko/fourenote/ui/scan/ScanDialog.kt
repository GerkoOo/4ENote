package com.gerko.fourenote.ui.scan

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.gerko.fourenote.data.scan.PageFinder
import com.gerko.fourenote.data.scan.PageScanner
import com.gerko.fourenote.data.scan.ScanFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.hypot

/** One photographed page: the photo, where its corners are, and how it will be cleaned up. */
private class ScanPage(val photo: Bitmap, val preview: Bitmap, corners: PageFinder.Corners, filter: ScanFilter) {
    var corners by mutableStateOf(corners)
    var filter by mutableStateOf(filter)
}

/**
 * The scanner: photograph pages with the camera app (or pick photos), check or drag each
 * page's corners, choose how it is cleaned up, and add the straightened pages to the note as
 * JPEGs ([onDone] gets each page's file content). Everything runs on the device.
 */
@Composable
fun ScanDialog(decode: (File) -> Bitmap?, onDone: (List<ByteArray>) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pages = remember { mutableStateListOf<ScanPage>() }
    var current by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var lastFilter by remember { mutableStateOf(ScanFilter.CLEAN) }
    var replaceCurrent by remember { mutableStateOf(false) }
    val shot = remember { File(File(context.cacheDir, "scans").apply { mkdirs() }, "shot.jpg") }

    fun addPhoto(file: File) {
        scope.launch {
            busy = true
            val page = withContext(Dispatchers.Default) {
                val photo = decode(file) ?: return@withContext null
                val scale = minOf(1f, 1200f / maxOf(photo.width, photo.height))
                val preview = Bitmap.createScaledBitmap(photo, (photo.width * scale).toInt().coerceAtLeast(1), (photo.height * scale).toInt().coerceAtLeast(1), true)
                ScanPage(photo, preview, PageScanner.findCorners(photo), lastFilter)
            }
            busy = false
            if (page == null) return@launch
            if (replaceCurrent && pages.isNotEmpty()) pages[current] = page else {
                pages += page
                current = pages.lastIndex
            }
            replaceCurrent = false
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken) addPhoto(shot)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val copy = File(shot.parentFile, "picked.jpg")
        context.contentResolver.openInputStream(uri)?.use { input -> copy.outputStream().use { input.copyTo(it) } }
        addPhoto(copy)
    }
    fun takePhoto(replace: Boolean = false) {
        replaceCurrent = replace
        camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", shot))
    }
    LaunchedEffect(Unit) { takePhoto() }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Cancel scanning") }
                    Text(
                        if (pages.isEmpty()) "Scan" else "Page ${current + 1} of ${pages.size} · drag the corners to fit the page",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { current-- }, enabled = current > 0) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous page")
                    }
                    IconButton(onClick = { current++ }, enabled = current < pages.lastIndex) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next page")
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val page = pages.getOrNull(current)
                    if (page != null) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CornerEditor(page, Modifier.weight(1f).fillMaxSize())
                            ResultPreview(page, Modifier.weight(1f).fillMaxSize())
                        }
                    }
                    if (page == null && !busy) {
                        // No page yet (the camera was closed without a photo): offer both ways in.
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(onClick = { takePhoto() }) {
                                Icon(Icons.Filled.AddAPhoto, contentDescription = null)
                                Text("  Take photo")
                            }
                            Button(onClick = { replaceCurrent = false; gallery.launch("image/*") }) {
                                Icon(Icons.Filled.Image, contentDescription = null)
                                Text("  From photos")
                            }
                        }
                    }
                    if (busy) CircularProgressIndicator()
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    val page = pages.getOrNull(current)
                    ScanFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = page?.filter == filter,
                            onClick = { page?.filter = filter; lastFilter = filter },
                            label = { Text(filter.label) },
                            enabled = page != null,
                        )
                    }
                    TextButton(onClick = { takePhoto(replace = true) }, enabled = page != null) { Text("Retake") }
                    IconButton(onClick = {
                        if (page != null) {
                            pages.removeAt(current)
                            current = current.coerceAtMost(pages.lastIndex).coerceAtLeast(0)
                        }
                    }, enabled = page != null) { Icon(Icons.Filled.Delete, contentDescription = "Remove this page") }
                    TextButton(onClick = { takePhoto() }) {
                        Icon(Icons.Filled.AddAPhoto, contentDescription = null)
                        Text("  Add page")
                    }
                    TextButton(onClick = { replaceCurrent = false; gallery.launch("image/*") }) {
                        Icon(Icons.Filled.Image, contentDescription = null)
                        Text("  From photos")
                    }
                    Button(
                        enabled = pages.isNotEmpty() && !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                val files = withContext(Dispatchers.Default) {
                                    pages.map { p ->
                                        val result = PageScanner.process(p.photo, p.corners, p.filter)
                                        ByteArrayOutputStream().also { result.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
                                            .also { result.recycle() }
                                    }
                                }
                                busy = false
                                onDone(files)
                            }
                        },
                    ) { Text(if (pages.size > 1) "Add ${pages.size} pages" else "Add page") }
                }
            }
        }
    }
}

/** The photo with the page outline; the nearest corner follows a drag. */
@Composable
private fun CornerEditor(page: ScanPage, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val handle = with(LocalDensity.current) { 14.dp.toPx() }
    val grab = with(LocalDensity.current) { 64.dp.toPx() }
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
                        dragging = (0 until 4).minBy { hypot(toScreen(p[2 * it], p[2 * it + 1]).x - at.x, toScreen(p[2 * it], p[2 * it + 1]).y - at.y) }
                            .takeIf { hypot(toScreen(p[2 * it], p[2 * it + 1]).x - at.x, toScreen(p[2 * it], p[2 * it + 1]).y - at.y) < grab } ?: -1
                    },
                    onDragEnd = { dragging = -1 },
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
            drawImage(
                image,
                dstOffset = IntOffset(left.toInt(), top.toInt()),
                dstSize = IntSize((photo.width * fit).toInt(), (photo.height * fit).toInt()),
            )
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
        }
    }
}

/** What the page will look like: straightened and cleaned, worked out on the small preview. */
@Composable
private fun ResultPreview(page: ScanPage, modifier: Modifier) {
    val result by produceState<Bitmap?>(null, page, page.corners, page.filter) {
        value = withContext(Dispatchers.Default) {
            val factor = page.preview.width.toFloat() / page.photo.width
            PageScanner.process(page.preview, page.corners.scaled(factor), page.filter, maxSide = 1200)
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
