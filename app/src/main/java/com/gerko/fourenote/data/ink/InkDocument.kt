package com.gerko.fourenote.data.ink

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class Tool { PEN, INK_PEN, CALLIGRAPHY, HIGHLIGHTER, ERASER, LASSO, TEXT }

enum class PaperStyle { BLANK, LINED, GRID, DOTS }

/**
 * A single stroke. [points] is packed as x0, y0, x1, y1, ... in dp relative to
 * the page origin, so notes render the same on screens of any density.
 * Deliberately not a data class: strokes are compared by identity so the
 * canvas can cache one Path per stroke.
 *
 * Strokes are immutable; any edit (move, recolor, partial erase) produces new
 * strokes with new [id]s. That lets sync merge two devices' edits as plain set
 * operations on ids.
 */
class InkStroke(
    val color: Int,
    val width: Float,
    val highlighter: Boolean,
    val points: FloatArray,
    /** Ink-pen strokes only: a width factor per point (see [InkPen]); null for even-width strokes. */
    val widths: FloatArray? = null,
    /** The [InkLayer] this stroke belongs to. */
    val layerId: String = InkLayer.BASE_ID,
    val id: String = newStrokeId(),
    /**
     * Calligraphy strokes only: the flat nib's angle in radians. The stroke is
     * drawn by sweeping a flat nib of length width x [widths] along the path
     * (see [Calligraphy.nibShapes]); null for round-tipped pens.
     */
    val nibAngle: Float? = null,
    /** Variable-width strokes only: cut straight off at both ends (the pointed pen) instead of rounded. */
    val squareEnds: Boolean = false,
)

fun newStrokeId(): String = UUID.randomUUID().toString()

/**
 * Returns a copy scaled by ([scaleX], [scaleY]) around ([pivotX], [pivotY]) and
 * then moved by ([dx], [dy]). Line width follows the average of the two scales,
 * so stretched writing keeps an even pen thickness.
 */
fun InkStroke.transformed(
    dx: Float,
    dy: Float,
    scaleX: Float,
    scaleY: Float,
    pivotX: Float,
    pivotY: Float,
) = InkStroke(
    color = color,
    width = width * sqrt(scaleX * scaleY),
    highlighter = highlighter,
    points = FloatArray(points.size) { i ->
        if (i % 2 == 0) pivotX + (points[i] - pivotX) * scaleX + dx
        else pivotY + (points[i] - pivotY) * scaleY + dy
    },
    widths = widths,
    layerId = layerId,
    nibAngle = nibAngle,
    squareEnds = squareEnds,
)

fun InkStroke.withColor(newColor: Int) =
    InkStroke(newColor, width, highlighter, points, widths, layerId, nibAngle = nibAngle, squareEnds = squareEnds)

/** True if most of this stroke's points fall inside the lasso [polygon] (packed x, y pairs). */
fun InkStroke.isInsideLasso(polygon: FloatArray): Boolean {
    if (polygon.size < 6) return false
    val n = points.size / 2
    var inside = 0
    for (i in 0 until n) {
        if (pointInPolygon(points[2 * i], points[2 * i + 1], polygon)) inside++
    }
    return inside * 2 >= n
}

/** Even-odd ray casting test. */
private fun pointInPolygon(x: Float, y: Float, polygon: FloatArray): Boolean {
    var inside = false
    val n = polygon.size / 2
    var j = n - 1
    for (i in 0 until n) {
        val xi = polygon[2 * i]
        val yi = polygon[2 * i + 1]
        val xj = polygon[2 * j]
        val yj = polygon[2 * j + 1]
        if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
        j = i
    }
    return inside
}

/** A page's backdrop: a scanned photo or one page of an imported PDF, stored in the note's attachments. */
sealed interface PageBackground {
    val file: String

    data class Image(override val file: String) : PageBackground

    data class PdfPage(override val file: String, val pageIndex: Int) : PageBackground
}

/**
 * A picture placed on a page: centered at ([x], [y]) in page units, [width] x
 * [height] before rotating by [rotation] degrees clockwise, drawn with
 * [opacity]. It sits above the paper and below the ink. [file] is in the
 * note's attachments.
 */
