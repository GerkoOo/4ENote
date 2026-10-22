package com.gerko.fourenote.ui.editor

import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.abs
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.input.pointer.pointerInput
import com.gerko.fourenote.data.ink.BackgroundPlacement
import com.gerko.fourenote.data.ink.PageImage
import kotlin.math.atan2
import com.gerko.fourenote.data.ink.Calligraphy
import com.gerko.fourenote.data.ink.InkPen
import com.gerko.fourenote.data.ink.InkStroke
import com.gerko.fourenote.data.ink.outline
import com.gerko.fourenote.data.ink.PaperStyle
import com.gerko.fourenote.data.ink.Shapes
import com.gerko.fourenote.data.ink.Smoothing
import com.gerko.fourenote.data.ink.Tool
import java.util.WeakHashMap
import kotlin.math.ceil
import kotlin.math.hypot

val PaperColor = Color(0xFFFFFDF7)
private val RuleColor = Color(0xFFB9D3EA)
private val MarginColor = Color(0xFFF0A8A8)
val DarkPaperColor = Color(0xFF1E1F24)
private val DarkRuleColor = Color(0xFF343E4A)
private val DarkMarginColor = Color(0xFF5C3B40)


const val HIGHLIGHTER_ALPHA = 0.35f
const val ERASER_RADIUS_DP = 12f
private const val SELECTION_PADDING_DP = 10f
private const val HANDLE_RADIUS_DP = 8f

/** Above this many pixels (a page zoomed in far) the ink is drawn directly instead of kept as a picture. */
private const val MAX_CACHED_PIXELS = 16_000_000f
private const val HANDLE_TOUCH_DP = 28f
private const val MIN_SCALE = 0.2f
private const val TWO_FINGER_HOLD_MS = 450L
private const val TWO_FINGER_SLOP_PX = 24f
private const val MULTI_FINGER_TAP_MS = 300L
private const val PAGE_HOLD_MS = 450L
private const val SHAPE_HOLD_MS = 500L
private const val LOOP_HOLD_MS = 500L
private const val HOLD_SLOP_PX = 6f
private const val MAX_SCALE = 8f

/** RESIZE_X and RESIZE_Y stretch one axis; RESIZE_XY scales both axes independently. */
private enum class LassoGesture { DRAW, MOVE, RESIZE_XY, RESIZE_X, RESIZE_Y }

/**
 * Drawing surface for one page. Strokes are captured in page units, shown
 * at [scale] pixels per unit, over the page's [background] image or ruled
 * [paper]. Dragging with two fingers scrolls the page list via [onScroll]
 * instead of drawing. A stylus's eraser end always erases, and with
 * [stylusOnly] finger touches are ignored so a resting palm draws nothing.
 *
 * With [Tool.LASSO], drawing a loop selects strokes; dragging inside the
 * selection moves it. The corner handle resizes width and height freely,
 * the right-edge handle stretches only the width and the bottom-edge handle
 * only the height. The canvas
 * previews moves locally and reports the final transform once on release.
 */
