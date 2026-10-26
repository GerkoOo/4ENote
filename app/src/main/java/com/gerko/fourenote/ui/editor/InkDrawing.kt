package com.gerko.fourenote.ui.editor

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.gerko.fourenote.data.ink.Calligraphy
import com.gerko.fourenote.data.ink.InkStroke
import com.gerko.fourenote.data.ink.PageImage
import com.gerko.fourenote.data.ink.PaperStyle
import com.gerko.fourenote.data.ink.outline
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt

internal val PaperColor = Color(0xFFFFFDF7)
internal val RuleColor = Color(0xFFB9D3EA)
internal val MarginColor = Color(0xFFF0A8A8)
internal val DarkPaperColor = Color(0xFF1E1F24)
internal val DarkRuleColor = Color(0xFF343E4A)
internal val DarkMarginColor = Color(0xFF5C3B40)


internal const val HIGHLIGHTER_ALPHA = 0.35f
internal const val ERASER_RADIUS_DP = 12f
internal const val SELECTION_PADDING_DP = 10f
internal const val HANDLE_RADIUS_DP = 8f

/** Above this many pixels (a page zoomed in far) the ink is drawn directly instead of kept as a picture. */
internal const val MAX_CACHED_PIXELS = 16_000_000f
internal const val HANDLE_TOUCH_DP = 28f
internal const val MIN_SCALE = 0.2f
internal const val MAX_SCALE = 8f

internal enum class ImageGesture { MOVE, SCALE, SCALE_X, SCALE_Y, ROTATE }

/** Where a picture's handles sit, in its own frame relative to its half-size: the corner and above the top edge. */
internal val SCALE_HANDLE = 1f to 1f
internal val SCALE_X_HANDLE = 1f to 0f
internal val SCALE_Y_HANDLE = 0f to 1f
internal val ROTATE_HANDLE = 0f to -1f
internal const val ROTATE_HANDLE_GAP = 28f

internal fun PageImage.handlePosition(handle: Pair<Float, Float>): Offset {
    val lx = handle.first * width / 2
    val ly = handle.second * height / 2 - if (handle == ROTATE_HANDLE) ROTATE_HANDLE_GAP else 0f
    val (px, py) = toPage(lx, ly)
    return Offset(px, py)
}

internal fun PageImage.handleDistance(point: Offset, handle: Pair<Float, Float>) =
    (point - handlePosition(handle)).getDistance()

