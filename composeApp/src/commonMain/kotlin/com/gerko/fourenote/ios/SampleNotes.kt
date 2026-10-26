package com.gerko.fourenote.ios

import com.gerko.fourenote.data.ink.InkDocument
import com.gerko.fourenote.data.ink.InkPage
import com.gerko.fourenote.data.ink.InkSerializer
import com.gerko.fourenote.data.ink.InkStroke
import com.gerko.fourenote.data.ink.PaperStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A demo note shown in the iPad app's sidebar. */
class SampleNote(val title: String, val document: InkDocument)

/**
 * Three notes drawn in code, so the iPad app has something to show before the editor exists.
 * Each one goes through the shared serializer on the way out: if [InkSerializer] ever breaks on
 * iOS, the app fails at startup instead of showing notes it misread.
 */
object SampleNotes {
    val all: List<SampleNote> by lazy {
        listOf(
            SampleNote("Welcome", roundTrip(welcome())),
            SampleNote("Shapes", roundTrip(shapes())),
            SampleNote("Spiral", roundTrip(spiral())),
        )
    }

    private fun roundTrip(doc: InkDocument): InkDocument = InkSerializer.decode(InkSerializer.encode(doc))

    private fun welcome(): InkDocument {
        val strokes = ArrayList<InkStroke>()
        // "4E" as pen strokes: the 4 is a diagonal running into a crossbar, then a stem; the E is a
        // spine with three arms.
        strokes += polyline(BLUE, 6f, 260f, 300f, 200f, 390f, 290f, 390f)
        strokes += polyline(BLUE, 6f, 260f, 420f, 260f, 300f)
        strokes += polyline(BLUE, 6f, 340f, 300f, 340f, 420f, 420f, 420f)
        strokes += polyline(BLUE, 6f, 340f, 360f, 410f, 360f)
        strokes += polyline(BLUE, 6f, 340f, 300f, 420f, 300f)
        // A yellow highlighter underline under the letters.
        strokes += InkStroke(
            color = HIGHLIGHTER_YELLOW,
            width = 28f,
            highlighter = true,
            points = floatArrayOf(180f, 460f, 440f, 460f),
        )
        // A blue sine wave with a width that swells and shrinks along it.
        val wavePoints = ArrayList<Float>()
        val waveWidths = ArrayList<Float>()
        val steps = 120
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val x = 120f + t * 560f
            val y = 620f + sin(t * 4f * PI.toFloat()) * 60f
            wavePoints += x
            wavePoints += y
            waveWidths += 2f + 6f * (0.5f + 0.5f * sin(t * 8f * PI.toFloat()))
        }
        strokes += InkStroke(
            color = BLUE,
            width = 6f,
            highlighter = false,
            points = wavePoints.toFloatArray(),
            widths = waveWidths.toFloatArray(),
        )
        return InkDocument(pages = listOf(InkPage(strokes = strokes)), paper = PaperStyle.LINED)
    }

    private fun shapes(): InkDocument {
        val strokes = ArrayList<InkStroke>()
        // A red circle, drawn as a closed polyline.
        val circle = ArrayList<Float>()
        val circleSteps = 96
        for (i in 0..circleSteps) {
            val a = i.toFloat() / circleSteps * 2f * PI.toFloat()
            circle += 260f + cos(a) * 120f
            circle += 320f + sin(a) * 120f
        }
        strokes += InkStroke(RED, 6f, false, circle.toFloatArray())
        // A blue triangle.
        strokes += polyline(BLUE, 6f, 520f, 200f, 640f, 440f, 400f, 440f, 520f, 200f)
        // A dark rectangle.
        strokes += polyline(DARK, 6f, 200f, 620f, 600f, 620f, 600f, 820f, 200f, 820f, 200f, 620f)
        return InkDocument(pages = listOf(InkPage(strokes = strokes)), paper = PaperStyle.GRID)
    }

    private fun spiral(): InkDocument {
        val points = ArrayList<Float>(800)
        val turns = 6f
        val steps = 400
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val a = t * turns * 2f * PI.toFloat()
            val r = 20f + t * 260f
            points += 397f + cos(a) * r
            points += 561f + sin(a) * r
        }
        val stroke = InkStroke(DARK, 4f, false, points.toFloatArray())
        return InkDocument(pages = listOf(InkPage(strokes = listOf(stroke))), paper = PaperStyle.DOTS)
    }

    private fun polyline(color: Int, width: Float, vararg coords: Float) =
        InkStroke(color, width, false, coords)

    private const val BLUE = 0xFF1E5AA8.toInt()
    private const val RED = 0xFFC62828.toInt()
    private const val DARK = 0xFF202020.toInt()
    private const val HIGHLIGHTER_YELLOW = 0x80FFEB3B.toInt()
}
