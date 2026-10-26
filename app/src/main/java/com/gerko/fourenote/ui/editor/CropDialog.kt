package com.gerko.fourenote.ui.editor

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Crops a picture: the whole picture with the kept part outlined; drag a corner or an edge to
 * move it, or inside to slide it. [onDone] gets the kept part's edges as fractions (left, top,
 * right, bottom) of the whole picture. Nothing is cut from the stored picture.
 */
@Composable
fun CropDialog(
    file: File,
    initial: Rect,
    onDone: (Rect) -> Unit,
    onCancel: () -> Unit,
) {
    // Read through the attachment store: a locked note's pictures are encrypted on the device.
    val attachments = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.gerko.fourenote.FourENoteApp).attachments
    val bitmap by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) {
            val bytes = runCatching { attachments.readPlain(file) }.getOrNull() ?: return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }
    }
    // The kept part, as fractions of the picture.
    var l by remember { mutableFloatStateOf(initial.left) }
    var t by remember { mutableFloatStateOf(initial.top) }
    var r by remember { mutableFloatStateOf(initial.right) }
    var b by remember { mutableFloatStateOf(initial.bottom) }
    val accent = MaterialTheme.colorScheme.primary
    val handleRadius = with(LocalDensity.current) { 10.dp.toPx() }
    val grab = with(LocalDensity.current) { 32.dp.toPx() }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onCancel) { Text("Cancel", color = Color.White) }
                    Text(
                        "Drag the corners or edges to crop",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { l = 0f; t = 0f; r = 1f; b = 1f }) { Text("Whole picture", color = Color.White) }
                    Button(onClick = { onDone(Rect(l, t, r, b)) }) { Text("Done") }
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    val image = bitmap
                    if (image == null) {
                        CircularProgressIndicator()
                        return@BoxWithConstraints
                    }
                    // Where the whole picture is drawn (fitted, centred), in pixels.
                    val fit = minOf(constraints.maxWidth.toFloat() / image.width, constraints.maxHeight.toFloat() / image.height)
                    val shown = Size(image.width * fit, image.height * fit)
                    val origin = Offset((constraints.maxWidth - shown.width) / 2, (constraints.maxHeight - shown.height) / 2)
                    var dragging by remember { mutableStateOf<String?>(null) }
                    Box(Modifier.fillMaxSize()) {
                        Image(image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                        Canvas(
                            Modifier.fillMaxSize().pointerInput(image) {
                                val min = 0.05f
                                detectDragGestures(
                                    onDragStart = { at ->
                                        fun near(x: Float, y: Float) =
                                            (at - Offset(origin.x + x * shown.width, origin.y + y * shown.height)).getDistance() < grab
                                        val inX = at.x in (origin.x + l * shown.width)..(origin.x + r * shown.width)
                                        val inY = at.y in (origin.y + t * shown.height)..(origin.y + b * shown.height)
                                        dragging = when {
                                            near(l, t) -> "lt"
                                            near(r, t) -> "rt"
                                            near(l, b) -> "lb"
                                            near(r, b) -> "rb"
                                            inY && kotlin.math.abs(at.x - (origin.x + l * shown.width)) < grab -> "l"
                                            inY && kotlin.math.abs(at.x - (origin.x + r * shown.width)) < grab -> "r"
                                            inX && kotlin.math.abs(at.y - (origin.y + t * shown.height)) < grab -> "t"
                                            inX && kotlin.math.abs(at.y - (origin.y + b * shown.height)) < grab -> "b"
                                            inX && inY -> "move"
                                            else -> null
                                        }
                                    },
                                    onDragEnd = { dragging = null },
                                    onDragCancel = { dragging = null },
                                ) { change, amount ->
                                    change.consume()
                                    val dx = amount.x / shown.width
                                    val dy = amount.y / shown.height
                                    val what = dragging ?: return@detectDragGestures
                                    if (what == "move") {
                                        val mx = dx.coerceIn(-l, 1f - r)
                                        val my = dy.coerceIn(-t, 1f - b)
                                        l += mx; r += mx; t += my; b += my
                                    } else {
                                        if ('l' in what) l = (l + dx).coerceIn(0f, r - min)
                                        if ('r' in what) r = (r + dx).coerceIn(l + min, 1f)
                                        if ('t' in what) t = (t + dy).coerceIn(0f, b - min)
                                        if ('b' in what) b = (b + dy).coerceIn(t + min, 1f)
                                    }
                                }
                            },
                        ) {
                            val keep = Rect(
                                origin.x + l * shown.width, origin.y + t * shown.height,
                                origin.x + r * shown.width, origin.y + b * shown.height,
                            )
                            // Darken what is cut away.
                            val shade = Color.Black.copy(alpha = 0.6f)
                            drawRect(shade, Offset(origin.x, origin.y), Size(shown.width, keep.top - origin.y))
                            drawRect(shade, Offset(origin.x, keep.bottom), Size(shown.width, origin.y + shown.height - keep.bottom))
                            drawRect(shade, Offset(origin.x, keep.top), Size(keep.left - origin.x, keep.height))
                            drawRect(shade, Offset(keep.right, keep.top), Size(origin.x + shown.width - keep.right, keep.height))
                            drawRect(accent, keep.topLeft, keep.size, style = Stroke(3.dp.toPx()))
                            for (corner in listOf(keep.topLeft, keep.topRight, keep.bottomLeft, keep.bottomRight)) {
                                drawCircle(Color.White, handleRadius + 3, corner)
                                drawCircle(accent, handleRadius, corner)
                            }
                        }
                    }
                }
            }
        }
    }
}