data class PageImage(
    val id: String = UUID.randomUUID().toString(),
    val file: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    /** Show with smart-inverted colors, e.g. a white-background diagram on dark paper. */
    val inverted: Boolean = false,
    /** Show as a negative instead (every colour flipped: blue becomes yellow, black white). */
    val negative: Boolean = false,
    /** The part of the picture shown, as fractions of it (0 to 1): its left, top, right and bottom edges. */
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    /**
     * A frame waiting for a photo taken on another device (it has no [file] yet). Any device can
     * fill it; the photo then syncs into this very frame, since it keeps the same [id].
     */
    val requested: Boolean = false,
    /** A photo frame's name, e.g. "Photo 2", so the right photo goes in the right frame. */
    val label: String = "",
    /** Locked in place: fingers and the pen can't move or resize it until it is unlocked. */
    val locked: Boolean = false,
) {
    /** True if only part of the picture is shown. */
    val cropped: Boolean get() = cropLeft > 0f || cropTop > 0f || cropRight < 1f || cropBottom < 1f

    /** True if this is an empty frame waiting for a photo. */
    val waitingForPhoto: Boolean get() = requested && file.isEmpty()

    /** True if the page point ([px], [py]) lies on the (rotated) image. */
    fun contains(px: Float, py: Float, margin: Float = 0f): Boolean {
        val (lx, ly) = toLocal(px, py)
        return abs(lx) <= width / 2 + margin && abs(ly) <= height / 2 + margin
    }

    /** Page point ([px], [py]) in the image's own frame: origin at its center, unrotated. */
    fun toLocal(px: Float, py: Float): Pair<Float, Float> {
        val a = Math.toRadians(-rotation.toDouble())
        val dx = px - x
        val dy = py - y
        return (dx * cos(a) - dy * sin(a)).toFloat() to (dx * sin(a) + dy * cos(a)).toFloat()
    }

    /**
     * Stretches the picture along its own width (or height) so its right (or
     * bottom) edge reaches the page point ([px], [py]), keeping the opposite
     * edge where it is. Works the same when the picture is rotated.
     */
    fun stretchedTo(px: Float, py: Float, horizontal: Boolean): PageImage {
        val (lx, ly) = toLocal(px, py)
        return if (horizontal) {
            val newWidth = (lx + width / 2).coerceAtLeast(MIN_SIZE)
            val (cx, cy) = toPage(-width / 2 + newWidth / 2, 0f)
            copy(x = cx, y = cy, width = newWidth)
        } else {
            val newHeight = (ly + height / 2).coerceAtLeast(MIN_SIZE)
            val (cx, cy) = toPage(0f, -height / 2 + newHeight / 2)
            copy(x = cx, y = cy, height = newHeight)
        }
    }

    /** A point given in the image's own frame, back on the page. */
    fun toPage(lx: Float, ly: Float): Pair<Float, Float> {
        val a = Math.toRadians(rotation.toDouble())
        return (x + lx * cos(a) - ly * sin(a)).toFloat() to (y + lx * sin(a) + ly * cos(a)).toFloat()
    }

    private companion object {
        const val MIN_SIZE = 8f
    }
}

enum class TextFont { SANS, SERIF, MONO }

/** How a text box's text looks. [size] is in page units (16 is about 12 pt on A4). */
data class TextStyleSpec(
    val size: Float = 16f,
    val color: Int = 0xFF000000.toInt(),
    val bold: Boolean = false,
    val italic: Boolean = false,
    val font: TextFont = TextFont.SANS,
)

/**
 * Typed text on a page, at ([x], [y]) (its top-left corner) and [width] wide in page units; it
 * grows downwards as needed. With [autoMath], `$…$` and `$$…$$` in [text] show as typeset maths.
 */
data class PageText(
    val id: String = UUID.randomUUID().toString(),
    val x: Float,
    val y: Float,
    val width: Float,
    val text: String = "",
    val style: TextStyleSpec = TextStyleSpec(),
    val autoMath: Boolean = true,
)

/**
 * Where a page's background sits: its top-left corner at ([x], [y]) in page
 * units and its size as a multiple ([scale]) of the page. The default fills
 * the page exactly.
 */
data class BackgroundPlacement(val x: Float = 0f, val y: Float = 0f, val scale: Float = 1f)