@Composable
fun InkCanvas(
    strokes: List<InkStroke>,
    scale: Float,
    background: ImageBitmap?,
    darkPaper: Boolean,
    selection: Set<InkStroke>,
    paper: PaperStyle,
    tool: Tool,
    color: Color,
    width: Float,
    stylusOnly: Boolean,
    smoothing: Int,
    nibDegrees: Float,
    onStroke: (InkStroke) -> Unit,
    onEraseStart: () -> Unit,
    onErase: (x: Float, y: Float) -> Unit,
    onEraseEnd: () -> Unit,
    onLasso: (polygon: FloatArray) -> Unit,
    onTransformSelection: (
        dx: Float, dy: Float, scaleX: Float, scaleY: Float, pivotX: Float, pivotY: Float,
    ) -> Unit,
    onScroll: (dxPx: Float, dyPx: Float) -> Unit,
    /** Two fingers lifted after scrolling or zooming. */
    onScrollEnd: () -> Unit = {},
    onZoom: (factor: Float) -> Unit,
    onTwoFingerHold: (rootPx: Offset) -> Unit,
    modifier: Modifier = Modifier,
    placement: BackgroundPlacement = BackgroundPlacement(),
    adjustingBackground: Boolean = false,
    onPlacementChange: (BackgroundPlacement) -> Unit = {},
    invertBackground: Boolean = false,
    images: List<PageImage> = emptyList(),
    imageBitmaps: Map<String, ImageBitmap> = emptyMap(),
    selectedImageId: String? = null,
    /** Holding the pen and a finger on a picture selects it, whatever the tool. */
    penAndFingerSelect: Boolean = false,
    onImageSelect: (String?) -> Unit = {},
    onImageChange: (PageImage) -> Unit = {},
    primaryButtonAction: PenButtonAction = PenButtonAction.OPEN_DIAL,
    secondaryButtonAction: PenButtonAction = PenButtonAction.ERASE,
    /** A pen button clicked while hovering, or held when touching down with a dial/last-tool action; root position. */
    onPenButtonClick: (PenButtonAction, Offset) -> Unit = { _, _ -> },
    /** Describes each pen button or eraser signal, so the settings can show what the pen sends. */
    onPenSignal: (String) -> Unit = {},
    onMultiFingerTap: (fingers: Int) -> Unit = {},
    /** Zoomed out: fingers only scroll, tap and move pages (pens still write). */
    overview: Boolean = false,
    /** A quick one-finger tap on the page while fingers don't draw. */
    onFingerTap: () -> Unit = {},
    /** In the [overview], holding a finger still on the page picks it up to move: start, move and drop, in root pixels. */
    onPageDragStart: (Offset) -> Unit = {},
    onPageDrag: (Offset) -> Unit = {},
    onPageDragEnd: () -> Unit = {},
    /** A loop just drawn on this page; long-pressing inside it with a finger calls [onLoopHold]. */
    loop: InkStroke? = null,
    onLoopHold: () -> Unit = {},
    /** Calligraphy as a pointed pen (bold down-strokes) instead of a flat nib. */
    pointedNib: Boolean = true,
    /** A tap with the text tool, in page units. */
    onTextTap: (x: Float, y: Float) -> Unit = { _, _ -> },
    /** Color and width of a tool, for strokes drawn with a tool other than the selected one (pen button). */
    styleFor: (Tool) -> Pair<Color, Float> = { color to width },
) {
    val currentPrimaryAction by rememberUpdatedState(primaryButtonAction)
    val currentSecondaryAction by rememberUpdatedState(secondaryButtonAction)
    val currentOnPenSignal by rememberUpdatedState(onPenSignal)
    val currentOnPenButtonClick by rememberUpdatedState(onPenButtonClick)
    val currentOnMultiFingerTap by rememberUpdatedState(onMultiFingerTap)
    val currentStyleFor by rememberUpdatedState(styleFor)
    // The tool of the stroke being drawn, which the pen button can make differ from [tool].
    var liveTool by remember { mutableStateOf<Tool?>(null) }
    // The picture being dragged, scaled or rotated, previewed here and saved once on release.
    var liveImage by remember { mutableStateOf<PageImage?>(null) }
    val currentImages by rememberUpdatedState(images)
    val currentSelectedImageId by rememberUpdatedState(selectedImageId)
    val currentOnImageSelect by rememberUpdatedState(onImageSelect)
    val currentPenAndFingerSelect by rememberUpdatedState(penAndFingerSelect)
    val currentOnImageChange by rememberUpdatedState(onImageChange)
    // While adjusting, the image follows the fingers here and is saved once on release.
    var livePlacement by remember { mutableStateOf<BackgroundPlacement?>(null) }
    val currentPlacement by rememberUpdatedState(placement)
    val currentAdjusting by rememberUpdatedState(adjustingBackground)
    val currentOnPlacementChange by rememberUpdatedState(onPlacementChange)
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentOnHold by rememberUpdatedState(onTwoFingerHold)
    val current = remember { mutableStateListOf<Offset>() }
    // Ink pen: when and how hard each point of [current] was drawn.
    val currentTimes = remember { ArrayList<Long>() }
    val currentPressures = remember { ArrayList<Float>() }
    val lasso = remember { mutableStateListOf<Offset>() }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragScaleX by remember { mutableStateOf(1f) }
    var dragScaleY by remember { mutableStateOf(1f) }
    var eraserAt by remember { mutableStateOf<Offset?>(null) }
    val pathCache = remember { WeakHashMap<InkStroke, Path>() }
    val inkCache = remember { InkBitmapCache() }
    val bounds = remember(selection) { selectionBounds(selection) }
    val accent = MaterialTheme.colorScheme.primary

    val currentTool by rememberUpdatedState(tool)
    val currentColor by rememberUpdatedState(color)
    val currentWidth by rememberUpdatedState(width)
    val currentStylusOnly by rememberUpdatedState(stylusOnly)
    val currentOverview by rememberUpdatedState(overview)
    val currentOnFingerTap by rememberUpdatedState(onFingerTap)
    val currentOnPageDragStart by rememberUpdatedState(onPageDragStart)
    val currentOnPageDrag by rememberUpdatedState(onPageDrag)
    val currentOnPageDragEnd by rememberUpdatedState(onPageDragEnd)
    val currentLoop by rememberUpdatedState(loop)
    val currentOnLoopHold by rememberUpdatedState(onLoopHold)
    val currentOnTextTap by rememberUpdatedState(onTextTap)
    val currentPointedNib by rememberUpdatedState(pointedNib)
    val currentSmoothing by rememberUpdatedState(smoothing)
    val currentNibDegrees by rememberUpdatedState(nibDegrees)
    val currentScale by rememberUpdatedState(scale)
    val currentOnScroll by rememberUpdatedState(onScroll)
    val currentOnScrollEnd by rememberUpdatedState(onScrollEnd)
    val currentOnZoom by rememberUpdatedState(onZoom)
    val currentBounds by rememberUpdatedState(bounds)
    val currentOnStroke by rememberUpdatedState(onStroke)
    val currentOnEraseStart by rememberUpdatedState(onEraseStart)
    val currentOnErase by rememberUpdatedState(onErase)
    val currentOnEraseEnd by rememberUpdatedState(onEraseEnd)
    val currentOnLasso by rememberUpdatedState(onLasso)
    val currentOnTransform by rememberUpdatedState(onTransformSelection)

    Box(
        modifier = modifier.clipToBounds().onGloballyPositioned { coordinates = it }.pointerInput(Unit) {
            // Pen buttons clicked while hovering (pressed and released without touching the screen).
            awaitPointerEventScope {
                var lastButtons = 0
                var touched = false
                var lastType = PointerType.Unknown
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull() ?: continue
                    if (change.type != PointerType.Stylus && change.type != PointerType.Eraser) continue
                    val buttons = event.stylusButtons()
                    if (change.pressed) touched = true
                    if (change.type != lastType) {
                        lastType = change.type
                        if (change.type == PointerType.Eraser) currentOnPenSignal(describePenButtons(buttons, change.type, change.pressed))
                    }
                    if (buttons != lastButtons) {
                        currentOnPenSignal(describePenButtons(buttons, change.type, change.pressed))
                        if (lastButtons == 0) touched = change.pressed
                        val released = lastButtons and buttons.inv()
                        if (released != 0 && !touched && !change.pressed) {
                            val action = if (released and BUTTON_SECONDARY != 0) currentSecondaryAction else currentPrimaryAction
                            if (action != PenButtonAction.NONE) {
                                coordinates?.let { currentOnPenButtonClick(action, it.localToRoot(change.position)) }
                            }
                        }
                        lastButtons = buttons
                    }
                }
            }
        }.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // Holding a pen side button can lend a different tool to this one stroke.
                // Buttons sent as keys (Xiaomi's pen) count as held too.
                val buttons = if (down.type == PointerType.Stylus) currentEvent.stylusButtons() or PenKeys.held else 0
                val heldAction = when {
                    buttons and BUTTON_SECONDARY != 0 -> currentSecondaryAction
                    buttons and BUTTON_PRIMARY != 0 -> currentPrimaryAction
                    else -> PenButtonAction.NONE
                }
                if (down.type == PointerType.Stylus) PenKeys.markUsed()
                val gestureTool = heldAction.tool ?: currentTool

                val erasing = gestureTool == Tool.ERASER || down.type == PointerType.Eraser
                val highlighter = gestureTool == Tool.HIGHLIGHTER
                fun toDp(p: Offset) = Offset(p.x / currentScale, p.y / currentScale)
                var secondFinger = false
                // Time and pressure of the point most recently passed to a trackDrag callback.
                var pointTime = down.uptimeMillis
                var pointPressure = down.pressure

                // Collects every position of the gesture after the first, in page units.
                // Stops early if a second finger lands; the gesture then becomes a scroll.
                // [onHold] runs once if the pointer stays still for SHAPE_HOLD_MS mid-drag.
                suspend fun AwaitPointerEventScope.trackDrag(onHold: (() -> Unit)? = null, onPoint: (Offset) -> Unit) {
                    var stillAt = down.position
                    var stillSince = down.uptimeMillis
                    var held = false
                    while (true) {
                        val waitMs = stillSince + SHAPE_HOLD_MS - currentEvent.changes.first().uptimeMillis
                        val event = if (onHold != null && !held) {
                            withTimeoutOrNull(waitMs.coerceAtLeast(1)) { awaitPointerEvent() }
                        } else {
                            awaitPointerEvent()
                        }
                        if (event == null) {
                            held = true
                            onHold?.invoke()
                            continue
                        }
                        event.changes.firstOrNull { it.id == down.id }?.let { change ->
                            // A pen resting on the screen still jitters a little; that counts as still.
                            if ((change.position - stillAt).getDistance() > HOLD_SLOP_PX) {
                                stillAt = change.position
                                stillSince = change.uptimeMillis
                            } else if (onHold != null && !held && change.pressed && change.uptimeMillis - stillSince >= SHAPE_HOLD_MS) {
                                held = true
                                onHold()
                            }
                        }
                        if (event.changes.count { it.pressed } > 1) {
                            secondFinger = true
                            return
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        pointPressure = change.pressure
                        change.historical.forEach {
                            pointTime = it.uptimeMillis
                            onPoint(toDp(it.position))
                        }
                        pointTime = change.uptimeMillis
                        onPoint(toDp(change.position))
                        change.consume()
                        if (!change.pressed) break
                    }
                }

                // Two fingers: moving scrolls the pages, pinching zooms them, holding still opens the pen dial.
                suspend fun AwaitPointerEventScope.scrollWithFingers() {
                    // If turned on: with any tool, the pen and a finger held together on the same picture
                    // select it (a locked one too, so it can be unlocked).
                    val pressed = currentEvent.changes.filter { it.pressed }
                    val penAt = pressed.firstOrNull { it.type == PointerType.Stylus }?.let { toDp(it.position) }
                    val fingerAt = pressed.firstOrNull { it.type == PointerType.Touch }?.let { toDp(it.position) }
                    val picked = if (currentPenAndFingerSelect && penAt != null && fingerAt != null) {
                        currentImages.lastOrNull { it.contains(penAt.x, penAt.y) && it.contains(fingerAt.x, fingerAt.y) }
                    } else {
                        null
                    }
                    if (picked != null) {
                        currentOnImageSelect(picked.id)
                        while (currentEvent.changes.any { it.pressed }) awaitPointerEvent().changes.forEach { it.consume() }
                        return
                    }
                    val holdStart = System.currentTimeMillis()
                    var moved = 0f
                    var decided = false
                    var lastSpan = 0f
                    // A quick touch-and-lift without moving is a tap (two-finger tap = undo, three = redo).
                    var fingers = currentEvent.changes.count { it.pressed }
                    while (true) {
                        val remaining = TWO_FINGER_HOLD_MS - (System.currentTimeMillis() - holdStart)
                        val event = if (decided) {
                            awaitPointerEvent()
                        } else {
                            withTimeoutOrNull(remaining.coerceAtLeast(1)) { awaitPointerEvent() }
                        }
                        if (event == null) {
                            decided = true
                            val fingers = currentEvent.changes.filter { it.pressed }
                            val local = coordinates
                            if (fingers.size >= 2 && local != null) {
                                val centroid = Offset(
                                    fingers.map { it.position.x }.average().toFloat(),
                                    fingers.map { it.position.y }.average().toFloat(),
                                )
                                currentOnHold(local.localToRoot(centroid))
                            }
                            continue
                        }
                        val pressed = event.changes.filter { it.pressed }
                        fingers = maxOf(fingers, pressed.size)
                        if (pressed.isEmpty()) {
                            val quick = System.currentTimeMillis() - holdStart < MULTI_FINGER_TAP_MS
                            if (!decided && quick && moved < TWO_FINGER_SLOP_PX) currentOnMultiFingerTap(fingers)
                            if (moved >= TWO_FINGER_SLOP_PX) currentOnScrollEnd()
                            return
                        }
                        val dy = pressed.map { it.positionChange().y }.average().toFloat()
                        val dx = pressed.map { it.positionChange().x }.average().toFloat()
                        val span = if (pressed.size >= 2) (pressed[0].position - pressed[1].position).getDistance() else 0f
                        if (!decided || moved >= TWO_FINGER_SLOP_PX) {
                            moved += abs(dx) + abs(dy)
                            if (lastSpan > 0f && span > 0f) moved += abs(span - lastSpan)
                            if (moved >= TWO_FINGER_SLOP_PX) decided = true
                            if (lastSpan > 0f && span > 0f) currentOnZoom(span / lastSpan)
                            currentOnScroll(-dx, -dy)
                        }
                        lastSpan = span
                        event.changes.forEach { it.consume() }
                    }
                }

                // Two fingers on a picture: scale by their spread, rotate by their twist, move with their middle.
                suspend fun AwaitPointerEventScope.pinchImage(base: PageImage, onPreview: (PageImage) -> Unit): PageImage {
                    var image = base
                    var startSpan = 0f
                    var startAngle = 0f
                    var startCentroid = Offset.Zero
                    var from = base
                    while (true) {
                        val pressed = currentEvent.changes.filter { it.pressed }
                        if (pressed.isEmpty()) return image
                        if (pressed.size >= 2) {
                            val a = toDp(pressed[0].position)
                            val b = toDp(pressed[1].position)
                            val span = (a - b).getDistance().coerceAtLeast(1f)
                            val angle = Math.toDegrees(atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())).toFloat()
                            val centroid = (a + b) / 2f
                            if (startSpan == 0f) {
                                startSpan = span
                                startAngle = angle
                                startCentroid = centroid
                                from = image
                            }
                            val f = (span / startSpan).coerceIn(0.05f, 20f)
                            image = from.copy(
                                x = from.x + centroid.x - startCentroid.x,
                                y = from.y + centroid.y - startCentroid.y,
                                width = (from.width * f).coerceAtLeast(8f),
                                height = (from.height * f).coerceAtLeast(8f),
                                rotation = ((from.rotation + angle - startAngle) % 360f + 360f) % 360f,
                            )
                            onPreview(image)
                        } else {
                            // Back to one finger: the next pinch starts from here.
                            startSpan = 0f
                        }
                        currentEvent.changes.forEach { it.consume() }
                        awaitPointerEvent()
                    }
                }

                if (currentAdjusting) {
                    // Move (one finger), pinch-zoom (two fingers) or resize from the corner dot.
                    down.consume()
                    val pageW = size.width / currentScale
                    val pageH = size.height / currentScale
                    var p = currentPlacement
                    val start = toDp(down.position)
                    val corner = (start - Offset(p.x + pageW * p.scale, p.y + pageH * p.scale)).getDistance() <= HANDLE_TOUCH_DP
                    var lastId = down.id
                    var last = start
                    var lastSpan = 0f
                    var lastCentroid = Offset.Zero
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            val a = toDp(pressed[0].position)
                            val b = toDp(pressed[1].position)
                            val span = (a - b).getDistance()
                            val centroid = (a + b) / 2f
                            if (lastSpan > 0f) {
                                val newScale = (p.scale * span / lastSpan).coerceIn(MIN_SCALE / 2, MAX_SCALE)
                                val f = newScale / p.scale
                                // Zoom around the fingers, then follow them as they move.
                                p = BackgroundPlacement(
                                    centroid.x - (lastCentroid.x - p.x) * f,
                                    centroid.y - (lastCentroid.y - p.y) * f,
                                    newScale,
                                )
                            }
                            lastSpan = span
                            lastCentroid = centroid
                        } else {
                            val finger = pressed[0]
                            val pos = toDp(finger.position)
                            // After a pinch, or if another finger is now the one down, start fresh from here.
                            if (lastSpan > 0f || finger.id != lastId) {
                                lastSpan = 0f
                                lastId = finger.id
                                last = pos
                            }
                            p = if (corner) {
                                p.copy(scale = maxOf((pos.x - p.x) / pageW, (pos.y - p.y) / pageH).coerceIn(MIN_SCALE / 2, MAX_SCALE))
                            } else {
                                p.copy(x = p.x + pos.x - last.x, y = p.y + pos.y - last.y)
                            }
                            last = pos
                        }
                        livePlacement = p
                        event.changes.forEach { it.consume() }
                    }
                    livePlacement = null
                    currentOnPlacementChange(p)
                    return@awaitEachGesture
                }

                val fingerOnSelectedPicture = gestureTool == Tool.LASSO &&
                    currentImages.any { it.id == currentSelectedImageId && !it.locked && it.contains(toDp(down.position).x, toDp(down.position).y) }
                // A finger held still inside a loop just drawn turns the loop into a lasso selection.
                val loopPoints = currentLoop?.points
                if (down.type == PointerType.Touch && loopPoints != null && toDp(down.position).let { Shapes.contains(loopPoints, it.x, it.y) }) {
                    var lifted = false
                    var moved = false
                    val timedOut = withTimeoutOrNull(LOOP_HOLD_MS) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val finger = event.changes.firstOrNull { it.id == down.id }
                            if (finger == null || !finger.pressed) { lifted = true; break }
                            if (event.changes.count { it.pressed } > 1 || (finger.position - down.position).getDistance() > TWO_FINGER_SLOP_PX) { moved = true; break }
                        }
                    } == null
                    if (timedOut) {
                        currentOnLoopHold()
                        while (currentEvent.changes.any { it.pressed }) awaitPointerEvent().changes.forEach { it.consume() }
                        return@awaitEachGesture
                    }
                    if (lifted) return@awaitEachGesture
                    // Moved: carry on as an ordinary touch.
                }

                // The text tool: a tap places or finishes a text box. Drags are left to the page list,
                // so a finger or pen still scrolls; two fingers scroll and zoom as usual.
                if (gestureTool == Tool.TEXT && down.type != PointerType.Eraser) {
                    var moved = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } >= 2) {
                            scrollWithFingers()
                            return@awaitEachGesture
                        }
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                        moved = maxOf(moved, (pointer.position - down.position).getDistance())
                        if (!pointer.pressed) {
                            if (moved < TWO_FINGER_SLOP_PX) {
                                val p = toDp(down.position)
                                currentOnTextTap(p.x, p.y)
                            }
                            return@awaitEachGesture
                        }
                    }
                }

                if ((currentStylusOnly || currentOverview) && down.type == PointerType.Touch && !fingerOnSelectedPicture) {
                    // Fingers never draw here. One finger is left to the page list (so it scrolls
                    // and flings normally); a second finger takes over to scroll or open the dial.
                    var moved = 0f
                    while (true) {
                        val held = down.uptimeMillis + PAGE_HOLD_MS - (currentEvent.changes.first().uptimeMillis)
                        val event = if (currentOverview && moved < TWO_FINGER_SLOP_PX) {
                            withTimeoutOrNull(held.coerceAtLeast(1)) { awaitPointerEvent() }
                        } else {
                            awaitPointerEvent()
                        }
                        if (event == null) {
                            // Held still: pick the page up and follow the finger until it lifts.
                            val local = coordinates ?: return@awaitEachGesture
                            currentOnPageDragStart(local.localToRoot(down.position))
                            while (true) {
                                val move = awaitPointerEvent()
                                val finger = move.changes.firstOrNull { it.id == down.id }
                                move.changes.forEach { it.consume() }
                                if (finger == null || !finger.pressed) break
                                coordinates?.let { currentOnPageDrag(it.localToRoot(finger.position)) }
                            }
                            currentOnPageDragEnd()
                            return@awaitEachGesture
                        }
                        val fingers = event.changes.count { it.pressed }
                        event.changes.firstOrNull { it.id == down.id }?.let { moved = maxOf(moved, (it.position - down.position).getDistance()) }
                        if (fingers == 0) {
                            val quick = event.changes.first().uptimeMillis - down.uptimeMillis < MULTI_FINGER_TAP_MS
                            if (quick && moved < TWO_FINGER_SLOP_PX) currentOnFingerTap()
                            return@awaitEachGesture
                        }
                        if (fingers >= 2) {
                            scrollWithFingers()
                            return@awaitEachGesture
                        }
                    }
                }

                // A pen side button: open the dial under the pen, or switch to the last tool.
                if (heldAction == PenButtonAction.OPEN_DIAL || heldAction == PenButtonAction.LAST_TOOL) {
                    down.consume()
                    coordinates?.let { currentOnPenButtonClick(heldAction, it.localToRoot(down.position)) }
                    while (currentEvent.changes.any { it.pressed }) awaitPointerEvent().changes.forEach { it.consume() }
                    return@awaitEachGesture
                }

                down.consume()
                val start = toDp(down.position)

                if (!erasing && gestureTool == Tool.LASSO) {
                    // Pictures first. The pen moves the selected picture and works its handles, but starts
                    // a lasso over any other picture (so ink drawn over it can still be selected);
                    // fingers can also pick up any picture, drag it and pinch it.
                    val pen = down.type == PointerType.Stylus
                    // A locked picture can be selected (to unlock it) but not moved or resized.
                    val selected = currentImages.firstOrNull { it.id == currentSelectedImageId && !it.locked }
                    fun onHandle(handle: Pair<Float, Float>) =
                        selected != null && selected.handleDistance(start, handle) <= HANDLE_TOUCH_DP
                    val imageGesture = when {
                        onHandle(ROTATE_HANDLE) -> ImageGesture.ROTATE
                        onHandle(SCALE_HANDLE) -> ImageGesture.SCALE
                        onHandle(SCALE_X_HANDLE) -> ImageGesture.SCALE_X
                        onHandle(SCALE_Y_HANDLE) -> ImageGesture.SCALE_Y
                        selected != null && selected.contains(start.x, start.y) -> ImageGesture.MOVE
                        pen -> null
                        else -> currentImages.lastOrNull { !it.locked && it.contains(start.x, start.y) }?.let {
                            currentOnImageSelect(it.id)
                            ImageGesture.MOVE
                        }
                    }
                    if (imageGesture != null) {
                        val target = selected?.takeIf { imageGesture != ImageGesture.MOVE || it.contains(start.x, start.y) }
                            ?: currentImages.last { !it.locked && it.contains(start.x, start.y) }
                        var image = target
                        var last = start
                        trackDrag { p ->
                            image = when (imageGesture) {
                                ImageGesture.MOVE -> image.copy(x = image.x + p.x - last.x, y = image.y + p.y - last.y)
                                ImageGesture.SCALE -> {
                                    // Keep the shape; the corner follows the finger's distance from the center.
                                    val corner = hypot(target.width / 2, target.height / 2)
                                    val f = (hypot(p.x - target.x, p.y - target.y) / corner).coerceIn(0.05f, 20f)
                                    image.copy(width = target.width * f, height = target.height * f)
                                }
                                // One side only: the opposite edge stays put and the picture stretches.
                                ImageGesture.SCALE_X -> target.stretchedTo(p.x, p.y, horizontal = true)
                                ImageGesture.SCALE_Y -> target.stretchedTo(p.x, p.y, horizontal = false)
                                ImageGesture.ROTATE -> {
                                    val degrees = Math.toDegrees(atan2((p.y - image.y).toDouble(), (p.x - image.x).toDouble())).toFloat() + 90f
                                    // Snap to straight angles when close, so images are easy to set level.
                                    val snapped = (Math.round(degrees / 90f) * 90f).takeIf { abs(it - degrees) < 4f } ?: degrees
                                    image.copy(rotation = (snapped + 360f) % 360f)
                                }
                            }
                            last = p
                            liveImage = image
                        }
                        if (secondFinger && imageGesture == ImageGesture.MOVE) {
                            // A second finger on a picture: pinch to scale and twist to rotate it.
                            image = pinchImage(image) { liveImage = it }
                        }
                        liveImage = null
                        if (secondFinger && imageGesture != ImageGesture.MOVE) {
                            scrollWithFingers()
                        } else {
                            currentOnImageChange(image)
                        }
                        return@awaitEachGesture
                    }
                    if (currentSelectedImageId != null) currentOnImageSelect(null)

                    val box = currentBounds
                    fun near(handle: Offset) = (start - handle).getDistance() <= HANDLE_TOUCH_DP
                    val gesture = when {
                        box != null && near(box.bottomRight) -> LassoGesture.RESIZE_XY
                        box != null && near(box.centerRight) -> LassoGesture.RESIZE_X
                        box != null && near(box.bottomCenter) -> LassoGesture.RESIZE_Y
                        box != null && box.contains(start) -> LassoGesture.MOVE
                        else -> LassoGesture.DRAW
                    }
                    when (gesture) {
                        LassoGesture.DRAW -> {
                            lasso.clear()
                            lasso.add(start)
                            trackDrag { p -> if ((p - lasso.last()).getDistance() > 1f) lasso.add(p) }
                            if (secondFinger) {
                                lasso.clear()
                                scrollWithFingers()
                                return@awaitEachGesture
                            }
                            // A tap (no lasso drawn) on a picture selects it, with the pen or a finger; a locked one
                            // too, but then it can only be unlocked, not moved.
                            val tapped = lasso.all { (it - start).getDistance() < TAP_SLOP_DP }
                            val picture = if (tapped) currentImages.lastOrNull { it.contains(start.x, start.y) } else null
                            if (picture != null) {
                                lasso.clear()
                                currentOnImageSelect(picture.id)
                                return@awaitEachGesture
                            }
                            val polygon = FloatArray(lasso.size * 2)
                            lasso.forEachIndexed { i, p ->
                                polygon[2 * i] = p.x
                                polygon[2 * i + 1] = p.y
                            }
                            lasso.clear()
                            currentOnLasso(polygon)
                        }
                        LassoGesture.MOVE -> {
                            trackDrag { p -> dragOffset = p - start }
                            if (!secondFinger && dragOffset != Offset.Zero) {
                                currentOnTransform(dragOffset.x, dragOffset.y, 1f, 1f, 0f, 0f)
                            }
                            dragOffset = Offset.Zero
                        }
                        LassoGesture.RESIZE_XY, LassoGesture.RESIZE_X, LassoGesture.RESIZE_Y -> {
                            val anchor = box!!.topLeft
                            val stretchX = gesture != LassoGesture.RESIZE_Y
                            val stretchY = gesture != LassoGesture.RESIZE_X
                            trackDrag { p ->
                                if (stretchX) {
                                    dragScaleX = ((p.x - anchor.x) / box.width).coerceIn(MIN_SCALE, MAX_SCALE)
                                }
                                if (stretchY) {
                                    dragScaleY = ((p.y - anchor.y) / box.height).coerceIn(MIN_SCALE, MAX_SCALE)
                                }
                            }
                            if (!secondFinger && (dragScaleX != 1f || dragScaleY != 1f)) {
                                currentOnTransform(0f, 0f, dragScaleX, dragScaleY, anchor.x, anchor.y)
                            }
                            dragScaleX = 1f
                            dragScaleY = 1f
                        }
                    }
                    if (secondFinger) scrollWithFingers()
                    return@awaitEachGesture
                }

                if (erasing) {
                    currentOnEraseStart()
                    currentOnErase(start.x, start.y)
                    eraserAt = start
                    trackDrag { p ->
                        eraserAt = p
                        currentOnErase(p.x, p.y)
                    }
                    eraserAt = null
                    currentOnEraseEnd()
                    if (secondFinger) scrollWithFingers()
                } else {
                    val inkPen = gestureTool == Tool.INK_PEN
                    val calligraphy = gestureTool == Tool.CALLIGRAPHY
                    fun addPoint(p: Offset) {
                        currentTimes.add(pointTime)
                        currentPressures.add(pointPressure)
                        current.add(p)
                    }
                    current.clear()
                    currentTimes.clear()
                    currentPressures.clear()
                    liveTool = gestureTool
                    addPoint(start)
                    val level = currentSmoothing
                    val stabilizer = Smoothing.Stabilizer(level)
                    stabilizer.next(start.x, start.y)
                    var lastRaw = start
                    // Draw a shape and hold the pen still: it snaps to a clean line, circle, triangle or rectangle.
                    var shape: FloatArray? = null
                    // The pen's path before smoothing, which would round off the corners of shapes.
                    val path = arrayListOf(start.x, start.y)
                    trackDrag(onHold = {
                        Shapes.recognize(path.toFloatArray())?.let { clean ->
                            shape = clean
                            current.clear()
                            for (i in clean.indices step 2) current.add(Offset(clean[i], clean[i + 1]))
                        }
                    }) { p ->
                        lastRaw = p
                        if (shape != null) return@trackDrag
                        path += p.x
                        path += p.y
                        val (x, y) = stabilizer.next(p.x, p.y)
                        val steady = Offset(x, y)
                        if ((steady - current.last()).getDistance() > 0.5f) addPoint(steady)
                    }
                    if (secondFinger) {
                        current.clear()
                        liveTool = null
                        scrollWithFingers()
                        return@awaitEachGesture
                    }
                    // The stabilizer trails the pen; finish the line where the pen lifted.
                    if (shape == null && (lastRaw - current.last()).getDistance() > 0.5f) addPoint(lastRaw)
                    val raw = FloatArray(current.size * 2)
                    current.forEachIndexed { i, p ->
                        raw[2 * i] = p.x
                        raw[2 * i + 1] = p.y
                    }
                    val points = shape ?: Smoothing.smoothStroke(raw, level)
                    val widths = when {
                        // A snapped shape is drawn evenly; its points no longer match the pen's pressure.
                        shape != null -> null
                        inkPen -> {
                            val factors = InkPen.widthFactors(raw, currentTimes.toLongArray(), currentPressures.toFloatArray())
                            InkPen.resampleWidths(raw, factors, points)
                        }
                        calligraphy && currentPointedNib -> InkPen.resampleWidths(
                            raw,
                            pointedWidths(raw, currentPressures.toFloatArray()),
                            points,
                        )
                        calligraphy -> InkPen.resampleWidths(
                            raw,
                            Calligraphy.pressureFactors(currentPressures.toFloatArray()),
                            points,
                        )
                        else -> null
                    }
                    val (strokeColor, strokeWidth) = if (gestureTool == currentTool) currentColor to currentWidth else currentStyleFor(gestureTool)
                    liveTool = null
                    currentOnStroke(
                        InkStroke(
                            color = strokeColor.toArgbInt(),
                            width = strokeWidth,
                            highlighter = highlighter,
                            points = points,
                            widths = widths,
                            nibAngle = if (calligraphy && !currentPointedNib) Calligraphy.nibRadians(currentNibDegrees) else null,
                            squareEnds = calligraphy && currentPointedNib,
                        ),
                    )
                    current.clear()
                }
            }
        },
    ) {
        // Paper, pictures and finished ink live on their own layer, so they are not redrawn
        // for every pen movement while a stroke is being drawn. That layer is itself kept as one
        // picture (see InkBitmapCache): a new stroke is painted onto it, so a full page costs no
        // more per frame than an empty one.
        Spacer(Modifier.matchParentSize().graphicsLayer().drawBehind {
        val shownPlacement = livePlacement ?: placement
        // Scans and PDF pages stay white, so ink on them keeps its real color.
        val dark = darkPaper && background == null
        val unselected = if (selection.isEmpty()) strokes else strokes.filter { it !in selection }

        fun DrawScope.drawBase() {
            if (background != null) {
                drawRect(if (invertBackground) Color.Black else Color.White)
                drawImage(
                    background,
                    dstOffset = IntOffset((shownPlacement.x * scale).roundToInt(), (shownPlacement.y * scale).roundToInt()),
                    dstSize = IntSize(
                        (size.width * shownPlacement.scale).roundToInt(),
                        (size.height * shownPlacement.scale).roundToInt(),
                    ),
                    filterQuality = FilterQuality.Medium,
                    colorFilter = if (invertBackground) SmartInvert else null,
                )
            } else {
                drawPaper(paper, scale, dark = darkPaper)
            }
            for (stored in images) {
                val image = liveImage?.takeIf { it.id == stored.id } ?: stored
                if (image.waitingForPhoto) {
                    withTransform({ rotate(image.rotation, pivot = Offset(image.x * scale, image.y * scale)) }) {
                        drawPhotoRequest(image, scale, accent)
                    }
                    continue
                }
                val bitmap = imageBitmaps[image.file] ?: continue
                withTransform({ rotate(image.rotation, pivot = Offset(image.x * scale, image.y * scale)) }) {
                    drawImage(
                        bitmap,
                        dstOffset = IntOffset(((image.x - image.width / 2) * scale).roundToInt(), ((image.y - image.height / 2) * scale).roundToInt()),
                        dstSize = IntSize((image.width * scale).roundToInt(), (image.height * scale).roundToInt()),
                        alpha = image.opacity,
                        filterQuality = FilterQuality.Medium,
                        colorFilter = if (image.inverted) SmartInvert else null,
                    )
                }
            }
        }
        fun DrawScope.drawStrokes(list: List<InkStroke>) {
            withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
                for (stroke in list) drawInkStroke(stroke, pathCache.getOrPut(stroke) { stroke.toPath() }, dark)
            }
        }

        // While a picture or the background is being moved, everything is drawn directly.
        val moving = adjustingBackground || liveImage != null
        if (!moving && size.width * size.height <= MAX_CACHED_PIXELS) {
            // A copy of the pictures: the page's list changes in place, so the list itself would always compare equal.
            val baseKey = listOf(paper, darkPaper, background, invertBackground, placement, images.toList(), imageBitmaps, scale)
            inkCache.draw(this, baseKey, unselected, { drawBase() }, { drawStrokes(it) })
        } else {
            drawBase()
            drawStrokes(unselected)
        }

        if (adjustingBackground && background != null) {
            val topLeft = Offset(shownPlacement.x * scale, shownPlacement.y * scale)
            val box = Size(size.width * shownPlacement.scale, size.height * shownPlacement.scale)
            drawRect(
                accent,
                topLeft = topLeft,
                size = box,
                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))),
            )
            val handle = topLeft + Offset(box.width, box.height)
            drawCircle(Color.White, radius = (HANDLE_RADIUS_DP + 2f) * scale, center = handle)
            drawCircle(accent, radius = HANDLE_RADIUS_DP * scale, center = handle)
        }
        images.firstOrNull { it.id == selectedImageId }?.let { stored ->
            drawImageFrame(liveImage?.takeIf { it.id == stored.id } ?: stored, scale, accent, handles = !stored.locked)
        }
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            if (bounds != null) {
                val pivot = bounds.topLeft
                withTransform({
                    translate(dragOffset.x, dragOffset.y)
                    scale(dragScaleX, dragScaleY, pivot = pivot)
                }) {
                    for (stroke in strokes) {
                        if (stroke !in selection) continue
                        val path = pathCache.getOrPut(stroke) { stroke.toPath() }
                        drawPath(
                            path,
                            accent.copy(alpha = 0.18f),
                            style = Stroke(stroke.width + 8f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                        drawInkStroke(stroke, path, dark)
                    }
                }
                val box = Rect(
                    pivot + dragOffset,
                    Size(bounds.width * dragScaleX, bounds.height * dragScaleY),
                )
                drawRect(
                    accent,
                    topLeft = box.topLeft,
                    size = box.size,
                    style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
                )
                for (handle in listOf(box.bottomRight, box.centerRight, box.bottomCenter)) {
                    drawCircle(Color.White, radius = HANDLE_RADIUS_DP + 2f, center = handle)
                    drawCircle(accent, radius = HANDLE_RADIUS_DP, center = handle)
                }
            }
        }
        })
        // Only what changes with every movement: the stroke in progress, the lasso loop, the eraser ring.
        Spacer(Modifier.matchParentSize().drawBehind {
        val dark = darkPaper && background == null
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            if (lasso.size > 1) {
                val path = Path().apply {
                    moveTo(lasso[0].x, lasso[0].y)
                    for (i in 1 until lasso.size) lineTo(lasso[i].x, lasso[i].y)
                    close()
                }
                drawPath(path, accent.copy(alpha = 0.08f))
                drawPath(
                    path,
                    accent,
                    style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
                )
            }

            eraserAt?.let { p ->
                drawCircle(Color.White.copy(alpha = 0.6f), radius = ERASER_RADIUS_DP, center = p)
                drawCircle(Color.Gray, radius = ERASER_RADIUS_DP, center = p, style = Stroke(1f))
            }

            if (current.isNotEmpty()) {
                val tool = liveTool ?: tool
                val (color, width) = if (tool == liveTool && liveTool != null) styleFor(tool) else color to width
                val livePoints = FloatArray(current.size * 2).also { arr ->
                    current.forEachIndexed { i, p ->
                        arr[2 * i] = p.x
                        arr[2 * i + 1] = p.y
                    }
                }
                val liveWidths = when {
                    currentTimes.size != current.size -> null
                    tool == Tool.INK_PEN ->
                        InkPen.widthFactors(livePoints, currentTimes.toLongArray(), currentPressures.toFloatArray())
                    tool == Tool.CALLIGRAPHY && pointedNib -> pointedWidths(livePoints, currentPressures.toFloatArray())
                    tool == Tool.CALLIGRAPHY -> Calligraphy.pressureFactors(currentPressures.toFloatArray())
                    else -> null
                }
                val live = InkStroke(
                    color = color.toArgbInt(),
                    width = width,
                    highlighter = tool == Tool.HIGHLIGHTER,
                    points = livePoints,
                    widths = liveWidths,
                    nibAngle = if (tool == Tool.CALLIGRAPHY && !pointedNib) Calligraphy.nibRadians(nibDegrees) else null,
                    squareEnds = tool == Tool.CALLIGRAPHY && pointedNib,
                )
                drawInkStroke(live, live.toPath(), dark)
            }
        }
        })
    }
}

