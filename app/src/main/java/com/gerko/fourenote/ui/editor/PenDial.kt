package com.gerko.fourenote.ui.editor

import androidx.compose.material.icons.filled.Title
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.HighlightAlt
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gerko.fourenote.data.ink.Smoothing
import com.gerko.fourenote.data.ink.Tool
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val DIAL_SIZE = 540.dp

/** Nib angles offered on the angle ring, in degrees. */
private val NIB_ANGLES = listOf(0f, 15f, 30f, 45f, 60f, 75f, 90f)

/** One finger hole: where it sits, whether it is the current choice, and what tapping it does. */
private class Hole(val selected: Boolean, val onClick: () -> Unit, val content: @Composable () -> Unit)

/** A ring of holes at [radius] from the center, spread evenly starting at the top. */
private class Ring(val radius: Dp, val holeSize: Dp, val holes: List<Hole>, val enabled: Boolean = true)

/**
 * Quick pen settings styled like a rotary phone with five nested rings, from
 * the outside in: tool, color, smoothness (0-10), thickness and calligraphy nib angle.
 * Like a rotary dial's finger holes, each ring's options sit close together
 * in an arc on the right; the left side, where the fingers were, is left
 * empty so nothing there can be pressed by accident. The center shows
 * the current pen and cycles its thickness when tapped. Opens where two
 * fingers were held (or the pen button pressed); tapping outside closes it.
 */
@Composable
fun PenDial(
    viewModel: NoteEditorViewModel,
    centerRootPx: Offset,
    darkPaper: Boolean,
    onDismiss: () -> Unit,
    onAddColor: () -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        // The dial's center is where the fingers were; the fan of options opens to their right.
        // Only the right half needs room, so the center may sit right up to the screen's left edge.
        val size = minOf(DIAL_SIZE, maxWidth * 2, maxHeight)
        val dialPx = with(density) { size.toPx() }
        val screenWidth = with(density) { maxWidth.toPx() }
        val screenHeight = with(density) { maxHeight.toPx() }
        val centerX = centerRootPx.x.coerceIn(0f, (screenWidth - dialPx / 2).coerceAtLeast(0f))
        val centerY = centerRootPx.y.coerceIn(dialPx / 2, (screenHeight - dialPx / 2).coerceAtLeast(dialPx / 2))
        val scale = size / DIAL_SIZE

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .offset { IntOffset((centerX - dialPx / 2).roundToInt(), (centerY - dialPx / 2).roundToInt()) }
                .size(size),
        ) {
            val rings = rings(viewModel, scale, onAddColor)
            FanBackground(rings, scale)
            rings.forEach { ring -> ring.holes.forEachIndexed { i, hole -> DialHole(ring, i, hole, scale) } }
            CenterHub(viewModel, scale)
        }
    }
}

private fun rings(viewModel: NoteEditorViewModel, scale: Float, onAddColor: () -> Unit): List<Ring> {
    val tools = listOf(
        Tool.PEN to Icons.Filled.Create,
        Tool.INK_PEN to Icons.Filled.HistoryEdu,
        Tool.CALLIGRAPHY to Icons.Filled.FormatItalic,
        Tool.HIGHLIGHTER to Icons.Filled.BorderColor,
        Tool.ERASER to Icons.Outlined.CleaningServices,
        Tool.LASSO to Icons.Filled.HighlightAlt,
        Tool.TEXT to Icons.Filled.Title,
    )
    return listOf(
        Ring(
            radius = 236.dp * scale,
            holeSize = 46.dp * scale,
            holes = tools.map { (tool, icon) ->
                Hole(viewModel.tool == tool, { viewModel.selectTool(tool) }) { ToolGlyph(icon) }
            } + listOf(
                Hole(false, viewModel::undo) { ToolGlyph(Icons.AutoMirrored.Filled.Undo) },
                Hole(false, viewModel::redo) { ToolGlyph(Icons.AutoMirrored.Filled.Redo) },
            ),
        ),
        Ring(
            radius = 186.dp * scale,
            holeSize = 40.dp * scale,
            holes = viewModel.toolColors.map { color ->
                Hole(viewModel.toolColor == color, { viewModel.setToolColor(color) }) { ColorGlyph(color, 26.dp * scale) }
            } + Hole(false, onAddColor) { ToolGlyph(Icons.Filled.Add) },
        ),
        Ring(
            radius = 138.dp * scale,
            holeSize = 32.dp * scale,
            holes = (0..Smoothing.MAX_LEVEL).map { level ->
                Hole(viewModel.smoothing == level, { viewModel.smoothing = level }) {
                    Text("$level", fontSize = (13 * scale).sp, style = MaterialTheme.typography.labelMedium)
                }
            },
        ),
        Ring(
            radius = 92.dp * scale,
            holeSize = 26.dp * scale,
            holes = viewModel.dialWidths.let { widths ->
                val current = widths.indices.minBy { abs(widths[it] - viewModel.toolWidth) }
                widths.mapIndexed { i, width ->
                    Hole(i == current, { viewModel.setToolWidth(width) }) { SizeGlyph(i, scale) }
                }
            },
        ),
        Ring(
            radius = 52.dp * scale,
            holeSize = 22.dp * scale,
            holes = NIB_ANGLES.map { degrees ->
                Hole(viewModel.nibDegrees == degrees, { viewModel.nibDegrees = degrees }) { NibGlyph(degrees, 18.dp * scale) }
            },
            // The nib angle only shapes flat-nib calligraphy; elsewhere it's shown dimmed.
            enabled = viewModel.tool == Tool.CALLIGRAPHY && !viewModel.pointedNib,
        ),
    )
}