/**
 * One page of a note. Stroke coordinates are in page units, where an A4 page
 * is [PAGE_WIDTH] wide; the editor scales pages to fit the screen.
 */
data class InkPage(
    val id: String = UUID.randomUUID().toString(),
    val width: Float = PAGE_WIDTH,
    val height: Float = PAGE_HEIGHT,
    val background: PageBackground? = null,
    val strokes: List<InkStroke> = emptyList(),
    val placement: BackgroundPlacement = BackgroundPlacement(),
    val images: List<PageImage> = emptyList(),
    /** Show the background (PDF page or scan) with smart-inverted colors, for reading in the dark. */
    val invertBackground: Boolean = false,
    val texts: List<PageText> = emptyList(),
) {
    companion object {
        /** A4 at 96 units per inch. */
        const val PAGE_WIDTH = 794f
        const val PAGE_HEIGHT = 1123f

        /** A page shaped like a [widthPx] x [heightPx] image, at the standard page width. */
        fun sizedFor(widthPx: Int, heightPx: Int, background: PageBackground) = InkPage(
            width = PAGE_WIDTH,
            height = PAGE_WIDTH * heightPx / widthPx.coerceAtLeast(1),
            background = background,
        )
    }
}

/**
 * A layer of ink shared by all pages of a note, like layers in a drawing app.
 * Layers are listed bottom to top; hidden layers are neither shown nor exported.
 */
data class InkLayer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val visible: Boolean = true,
) {
    companion object {
        /** The layer every note starts with, and that strokes from before layers belong to. */
        const val BASE_ID = "base"
        val BASE = InkLayer(BASE_ID, "Layer 1")
    }
}

data class InkDocument(
    val pages: List<InkPage> = listOf(InkPage()),
    val paper: PaperStyle = PaperStyle.LINED,
    val layers: List<InkLayer> = listOf(InkLayer.BASE),
) {
    /** Nothing was put in it: one page with no ink, text, pictures or background. */
    val isBlank: Boolean
        get() = pages.size == 1 && pages[0].let {
            it.strokes.isEmpty() && it.texts.isEmpty() && it.images.isEmpty() && it.background == null
        }

    /** The page's strokes on visible layers, in drawing order (bottom layer first). */
    fun visibleStrokes(page: InkPage): List<InkStroke> {
        val order = layers.withIndex().filter { it.value.visible }.associate { it.value.id to it.index }
        return page.strokes.filter { it.layerId in order }.sortedBy { order.getValue(it.layerId) }
    }
}

/** Serializes [InkDocument] to the JSON stored in Note.inkData. */
object InkSerializer {
    private const val VERSION = 3

    fun encode(doc: InkDocument): String {
        val pages = JSONArray()
        for (page in doc.pages) {
            val json = JSONObject()
                .put("id", page.id)
                .put("w", page.width.toDouble())
                .put("h", page.height.toDouble())
                .put("strokes", encodeStrokes(page.strokes))
            val bgJson = when (val bg = page.background) {
                is PageBackground.Image -> JSONObject().put("type", "image").put("file", bg.file)
                is PageBackground.PdfPage -> JSONObject().put("type", "pdf").put("file", bg.file).put("page", bg.pageIndex)
                null -> null
            }
            if (page.images.isNotEmpty()) {
                val images = JSONArray()
                for (img in page.images) {
                    images.put(
                        JSONObject().put("id", img.id).put("file", img.file)
                            .put("x", img.x.toDouble()).put("y", img.y.toDouble())
                            .put("w", img.width.toDouble()).put("h", img.height.toDouble())
                            .put("r", img.rotation.toDouble()).put("o", img.opacity.toDouble())
                            .apply { if (img.inverted) put("invert", true) }
                            .apply { if (img.negative) put("neg", true) }
                            .apply {
                                if (img.cropped) {
                                    put("crop", JSONArray().put(img.cropLeft.toDouble()).put(img.cropTop.toDouble())
                                        .put(img.cropRight.toDouble()).put(img.cropBottom.toDouble()))
                                }
                            }
                            .apply { if (img.requested) put("req", true) }
                            .apply { if (img.label.isNotEmpty()) put("lbl", img.label) }
                            .apply { if (img.locked) put("lock", true) },
                    )
                }
                json.put("images", images)
            }
            if (page.texts.isNotEmpty()) {
                val texts = JSONArray()
                for (t in page.texts) {
                    val st = t.style
                    texts.put(
                        JSONObject().put("id", t.id).put("x", t.x.toDouble()).put("y", t.y.toDouble())
                            .put("w", t.width.toDouble()).put("t", t.text).put("m", t.autoMath)
                            .put("s", st.size.toDouble()).put("c", st.color).put("f", st.font.name)
                            .apply { if (st.bold) put("b", true) }
                            .apply { if (st.italic) put("i", true) },
                    )
                }
                json.put("texts", texts)
            }
            if (bgJson != null) {
                if (page.invertBackground) bgJson.put("invert", true)
                val p = page.placement
                if (p != BackgroundPlacement()) {
                    bgJson.put("x", p.x.toDouble()).put("y", p.y.toDouble()).put("scale", p.scale.toDouble())
                }
                json.put("bg", bgJson)
            }
            pages.put(json)
        }
        val layers = JSONArray()
        for (layer in doc.layers) {
            layers.put(JSONObject().put("id", layer.id).put("name", layer.name).put("visible", layer.visible))
        }
        return JSONObject()
            .put("v", VERSION)
            .put("paper", doc.paper.name)
            .put("layers", layers)
            .put("pages", pages)
            .toString()
    }

