package com.gerko.fourenote.ios

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.gerko.fourenote.data.ink.InkPage
import com.gerko.fourenote.data.ink.InkStroke
import com.gerko.fourenote.data.ink.PaperStyle

/**
 * Draws one page of a note: its paper, then its strokes. Everything is scaled by the canvas
 * width over the page width, so a page looks the same at any size.
 */
@Composable
fun PageView(page: InkPage, paper: PaperStyle, modifier: Modifier = Modifier) {
    Canvas(modifier.clipToBounds()) {
        val scale = size.width / page.width
        drawPaper(paper, scale)
        for (stroke in page.strokes) drawStroke(stroke, scale)
    }
}

private fun DrawScope.drawPaper(paper: PaperStyle, scale: Float) {
    drawRect(PAPER_COLOR)
    when (paper) {
        PaperStyle.BLANK -> Unit
        PaperStyle.LINED -> {
            var y = 64f * scale
            while (y < size.height) {
                drawLine(RULE_COLOR, Offset(0f, y), Offset(size.width, y), strokeWidth = scale)
                y += 32f * scale
            }
            val x = 72f * scale
            drawLine(MARGIN_COLOR, Offset(x, 0f), Offset(x, size.height), strokeWidth = scale)
        }
        PaperStyle.GRID -> {
            var y = 24f * scale
            while (y < size.height) {
                drawLine(RULE_COLOR, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.6f * scale)
                y += 24f * scale
            }
            var x = 24f * scale
            while (x < size.width) {
                drawLine(RULE_COLOR, Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.6f * scale)
                x += 24f * scale
            }
        }
        PaperStyle.DOTS -> {
            var y = 24f * scale
            while (y < size.height) {
                var x = 24f * scale
                while (x < size.width) {
                    drawCircle(RULE_COLOR, radius = 1.1f * scale, center = Offset(x, y))
                    x += 24f * scale
                }
                y += 24f * scale
            }
        }
    }
}

private fun DrawScope.drawStroke(stroke: InkStroke, scale: Float) {
    val color = Color(stroke.color)
    val blend = if (stroke.highlighter) BlendMode.Multiply else BlendMode.SrcOver
    val points = stroke.points
    val widths = stroke.widths
    if (points.size < 2) return
    if (points.size == 2) {
        // A single point: draw a dot the size of the pen.
        val radius = (widths?.firstOrNull() ?: stroke.width) * scale / 2f
        drawCircle(color, radius = radius, center = Offset(points[0], points[1]), blendMode = blend)
        return
    }
    if (widths == null) {
        val path = Path()
        path.moveTo(points[0], points[1])
        var i = 2
        while (i < points.size) {
            path.lineTo(points[i], points[i + 1])
            i += 2
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = stroke.width * scale, cap = StrokeCap.Round, join = StrokeJoin.Round),
            blendMode = blend,
        )
    } else {
        // Variable width: each segment gets the average of its two point widths.
        var i = 2
        var segment = 1
        while (i < points.size) {
            val w0 = widths.getOrElse(segment - 1) { stroke.width }
            val w1 = widths.getOrElse(segment) { stroke.width }
            drawLine(
                color = color,
                start = Offset(points[i - 2], points[i - 1]),
                end = Offset(points[i], points[i + 1]),
                strokeWidth = (w0 + w1) / 2f * scale,
                cap = StrokeCap.Round,
                blendMode = blend,
            )
            i += 2
            segment++
        }
    }
}

private val PAPER_COLOR = Color(0xFFFFFDF7)
private val RULE_COLOR = Color(0xFFB9D3EA)
private val MARGIN_COLOR = Color(0xFFF0A8A8)