/** Gap between neighboring holes along a ring. */
private val HOLE_GAP = 6.dp

/**
 * The widest a ring's arc may get, in degrees. Below 180 so no option ever
 * reaches the left side, under the fingers; a ring with more options than
 * fit packs them closer and draws its holes smaller instead.
 */
private const val MAX_SWEEP = 170f

/**
 * Where a ring's holes go: packed side by side (hole size plus a small gap
 * apart) along an arc centered on the right, from top to bottom. Only rings
 * with too many holes for that spread wider, up to [MAX_SWEEP].
 * Angles are in degrees, clockwise from the right (0 is straight right).
 */
private fun Ring.angles(scale: Float): List<Float> {
    val n = holes.size
    if (n <= 1) return List(n) { 0f }
    val packed = Math.toDegrees(((holeSize + HOLE_GAP * scale) / radius).toDouble()).toFloat()
    val step = minOf(packed, MAX_SWEEP / (n - 1))
    val half = step * (n - 1) / 2
    return List(n) { i -> -half + i * step }
}

/** The span a ring's holes cover, padded by half a hole at each end. */
private fun Ring.sweep(scale: Float): Pair<Float, Float> {
    val angles = angles(scale)
    val pad = Math.toDegrees(((holeSize / 2 + 4.dp * scale) / radius).toDouble()).toFloat()
    val start = (angles.minOrNull() ?: 0f) - pad
    val end = (angles.maxOrNull() ?: 0f) + pad
    return start to end - start
}

/**
 * The dial's face: a fan on the right, just big enough for the outer ring,
 * with a band behind each ring's arc so the layers read as separate dials.
 */
@Composable
private fun FanBackground(rings: List<Ring>, scale: Float) {
    val face = MaterialTheme.colorScheme.surfaceContainer
    val band = MaterialTheme.colorScheme.surfaceContainerHighest
    val edge = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        Modifier
            .fillMaxSize()
            // Swallow taps on the face so they don't close the dial.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        val center = Offset(size.width / 2, size.height / 2)
        val outer = rings.first()
        val (faceStart, faceSweep) = outer.sweep(scale)
        val faceRadius = (outer.radius + outer.holeSize / 2 + 10.dp * scale).toPx()
        val topLeft = center - Offset(faceRadius, faceRadius)
        val faceSize = androidx.compose.ui.geometry.Size(faceRadius * 2, faceRadius * 2)
        drawArc(face, faceStart - 4f, faceSweep + 8f, useCenter = true, topLeft = topLeft, size = faceSize)
        drawArc(edge, faceStart - 4f, faceSweep + 8f, useCenter = true, topLeft = topLeft, size = faceSize, style = Stroke(1.dp.toPx()))
        for (ring in rings) {
            val (start, sweep) = ring.sweep(scale)
            val r = ring.radius.toPx()
            drawArc(
                band,
                start,
                sweep,
                useCenter = false,
                topLeft = center - Offset(r, r),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                style = Stroke((ring.holeSize + 8.dp * scale).toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

/** A ring's hole size, shrunk if its options had to be packed closer than a hole's width. */
private fun Ring.fittedHoleSize(scale: Float): Dp {
    val angles = angles(scale)
    if (angles.size < 2) return holeSize
    val stepRadians = Math.toRadians(abs(angles[0] - angles[1]).toDouble()).toFloat()
    return minOf(holeSize, radius * stepRadians - 2.dp * scale)
}

@Composable
private fun DialHole(ring: Ring, index: Int, hole: Hole, scale: Float) {
    val radiusPx = with(LocalDensity.current) { ring.radius.toPx() }
    val radians = Math.toRadians(ring.angles(scale)[index].toDouble())
    val accent = MaterialTheme.colorScheme.primary
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset { IntOffset((radiusPx * cos(radians)).roundToInt(), (radiusPx * sin(radians)).roundToInt()) }
            .size(ring.fittedHoleSize(scale))
            .alpha(if (ring.enabled) 1f else 0.35f)
            .clip(CircleShape)
            .background(if (hole.selected) accent.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(if (hole.selected) 3.dp else 1.dp, if (hole.selected) accent else MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(onClick = hole.onClick),
    ) {
        hole.content()
    }
}

@Composable
private fun ToolGlyph(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
}

@Composable
private fun ColorGlyph(color: Color, size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(color)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape),
    )
}

/** A dot growing with the [index] of its size, thinnest first. */
@Composable
private fun SizeGlyph(index: Int, scale: Float) {
    Box(Modifier.size((3 + index * 2.5f).dp * scale).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
}

/** A short line at the nib's angle, so each hole shows the direction it writes thin in. */
@Composable
private fun NibGlyph(degrees: Float, size: Dp) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2
        val a = Math.toRadians(-degrees.toDouble())
        val dx = (r * cos(a)).toFloat()
        val dy = (r * sin(a)).toFloat()
        drawLine(color, center - Offset(dx, dy), center + Offset(dx, dy), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    }
}

/** The current pen as a dot of its thickness and color; tapping cycles through the thicknesses. */
@Composable
private fun CenterHub(viewModel: NoteEditorViewModel, scale: Float) {
    val widths = viewModel.toolWidths
    val index = widths.indexOf(viewModel.toolWidth).coerceAtLeast(0)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(34.dp * scale)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
            .clickable { viewModel.setToolWidth(widths[(index + 1) % widths.size]) },
    ) {
        val dot = ((6 + index * 6) * scale).dp
        Box(Modifier.size(dot).clip(CircleShape).background(viewModel.toolColor))
    }
}