    fun decode(data: String): InkDocument {
        if (data.isBlank()) return InkDocument()
        return try {
            val json = JSONObject(data)
            val paper = runCatching { PaperStyle.valueOf(json.optString("paper")) }
                .getOrDefault(PaperStyle.LINED)
            val pagesJson = json.optJSONArray("pages")
            val pages = if (pagesJson != null) {
                List(pagesJson.length()) { i -> decodePage(pagesJson.getJSONObject(i)) }
            } else {
                // Versions 1-2: one unbounded canvas. It becomes a single page big enough for its ink.
                val strokes = decodeStrokes(json.optJSONArray("strokes") ?: JSONArray())
                listOf(InkPage(id = "page-1", strokes = strokes).grownToFit())
            }
            val layersJson = json.optJSONArray("layers")
            val layers = if (layersJson == null) {
                listOf(InkLayer.BASE)
            } else {
                List(layersJson.length()) { i ->
                    val l = layersJson.getJSONObject(i)
                    InkLayer(l.getString("id"), l.optString("name"), l.optBoolean("visible", true))
                }
            }
            InkDocument(pages.ifEmpty { listOf(InkPage()) }, paper, layers.ifEmpty { listOf(InkLayer.BASE) })
        } catch (e: Exception) {
            InkDocument()
        }
    }