/** Dashed frame, scale dots (corner, right edge, bottom edge) and rotate handle around the selected picture. */
internal fun DrawScope.drawImageFrame(image: PageImage, scale: Float, accent: Color, handles: Boolean = true) {
    val corners = listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f).map { (sx, sy) ->
        val (px, py) = image.toPage(sx * image.width / 2, sy * image.height / 2)
        Offset(px * scale, py * scale)
    }
    val frame = Path().apply {
        moveTo(corners[0].x, corners[0].y)
        corners.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(frame, accent, style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
    if (!handles) return
    val topMiddle = image.toPage(0f, -image.height / 2).let { Offset(it.first * scale, it.second * scale) }
    val rotateAt = image.handlePosition(ROTATE_HANDLE).let { Offset(it.x * scale, it.y * scale) }
    drawLine(accent, topMiddle, rotateAt, strokeWidth = 2f)
    val dots = listOf(SCALE_HANDLE, SCALE_X_HANDLE, SCALE_Y_HANDLE).map { h -> image.handlePosition(h).let { Offset(it.x * scale, it.y * scale) } }
    for (handle in listOf(rotateAt) + dots) {
        drawCircle(Color.White, radius = (HANDLE_RADIUS_DP + 2f) * scale, center = handle)
        drawCircle(accent, radius = HANDLE_RADIUS_DP * scale, center = handle)
    }
}

/** Box around the selected strokes in dp, padded so thin strokes are easy to grab. */
internal fun selectionBounds(selection: Set<InkStroke>): Rect? {
    if (selection.isEmpty()) return null
    var left = Float.MAX_VALUE
    var top = Float.MAX_VALUE
    var right = -Float.MAX_VALUE
    var bottom = -Float.MAX_VALUE
    for (stroke in selection) {
        val half = stroke.width / 2
        for (i in stroke.points.indices step 2) {
            left = minOf(left, stroke.points[i] - half)
            right = maxOf(right, stroke.points[i] + half)
            top = minOf(top, stroke.points[i + 1] - half)
            bottom = maxOf(bottom, stroke.points[i + 1] + half)
        }
    }
    return Rect(left - SELECTION_PADDING_DP, top - SELECTION_PADDING_DP, right + SELECTION_PADDING_DP, bottom + SELECTION_PADDING_DP)
}

internal fun Color.toArgbInt(): Int =
    android.graphics.Color.argb(
        (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(),
    )

/** Builds a smoothed path through the stroke's points using midpoint quadratics. */
fun InkStroke.toPath(): Path {
    if (nibAngle != null) return nibPath()
    if (widths != null) return outlinePath()
    val path = Path()
    val n = points.size / 2
    if (n == 0) return path
    path.moveTo(points[0], points[1])
    if (n == 1) {
        // A tap: a tiny segment so the round cap draws a dot.
        path.lineTo(points[0] + 0.01f, points[1])
        return path
    }
    for (i in 1 until n - 1) {
        val x = points[2 * i]
        val y = points[2 * i + 1]
        val mx = (x + points[2 * i + 2]) / 2
        val my = (y + points[2 * i + 3]) / 2
        path.quadraticTo(x, y, mx, my)
    }
    path.lineTo(points[2 * n - 2], points[2 * n - 1])
    return path
}

/** A calligraphy stroke: the union of the flat nib's sweep, with sharp corners and ends. */
private fun InkStroke.nibPath(): Path {
    val path = Path()
    for (shape in Calligraphy.nibShapes(this)) {
        if (shape.size < 6) continue
        path.moveTo(shape[0], shape[1])
        for (i in 1 until shape.size / 2) path.lineTo(shape[2 * i], shape[2 * i + 1])
        path.close()
    }
    return path
}

/** The filled shape of a variable-width stroke, with round ends unless it has [InkStroke.squareEnds]. */
private fun InkStroke.outlinePath(): Path {
    val path = Path()
    val n = points.size / 2
    val factors = widths ?: return path
    if (n == 0) return path
    fun cap(i: Int) {
        val r = width * factors[i] / 2
        path.addOval(Rect(Offset(points[2 * i], points[2 * i + 1]), r))
    }
    val shape = outline()
    if (shape.isNotEmpty()) {
        path.moveTo(shape[0], shape[1])
        for (i in 1 until shape.size / 2) path.lineTo(shape[2 * i], shape[2 * i + 1])
        path.close()
    }
    if (!squareEnds) {
        cap(0)
        cap(n - 1)
    }
    return path
}

/** Draws [stroke] in page units; callers apply any scaling transform. */
fun DrawScope.drawInkStroke(stroke: InkStroke, path: Path, darkPaper: Boolean = false) {
    // Ink always shows its real color; dark mode only changes the paper.
    val color = Color(stroke.color)
    if (stroke.widths != null || stroke.nibAngle != null) {
        drawPath(path = path, color = color)
    } else if (stroke.highlighter) {
        drawPath(
            path = path,
            color = Color(stroke.color).copy(alpha = if (darkPaper) 0.45f else HIGHLIGHTER_ALPHA),
            style = Stroke(width = stroke.width, cap = StrokeCap.Square, join = StrokeJoin.Round),
            // Multiply makes highlights look like ink on white paper, but would vanish on dark paper.
            blendMode = if (darkPaper) BlendMode.SrcOver else BlendMode.Multiply,
        )
    } else {
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = stroke.width, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

fun DrawScope.drawPaper(paper: PaperStyle, scale: Float = density, dark: Boolean = false) {
    val ruleColor = if (dark) DarkRuleColor else RuleColor
    val marginColor = if (dark) DarkMarginColor else MarginColor
    drawRect(if (dark) DarkPaperColor else PaperColor)
    clipRect {
        when (paper) {
            PaperStyle.BLANK -> Unit
            PaperStyle.LINED -> {
                val spacing = 32f * scale
                var y = spacing * 2
                while (y < size.height) {
                    drawLine(ruleColor, Offset(0f, y), Offset(size.width, y), strokeWidth = scale)
                    y += spacing
                }
                val margin = 72f * scale
                drawLine(marginColor, Offset(margin, 0f), Offset(margin, size.height), strokeWidth = scale)
            }
            PaperStyle.GRID -> {
                val spacing = 24f * scale
                var x = spacing
                while (x < size.width) {
                    drawLine(ruleColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = scale * 0.6f)
                    x += spacing
                }
                var y = spacing
                while (y < size.height) {
                    drawLine(ruleColor, Offset(0f, y), Offset(size.width, y), strokeWidth = scale * 0.6f)
                    y += spacing
                }
            }
            PaperStyle.DOTS -> {
                // A dot where each pair of grid lines would cross.
                val spacing = 24f * scale
                var y = spacing
                while (y < size.height) {
                    var x = spacing
                    while (x < size.width) {
                        drawCircle(ruleColor, radius = scale * 1.1f, center = Offset(x, y))
                        x += spacing
                    }
                    y += spacing
                }
            }
        }
    }
}

/** True if any part of [stroke] lies within [radius] dp of ([x], [y]). */
fun InkStroke.isHitBy(x: Float, y: Float, radius: Float): Boolean {
    val reach = radius + width / 2
    val n = points.size / 2
    if (n == 1) return hypot(points[0] - x, points[1] - y) <= reach
    for (i in 0 until n - 1) {
        if (distanceToSegment(
                x, y,
                points[2 * i], points[2 * i + 1],
                points[2 * i + 2], points[2 * i + 3],
            ) <= reach
        ) return true
    }
    return false
}

/**
 * Partial erase: returns the pieces of this stroke left after rubbing out
 * everything within [radius] dp of ([x], [y]), or null if the eraser missed.
 * Segments near the eraser are subdivided so the cut follows the eraser's
 * edge; segments far from it are kept as they are.
 */
fun InkStroke.erasedAround(
    x: Float,
    y: Float,
    radius: Float,
    /** What the pieces replace (see [InkStroke.replaces]): this stroke, or what it was cut from earlier in the same rub. */
    replacing: String? = id,
): List<InkStroke>? {
    if (!isHitBy(x, y, radius)) return null
    val reach = radius + width / 2
    val step = maxOf(0.5f, radius / 4)
    val pieces = mutableListOf<InkStroke>()
    var run = ArrayList<Float>()
    var runWidths = ArrayList<Float>()
    val factors = widths

    fun flush() {
        // A piece needs at least two points to draw as a line.
        if (run.size >= 4) {
            pieces.add(
                InkStroke(color, width, highlighter, run.toFloatArray(), factors?.let { runWidths.toFloatArray() }, layerId, nibAngle = nibAngle, squareEnds = squareEnds, replaces = replacing),
            )
        }
        run = ArrayList()
        runWidths = ArrayList()
    }

    fun visit(px: Float, py: Float, w: Float) {
        if (hypot(px - x, py - y) <= reach) {
            flush()
        } else {
            run.add(px)
            run.add(py)
            runWidths.add(w)
        }
    }

    fun factor(i: Int) = factors?.getOrNull(i) ?: 1f

    visit(points[0], points[1], factor(0))
    for (i in 1 until points.size / 2) {
        val ax = points[2 * i - 2]
        val ay = points[2 * i - 1]
        val bx = points[2 * i]
        val by = points[2 * i + 1]
        if (distanceToSegment(x, y, ax, ay, bx, by) > reach) {
            visit(bx, by, factor(i))
            continue
        }
        val steps = ceil(hypot(bx - ax, by - ay) / step).toInt().coerceAtLeast(1)
        for (j in 1..steps) {
            val t = j.toFloat() / steps
            visit(ax + (bx - ax) * t, ay + (by - ay) * t, factor(i - 1) + (factor(i) - factor(i - 1)) * t)
        }
    }
    flush()
    return pieces
}

private fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax
    val dy = by - ay
    val lengthSq = dx * dx + dy * dy
    val t = if (lengthSq == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / lengthSq).coerceIn(0f, 1f)
    return hypot(px - (ax + t * dx), py - (ay + t * dy))
}

internal const val BUTTON_PRIMARY = PenKeys.STYLUS_PRIMARY
internal const val BUTTON_SECONDARY = PenKeys.STYLUS_SECONDARY

/**
 * The pen side buttons held during [this] event. Compose reports the first stylus button as
 * isPrimaryPressed (shared with a mouse's left button), so the raw MotionEvent is read when it's there.
 */
internal fun PointerEvent.stylusButtons(): Int {
    motionEvent?.let { return it.buttonState and (BUTTON_PRIMARY or BUTTON_SECONDARY) }
    return (if (buttons.isPrimaryPressed) BUTTON_PRIMARY else 0) or (if (buttons.isSecondaryPressed) BUTTON_SECONDARY else 0)
}

internal fun describePenButtons(buttons: Int, type: PointerType, touching: Boolean): String {
    val names = listOfNotNull(
        "button 1".takeIf { buttons and BUTTON_PRIMARY != 0 },
        "button 2".takeIf { buttons and BUTTON_SECONDARY != 0 },
    )
    val end = if (type == PointerType.Eraser) "Eraser end" else "Pen"
    val where = if (touching) "touching" else "hovering"
    return "$end $where: " + (names.joinToString(" + ").ifEmpty { "buttons released" })
}

/** The pointed pen's width at each point: bold going down, a hairline going up, scaled by pen pressure. */
internal fun pointedWidths(points: FloatArray, pressures: FloatArray, slantDegrees: Float): FloatArray {
    val direction = Calligraphy.pointedFactors(points, Calligraphy.nibRadians(slantDegrees))
    val pressure = Calligraphy.pressureFactors(pressures)
    return FloatArray(direction.size) { i -> direction[i] * (pressure.getOrNull(i) ?: 1f).coerceIn(0.6f, 1.2f) }
}

/**
 * A page's paper, pictures and finished ink, kept as one picture at screen resolution. When the
 * strokes only grew (a stroke was added) just the new ones are painted onto it; anything else
 * (erasing, undo, zoom, paper or pictures changing) paints it afresh.
 */
internal class InkBitmapCache(private val budget: InkCacheBudget) {
    private var bitmap: ImageBitmap? = null
    private var baseKey: Any? = null
    private var drawn: List<InkStroke> = emptyList()
    private val painter = androidx.compose.ui.graphics.drawscope.CanvasDrawScope()

    fun draw(
        scope: DrawScope,
        key: Any,
        strokes: List<InkStroke>,
        drawBase: DrawScope.() -> Unit,
        drawStrokes: DrawScope.(List<InkStroke>) -> Unit,
        zoom: Float = 1f,
    ) {
        if (bitmap == null) budget.register()
        // Painted at the zoomed size so it stays sharp, but within this page's share of the
        // memory budget, and in steps so pinching doesn't make a new picture every frame.
        val logical = scope.size.width * scope.size.height
        val resolution = snappedResolution(
            minOf(zoom.coerceAtLeast(1f), kotlin.math.sqrt(budget.pixelsPerPage() / logical.coerceAtLeast(1f))),
        )
        val width = (scope.size.width * resolution).roundToInt().coerceAtLeast(1)
        val height = (scope.size.height * resolution).roundToInt().coerceAtLeast(1)
        var picture = bitmap
        val sameSize = picture != null && picture.width == width && picture.height == height
        if (!sameSize) picture = ImageBitmap(width, height)
        val grown = sameSize && key == baseKey && drawn.size <= strokes.size && drawn.indices.all { drawn[it] === strokes[it] }
        val canvas = androidx.compose.ui.graphics.Canvas(picture)
        canvas.save()
        canvas.scale(width / scope.size.width, height / scope.size.height)
        painter.draw(scope, scope.layoutDirection, canvas, scope.size) {
            if (grown) {
                if (strokes.size > drawn.size) drawStrokes(strokes.subList(drawn.size, strokes.size))
            } else {
                drawRect(Color.Transparent, blendMode = BlendMode.Clear)
                drawBase()
                drawStrokes(strokes)
            }
        }
        canvas.restore()
        bitmap = picture
        baseKey = key
        drawn = strokes
        scope.drawImage(
            picture,
            dstSize = IntSize(scope.size.width.roundToInt(), scope.size.height.roundToInt()),
            filterQuality = FilterQuality.Medium,
        )
    }

    /** Lets go of the picture and its share of the budget. */
    fun release() {
        if (bitmap == null) return
        bitmap = null
        baseKey = null
        drawn = emptyList()
        budget.unregister()
    }
}

/** An empty frame waiting for a photo from another device: dashed outline, a camera and a hint. */
internal fun DrawScope.drawPhotoRequest(image: PageImage, scale: Float, accent: Color) {
    val topLeft = Offset((image.x - image.width / 2) * scale, (image.y - image.height / 2) * scale)
    val box = Size(image.width * scale, image.height * scale)
    drawRect(accent.copy(alpha = 0.08f), topLeft = topLeft, size = box)
    drawRect(
        accent,
        topLeft = topLeft,
        size = box,
        style = Stroke(2.5f * scale, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f * scale, 9f * scale))),
    )
    val center = Offset(image.x * scale, image.y * scale)
    val unit = minOf(box.width, box.height) / 10f
    // A simple camera: body, lens and viewfinder bump.
    drawRoundRect(
        accent,
        topLeft = center - Offset(unit * 1.6f, unit * 1.1f),
        size = Size(unit * 3.2f, unit * 2.2f),
        cornerRadius = CornerRadius(unit * 0.4f),
        style = Stroke(unit * 0.25f),
    )
    drawCircle(accent, radius = unit * 0.6f, center = center, style = Stroke(unit * 0.25f))
    drawRect(accent, topLeft = center - Offset(unit * 0.6f, unit * 1.5f), size = Size(unit * 1.2f, unit * 0.4f))
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = accent.toArgbInt()
            textSize = (unit * 0.7f).coerceIn(9f * scale, 22f * scale)
            textAlign = android.graphics.Paint.Align.CENTER
        }
        canvas.nativeCanvas.drawText("Waiting for a photo from your phone", center.x, center.y + unit * 2.3f, paint)
        if (image.label.isNotEmpty()) {
            paint.textSize *= 1.4f
            paint.isFakeBoldText = true
            canvas.nativeCanvas.drawText(image.label, center.x, center.y - unit * 2.2f, paint)
        }
    }
}

// How far the pen may move and still count as a tap.
internal const val TAP_SLOP_DP = 8f