private enum class ImageGesture { MOVE, SCALE, SCALE_X, SCALE_Y, ROTATE }

/** Where a picture's handles sit, in its own frame relative to its half-size: the corner and above the top edge. */
private val SCALE_HANDLE = 1f to 1f
private val SCALE_X_HANDLE = 1f to 0f
private val SCALE_Y_HANDLE = 0f to 1f
private val ROTATE_HANDLE = 0f to -1f
private const val ROTATE_HANDLE_GAP = 28f

private fun PageImage.handlePosition(handle: Pair<Float, Float>): Offset {
    val lx = handle.first * width / 2
    val ly = handle.second * height / 2 - if (handle == ROTATE_HANDLE) ROTATE_HANDLE_GAP else 0f
    val (px, py) = toPage(lx, ly)
    return Offset(px, py)
}

private fun PageImage.handleDistance(point: Offset, handle: Pair<Float, Float>) =
    (point - handlePosition(handle)).getDistance()

/** Dashed frame, scale dots (corner, right edge, bottom edge) and rotate handle around the selected picture. */
private fun DrawScope.drawImageFrame(image: PageImage, scale: Float, accent: Color, handles: Boolean = true) {
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
private fun selectionBounds(selection: Set<InkStroke>): Rect? {
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

private fun Color.toArgbInt(): Int =
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
fun InkStroke.erasedAround(x: Float, y: Float, radius: Float): List<InkStroke>? {
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
                InkStroke(color, width, highlighter, run.toFloatArray(), factors?.let { runWidths.toFloatArray() }, layerId, nibAngle = nibAngle, squareEnds = squareEnds),
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

private const val BUTTON_PRIMARY = PenKeys.STYLUS_PRIMARY
private const val BUTTON_SECONDARY = PenKeys.STYLUS_SECONDARY

/**
 * The pen side buttons held during [this] event. Compose reports the first stylus button as
 * isPrimaryPressed (shared with a mouse's left button), so the raw MotionEvent is read when it's there.
 */
private fun PointerEvent.stylusButtons(): Int {
    motionEvent?.let { return it.buttonState and (BUTTON_PRIMARY or BUTTON_SECONDARY) }
    return (if (buttons.isPrimaryPressed) BUTTON_PRIMARY else 0) or (if (buttons.isSecondaryPressed) BUTTON_SECONDARY else 0)
}

private fun describePenButtons(buttons: Int, type: PointerType, touching: Boolean): String {
    val names = listOfNotNull(
        "button 1".takeIf { buttons and BUTTON_PRIMARY != 0 },
        "button 2".takeIf { buttons and BUTTON_SECONDARY != 0 },
    )
    val end = if (type == PointerType.Eraser) "Eraser end" else "Pen"
    val where = if (touching) "touching" else "hovering"
    return "$end $where: " + (names.joinToString(" + ").ifEmpty { "buttons released" })
}

/** The pointed pen's width at each point: bold going down, a hairline going up, scaled by pen pressure. */
private fun pointedWidths(points: FloatArray, pressures: FloatArray): FloatArray {
    val direction = Calligraphy.pointedFactors(points)
    val pressure = Calligraphy.pressureFactors(pressures)
    return FloatArray(direction.size) { i -> direction[i] * (pressure.getOrNull(i) ?: 1f).coerceIn(0.6f, 1.2f) }
}

/**
 * A page's paper, pictures and finished ink, kept as one picture at screen resolution. When the
 * strokes only grew (a stroke was added) just the new ones are painted onto it; anything else
 * (erasing, undo, zoom, paper or pictures changing) paints it afresh.
 */
private class InkBitmapCache {
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
    ) {
        val width = scope.size.width.roundToInt().coerceAtLeast(1)
        val height = scope.size.height.roundToInt().coerceAtLeast(1)
        var picture = bitmap
        val sameSize = picture != null && picture.width == width && picture.height == height
        if (!sameSize) picture = ImageBitmap(width, height)
        val grown = sameSize && key == baseKey && drawn.size <= strokes.size && drawn.indices.all { drawn[it] === strokes[it] }
        val canvas = androidx.compose.ui.graphics.Canvas(picture!!)
        painter.draw(scope, scope.layoutDirection, canvas, scope.size) {
            if (grown) {
                if (strokes.size > drawn.size) drawStrokes(strokes.subList(drawn.size, strokes.size))
            } else {
                drawRect(Color.Transparent, blendMode = BlendMode.Clear)
                drawBase()
                drawStrokes(strokes)
            }
        }
        bitmap = picture
        baseKey = key
        drawn = strokes
        scope.drawImage(picture)
    }
}

/** An empty frame waiting for a photo from another device: dashed outline, a camera and a hint. */
private fun DrawScope.drawPhotoRequest(image: PageImage, scale: Float, accent: Color) {
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
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(unit * 0.4f),
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
private const val TAP_SLOP_DP = 8f