    private fun decodePage(json: JSONObject): InkPage {
        val bg = json.optJSONObject("bg")?.let {
            when (it.optString("type")) {
                "image" -> PageBackground.Image(it.getString("file"))
                "pdf" -> PageBackground.PdfPage(it.getString("file"), it.optInt("page"))
                else -> null
            }
        }
        val bgJson = json.optJSONObject("bg")
        val placement = if (bgJson == null) {
            BackgroundPlacement()
        } else {
            BackgroundPlacement(
                bgJson.optDouble("x", 0.0).toFloat(),
                bgJson.optDouble("y", 0.0).toFloat(),
                bgJson.optDouble("scale", 1.0).toFloat(),
            )
        }
        val imagesJson = json.optJSONArray("images") ?: JSONArray()
        val images = List(imagesJson.length()) { i ->
            val o = imagesJson.getJSONObject(i)
            PageImage(
                id = o.getString("id"),
                file = o.getString("file"),
                x = o.getDouble("x").toFloat(),
                y = o.getDouble("y").toFloat(),
                width = o.getDouble("w").toFloat(),
                height = o.getDouble("h").toFloat(),
                rotation = o.optDouble("r", 0.0).toFloat(),
                opacity = o.optDouble("o", 1.0).toFloat(),
                inverted = o.optBoolean("invert"),
                negative = o.optBoolean("neg"),
                cropLeft = o.optJSONArray("crop")?.optDouble(0, 0.0)?.toFloat() ?: 0f,
                cropTop = o.optJSONArray("crop")?.optDouble(1, 0.0)?.toFloat() ?: 0f,
                cropRight = o.optJSONArray("crop")?.optDouble(2, 1.0)?.toFloat() ?: 1f,
                cropBottom = o.optJSONArray("crop")?.optDouble(3, 1.0)?.toFloat() ?: 1f,
                requested = o.optBoolean("req"),
                label = o.optString("lbl"),
                locked = o.optBoolean("lock"),
            )
        }
        val textsJson = json.optJSONArray("texts") ?: JSONArray()
        val texts = List(textsJson.length()) { i ->
            val o = textsJson.getJSONObject(i)
            PageText(
                id = o.getString("id"),
                x = o.getDouble("x").toFloat(),
                y = o.getDouble("y").toFloat(),
                width = o.getDouble("w").toFloat(),
                text = o.optString("t"),
                autoMath = o.optBoolean("m", true),
                style = TextStyleSpec(
                    size = o.optDouble("s", 16.0).toFloat(),
                    color = o.optInt("c", 0xFF000000.toInt()),
                    bold = o.optBoolean("b"),
                    italic = o.optBoolean("i"),
                    font = runCatching { TextFont.valueOf(o.optString("f")) }.getOrDefault(TextFont.SANS),
                ),
            )
        }
        return InkPage(
            texts = texts,
            invertBackground = bgJson?.optBoolean("invert") ?: false,
            images = images,
            placement = placement,
            // The id names the page's file; an unsafe one (hostile or damaged note) gets a new id.
            id = json.optString("id").takeIf(com.gerko.fourenote.data.SafeName::isSafeFromCloud) ?: UUID.randomUUID().toString(),
            width = json.optDouble("w", InkPage.PAGE_WIDTH.toDouble()).toFloat(),
            height = json.optDouble("h", InkPage.PAGE_HEIGHT.toDouble()).toFloat(),
            background = bg,
            strokes = decodeStrokes(json.optJSONArray("strokes") ?: JSONArray()),
        )
    }

    private fun encodeStrokes(strokes: List<InkStroke>): JSONArray {
        val array = JSONArray()
        for (stroke in strokes) {
            val points = JSONArray()
            // One decimal place of a page unit is far below what the eye can see.
            for (v in stroke.points) points.put((v * 10).roundToInt() / 10.0)
            array.put(
                JSONObject()
                    .put("id", stroke.id)
                    .put("c", stroke.color)
                    .put("w", stroke.width.toDouble())
                    .put("h", stroke.highlighter)
                    .put("p", points)
                    .put("l", stroke.layerId)
                    .apply { stroke.nibAngle?.let { put("nib", it.toDouble()) } }
                    .apply { if (stroke.squareEnds) put("sq", true) }
                    .apply {
                        stroke.widths?.let { w -> put("pw", JSONArray().apply { w.forEach { put((it * 100).roundToInt() / 100.0) } }) }
                    },
            )
        }
        return array
    }

    private fun decodeStrokes(array: JSONArray): List<InkStroke> = List(array.length()) { i ->
        val s = array.getJSONObject(i)
        val p = s.getJSONArray("p")
        InkStroke(
            color = s.getInt("c"),
            width = s.getDouble("w").toFloat(),
            highlighter = s.optBoolean("h"),
            points = FloatArray(p.length()) { j -> p.getDouble(j).toFloat() },
            widths = s.optJSONArray("pw")?.let { w -> FloatArray(w.length()) { j -> w.getDouble(j).toFloat() } },
            layerId = s.optString("l").ifEmpty { InkLayer.BASE_ID },
            nibAngle = if (s.has("nib")) s.getDouble("nib").toFloat() else null,
            squareEnds = s.optBoolean("sq"),
            // Version 1 notes predate stroke ids.
            id = s.optString("id").ifEmpty { newStrokeId() },
        )
    }

    /** Enlarges a page so ink drawn before pages existed is not cut off. */
    private fun InkPage.grownToFit(): InkPage {
        var right = 0f
        var bottom = 0f
        for (stroke in strokes) {
            for (i in stroke.points.indices step 2) {
                right = maxOf(right, stroke.points[i] + stroke.width)
                bottom = maxOf(bottom, stroke.points[i + 1] + stroke.width)
            }
        }
        return copy(width = maxOf(width, right + 24f), height = maxOf(height, bottom + 24f))
    }
}
