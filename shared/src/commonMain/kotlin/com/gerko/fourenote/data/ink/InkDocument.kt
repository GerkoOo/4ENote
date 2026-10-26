package com.gerko.fourenote.data.ink

import com.gerko.fourenote.data.SafeName
import com.gerko.fourenote.data.newId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
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

fun newStrokeId(): String = newId()

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
    val id: String = newId(),
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
        val a = -rotation.toDouble() * PI / 180.0
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
        val a = rotation.toDouble() * PI / 180.0
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
    val id: String = newId(),
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
    val id: String = newId(),
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
    val id: String = newId(),
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
        val pages = buildJsonArray {
            for (page in doc.pages) {
                add(
                    buildJsonObject {
                        put("id", page.id)
                        put("w", number(page.width))
                        put("h", number(page.height))
                        put("strokes", encodeStrokes(page.strokes))
                        if (page.images.isNotEmpty()) {
                            put("images", buildJsonArray {
                                for (img in page.images) {
                                    add(
                                        buildJsonObject {
                                            put("id", img.id)
                                            put("file", img.file)
                                            put("x", number(img.x))
                                            put("y", number(img.y))
                                            put("w", number(img.width))
                                            put("h", number(img.height))
                                            put("r", number(img.rotation))
                                            put("o", number(img.opacity))
                                            if (img.inverted) put("invert", true)
                                            if (img.negative) put("neg", true)
                                            if (img.cropped) {
                                                put("crop", buildJsonArray {
                                                    add(number(img.cropLeft))
                                                    add(number(img.cropTop))
                                                    add(number(img.cropRight))
                                                    add(number(img.cropBottom))
                                                })
                                            }
                                            if (img.requested) put("req", true)
                                            if (img.label.isNotEmpty()) put("lbl", img.label)
                                            if (img.locked) put("lock", true)
                                        },
                                    )
                                }
                            })
                        }
                        if (page.texts.isNotEmpty()) {
                            put("texts", buildJsonArray {
                                for (t in page.texts) {
                                    val st = t.style
                                    add(
                                        buildJsonObject {
                                            put("id", t.id)
                                            put("x", number(t.x))
                                            put("y", number(t.y))
                                            put("w", number(t.width))
                                            put("t", t.text)
                                            put("m", t.autoMath)
                                            put("s", number(st.size))
                                            put("c", st.color)
                                            put("f", st.font.name)
                                            if (st.bold) put("b", true)
                                            if (st.italic) put("i", true)
                                        },
                                    )
                                }
                            })
                        }
                        val bg = page.background
                        if (bg != null) {
                            put("bg", buildJsonObject {
                                when (bg) {
                                    is PageBackground.Image -> {
                                        put("type", "image")
                                        put("file", bg.file)
                                    }
                                    is PageBackground.PdfPage -> {
                                        put("type", "pdf")
                                        put("file", bg.file)
                                        put("page", bg.pageIndex)
                                    }
                                }
                                if (page.invertBackground) put("invert", true)
                                val p = page.placement
                                if (p != BackgroundPlacement()) {
                                    put("x", number(p.x))
                                    put("y", number(p.y))
                                    put("scale", number(p.scale))
                                }
                            })
                        }
                    },
                )
            }
        }
        val layers = buildJsonArray {
            for (layer in doc.layers) {
                add(
                    buildJsonObject {
                        put("id", layer.id)
                        put("name", layer.name)
                        put("visible", layer.visible)
                    },
                )
            }
        }
        return buildJsonObject {
            put("v", VERSION)
            put("paper", doc.paper.name)
            put("layers", layers)
            put("pages", pages)
        }.toString()
    }

    fun decode(data: String): InkDocument {
        if (data.isBlank()) return InkDocument()
        return try {
            val json = Json.parseToJsonElement(data).jsonObject
            val paper = runCatching { PaperStyle.valueOf(json.string("paper")) }
                .getOrDefault(PaperStyle.LINED)
            val pagesJson = json.array("pages")
            val pages = if (pagesJson != null) {
                pagesJson.map { decodePage(it.jsonObject) }
            } else {
                // Versions 1-2: one unbounded canvas. It becomes a single page big enough for its ink.
                val strokes = decodeStrokes(json.array("strokes") ?: JsonArray(emptyList()))
                listOf(InkPage(id = "page-1", strokes = strokes).grownToFit())
            }
            val layersJson = json.array("layers")
            val layers = if (layersJson == null) {
                listOf(InkLayer.BASE)
            } else {
                layersJson.map { element ->
                    val l = element.jsonObject
                    InkLayer(l.string("id"), l.string("name"), l.boolean("visible", true))
                }
            }
            InkDocument(pages.ifEmpty { listOf(InkPage()) }, paper, layers.ifEmpty { listOf(InkLayer.BASE) })
        } catch (e: Exception) {
            InkDocument()
        }
    }

    private fun decodePage(json: JsonObject): InkPage {
        val bgJson = json.objectOrNull("bg")
        val bg = bgJson?.let {
            when (it.string("type")) {
                "image" -> PageBackground.Image(it.string("file"))
                "pdf" -> PageBackground.PdfPage(it.string("file"), it.int("page"))
                else -> null
            }
        }
        val placement = if (bgJson == null) {
            BackgroundPlacement()
        } else {
            BackgroundPlacement(
                bgJson.double("x", 0.0).toFloat(),
                bgJson.double("y", 0.0).toFloat(),
                bgJson.double("scale", 1.0).toFloat(),
            )
        }
        val imagesJson = json.array("images") ?: JsonArray(emptyList())
        val images = imagesJson.map { element ->
            val o = element.jsonObject
            val crop = o.array("crop")
            PageImage(
                id = o.string("id"),
                file = o.string("file"),
                x = o.double("x").toFloat(),
                y = o.double("y").toFloat(),
                width = o.double("w").toFloat(),
                height = o.double("h").toFloat(),
                rotation = o.double("r", 0.0).toFloat(),
                opacity = o.double("o", 1.0).toFloat(),
                inverted = o.boolean("invert"),
                negative = o.boolean("neg"),
                cropLeft = (crop?.getOrNull(0) as? JsonPrimitive)?.doubleOrNull?.toFloat() ?: 0f,
                cropTop = (crop?.getOrNull(1) as? JsonPrimitive)?.doubleOrNull?.toFloat() ?: 0f,
                cropRight = (crop?.getOrNull(2) as? JsonPrimitive)?.doubleOrNull?.toFloat() ?: 1f,
                cropBottom = (crop?.getOrNull(3) as? JsonPrimitive)?.doubleOrNull?.toFloat() ?: 1f,
                requested = o.boolean("req"),
                label = o.string("lbl"),
                locked = o.boolean("lock"),
            )
        }
        val textsJson = json.array("texts") ?: JsonArray(emptyList())
        val texts = textsJson.map { element ->
            val o = element.jsonObject
            PageText(
                id = o.string("id"),
                x = o.double("x").toFloat(),
                y = o.double("y").toFloat(),
                width = o.double("w").toFloat(),
                text = o.string("t"),
                autoMath = o.boolean("m", true),
                style = TextStyleSpec(
                    size = o.double("s", 16.0).toFloat(),
                    color = o.int("c", 0xFF000000.toInt()),
                    bold = o.boolean("b"),
                    italic = o.boolean("i"),
                    font = runCatching { TextFont.valueOf(o.string("f")) }.getOrDefault(TextFont.SANS),
                ),
            )
        }
        return InkPage(
            texts = texts,
            invertBackground = bgJson?.boolean("invert") ?: false,
            images = images,
            placement = placement,
            // The id names the page's file; an unsafe one (hostile or damaged note) gets a new id.
            id = json.string("id").takeIf(SafeName::isSafeFromCloud) ?: newId(),
            width = json.double("w", InkPage.PAGE_WIDTH.toDouble()).toFloat(),
            height = json.double("h", InkPage.PAGE_HEIGHT.toDouble()).toFloat(),
            background = bg,
            strokes = decodeStrokes(json.array("strokes") ?: JsonArray(emptyList())),
        )
    }

    private fun encodeStrokes(strokes: List<InkStroke>): JsonArray = buildJsonArray {
        for (stroke in strokes) {
            add(
                buildJsonObject {
                    put("id", stroke.id)
                    put("c", stroke.color)
                    put("w", number(stroke.width))
                    put("h", stroke.highlighter)
                    put("p", buildJsonArray {
                        // One decimal place of a page unit is far below what the eye can see.
                        for (v in stroke.points) add(number((v * 10).roundToInt() / 10f))
                    })
                    put("l", stroke.layerId)
                    stroke.nibAngle?.let { put("nib", number(it)) }
                    if (stroke.squareEnds) put("sq", true)
                    stroke.widths?.let { w ->
                        put("pw", buildJsonArray {
                            w.forEach { add(number((it * 100).roundToInt() / 100f)) }
                        })
                    }
                },
            )
        }
    }

    private fun decodeStrokes(array: JsonArray): List<InkStroke> = array.map { element ->
        val s = element.jsonObject
        val p = s.array("p") ?: JsonArray(emptyList())
        InkStroke(
            color = s.int("c"),
            width = s.double("w").toFloat(),
            highlighter = s.boolean("h"),
            points = FloatArray(p.size) { j -> (p[j] as? JsonPrimitive)?.doubleOrNull?.toFloat() ?: 0f },
            widths = s.array("pw")?.let { w -> FloatArray(w.size) { j -> (w[j] as? JsonPrimitive)?.doubleOrNull?.toFloat() ?: 0f } },
            layerId = s.string("l").ifEmpty { InkLayer.BASE_ID },
            nibAngle = if (s.containsKey("nib")) s.double("nib").toFloat() else null,
            squareEnds = s.boolean("sq"),
            // Version 1 notes predate stroke ids.
            id = s.string("id").ifEmpty { newStrokeId() },
        )
    }

    /**
     * A number as org.json wrote it: whole numbers as integers (3, not 3.0), the rest as they are.
     * Old app versions and the fixture compare the text, so this must not change.
     */
    private fun number(value: Float): JsonPrimitive {
        val rounded = value.toDouble()
        return if (rounded == rounded.toLong().toDouble()) JsonPrimitive(rounded.toLong()) else JsonPrimitive(rounded)
    }

    private fun JsonObject.string(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull ?: ""

    private fun JsonObject.int(key: String, default: Int = 0): Int =
        (this[key] as? JsonPrimitive)?.intOrNull ?: default

    private fun JsonObject.double(key: String, default: Double = 0.0): Double =
        (this[key] as? JsonPrimitive)?.doubleOrNull ?: default

    private fun JsonObject.boolean(key: String, default: Boolean = false): Boolean =
        (this[key] as? JsonPrimitive)?.booleanOrNull ?: default

    private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

    private fun JsonObject.objectOrNull(key: String): JsonObject? = this[key] as? JsonObject

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
