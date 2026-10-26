package com.gerko.fourenote.data.ink

import com.gerko.fourenote.data.JsonReader
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
 * Where a link points: a note, optionally one of its pages, optionally some objects on that page
 * (stroke, text and image ids), and optionally a sticky note (not built yet).
 */
data class LinkTarget(
    val noteId: String,
    val pageId: String? = null,
    val objectIds: List<String> = emptyList(),
    /** Reserved for sticky notes, which do not exist yet. */
    val stickyId: String? = null,
)

/**
 * A link from objects on this page ([strokeIds], [textIds], [imageIds]) to [target]. The anchors
 * are the objects the link is drawn from; a link with no anchors left is dropped (see
 * [withoutDeadLinks]).
 */
data class PageLink(
    val id: String = newId(),
    val strokeIds: List<String> = emptyList(),
    val textIds: List<String> = emptyList(),
    val imageIds: List<String> = emptyList(),
    val target: LinkTarget,
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
    val links: List<PageLink> = emptyList(),
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
 * Drops links that no longer hang on anything on the page: those with no anchors at all, and
 * those whose anchors were all erased. Moving or recoloring strokes gives them new ids, so the
 * editor must carry the link over to the new ids or it is dropped here.
 */
fun InkPage.withoutDeadLinks(): InkPage {
    if (links.isEmpty()) return this
    val strokeIds = strokes.mapTo(HashSet()) { it.id }
    val textIds = texts.mapTo(HashSet()) { it.id }
    val imageIds = images.mapTo(HashSet()) { it.id }
    val alive = links.filter { link ->
        link.strokeIds.any { it in strokeIds } ||
            link.textIds.any { it in textIds } ||
            link.imageIds.any { it in imageIds }
    }
    return if (alive.size == links.size) this else copy(links = alive)
}

/**
 * Carries links over to strokes that were given new ids (moved, recolored, partly erased): each
 * anchor id that is a key in [replacements] becomes the ids in its value, in place, and an empty
 * value drops it. Links that use none of the replaced ids are returned as they are.
 */
fun List<PageLink>.withStrokesReplaced(replacements: Map<String, List<String>>): List<PageLink> {
    if (replacements.isEmpty()) return this
    return map { link ->
        if (link.strokeIds.none { it in replacements }) {
            link
        } else {
            link.copy(strokeIds = link.strokeIds.flatMap { replacements[it] ?: listOf(it) })
        }
    }
}

/**
 * The box around a link's anchors that still exist on the page, as left, top, right and bottom in
 * page units, or null if none of them do. Pictures are taken unrotated: the box only has to be
 * near enough to show where the link starts.
 */
fun linkAnchorBounds(
    link: PageLink,
    strokes: List<InkStroke>,
    texts: List<PageText>,
    images: List<PageImage>,
): FloatArray? {
    var left = Float.MAX_VALUE
    var top = Float.MAX_VALUE
    var right = -Float.MAX_VALUE
    var bottom = -Float.MAX_VALUE
    var found = false
    fun include(l: Float, t: Float, r: Float, b: Float) {
        left = minOf(left, l)
        top = minOf(top, t)
        right = maxOf(right, r)
        bottom = maxOf(bottom, b)
        found = true
    }
    val strokeIds = link.strokeIds.toHashSet()
    for (stroke in strokes) {
        if (stroke.id !in strokeIds) continue
        for (i in stroke.points.indices step 2) {
            val x = stroke.points[i]
            val y = stroke.points[i + 1]
            include(x, y, x, y)
        }
    }
    val textIds = link.textIds.toHashSet()
    for (text in texts) {
        if (text.id in textIds) include(text.x, text.y, text.x + text.width, text.y + text.style.size * 1.4f)
    }
    val imageIds = link.imageIds.toHashSet()
    for (image in images) {
        if (image.id in imageIds) {
            include(image.x - image.width / 2, image.y - image.height / 2, image.x + image.width / 2, image.y + image.height / 2)
        }
    }
    return if (found) floatArrayOf(left, top, right, bottom) else null
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

    fun encode(doc: InkDocument): String = StringBuilder().also { encodeTo(doc, it) }.toString()

    /**
     * Writes the note as text straight into [out], without building it up as a tree first
     * (an object per number would take several hundred MB for a long note).
     */
    fun encodeTo(doc: InkDocument, out: Appendable) {
        val w = Writer(out)
        w.obj {
            w.key("v").raw(VERSION.toString())
            w.key("paper").str(doc.paper.name)
            w.key("layers").arr(doc.layers) { layer ->
                w.obj {
                    w.key("id").str(layer.id)
                    w.key("name").str(layer.name)
                    w.key("visible").bool(layer.visible)
                }
            }
            w.key("pages").arr(doc.pages) { page -> writePage(w, page) }
        }
    }

    private fun writePage(w: Writer, page: InkPage) = w.obj {
        w.key("id").str(page.id)
        w.key("w").num(page.width)
        w.key("h").num(page.height)
        w.key("strokes").arr(page.strokes) { stroke ->
            w.obj {
                w.key("id").str(stroke.id)
                w.key("c").raw(stroke.color.toString())
                w.key("w").num(stroke.width)
                w.key("h").bool(stroke.highlighter)
                // One decimal place of a page unit is far below what the eye can see.
                w.key("p").nums(stroke.points) { (it * 10).roundToInt() / 10f }
                w.key("l").str(stroke.layerId)
                stroke.nibAngle?.let { w.key("nib").num(it) }
                if (stroke.squareEnds) w.key("sq").bool(true)
                stroke.widths?.let { widths -> w.key("pw").nums(widths) { (it * 100).roundToInt() / 100f } }
            }
        }
        if (page.images.isNotEmpty()) {
            w.key("images").arr(page.images) { img ->
                w.obj {
                    w.key("id").str(img.id)
                    w.key("file").str(img.file)
                    w.key("x").num(img.x)
                    w.key("y").num(img.y)
                    w.key("w").num(img.width)
                    w.key("h").num(img.height)
                    w.key("r").num(img.rotation)
                    w.key("o").num(img.opacity)
                    if (img.inverted) w.key("invert").bool(true)
                    if (img.negative) w.key("neg").bool(true)
                    if (img.cropped) {
                        w.key("crop").nums(floatArrayOf(img.cropLeft, img.cropTop, img.cropRight, img.cropBottom)) { it }
                    }
                    if (img.requested) w.key("req").bool(true)
                    if (img.label.isNotEmpty()) w.key("lbl").str(img.label)
                    if (img.locked) w.key("lock").bool(true)
                }
            }
        }
        if (page.links.isNotEmpty()) {
            w.key("links").arr(page.links) { link ->
                w.obj {
                    w.key("id").str(link.id)
                    if (link.strokeIds.isNotEmpty()) w.key("s").strs(link.strokeIds)
                    if (link.textIds.isNotEmpty()) w.key("t").strs(link.textIds)
                    if (link.imageIds.isNotEmpty()) w.key("i").strs(link.imageIds)
                    w.key("to").obj {
                        w.key("note").str(link.target.noteId)
                        link.target.pageId?.let { w.key("page").str(it) }
                        if (link.target.objectIds.isNotEmpty()) w.key("obj").strs(link.target.objectIds)
                        link.target.stickyId?.let { w.key("sticky").str(it) }
                    }
                }
            }
        }
        if (page.texts.isNotEmpty()) {
            w.key("texts").arr(page.texts) { t ->
                val st = t.style
                w.obj {
                    w.key("id").str(t.id)
                    w.key("x").num(t.x)
                    w.key("y").num(t.y)
                    w.key("w").num(t.width)
                    w.key("t").str(t.text)
                    w.key("m").bool(t.autoMath)
                    w.key("s").num(st.size)
                    w.key("c").raw(st.color.toString())
                    w.key("f").str(st.font.name)
                    if (st.bold) w.key("b").bool(true)
                    if (st.italic) w.key("i").bool(true)
                }
            }
        }
        val bg = page.background
        if (bg != null) {
            w.key("bg").obj {
                when (bg) {
                    is PageBackground.Image -> {
                        w.key("type").str("image")
                        w.key("file").str(bg.file)
                    }
                    is PageBackground.PdfPage -> {
                        w.key("type").str("pdf")
                        w.key("file").str(bg.file)
                        w.key("page").raw(bg.pageIndex.toString())
                    }
                }
                if (page.invertBackground) w.key("invert").bool(true)
                val p = page.placement
                if (p != BackgroundPlacement()) {
                    w.key("x").num(p.x)
                    w.key("y").num(p.y)
                    w.key("scale").num(p.scale)
                }
            }
        }
    }

    /** Writes JSON by hand, keeping track of the commas. */
    private class Writer(private val out: Appendable) {
        private var first = true

        fun key(name: String): Writer {
            if (!first) out.append(',')
            first = false
            JsonReader.quote(name, out)
            out.append(':')
            return this
        }

        fun obj(body: () -> Unit) {
            out.append('{')
            first = true
            body()
            out.append('}')
            first = false
        }

        fun <T> arr(items: List<T>, item: (T) -> Unit) {
            out.append('[')
            items.forEachIndexed { i, it ->
                if (i > 0) out.append(',')
                first = true
                item(it)
            }
            out.append(']')
            first = false
        }

        fun nums(values: FloatArray, round: (Float) -> Float) {
            out.append('[')
            for (i in values.indices) {
                if (i > 0) out.append(',')
                out.append(numberText(round(values[i])))
            }
            out.append(']')
            first = false
        }

        fun str(value: String) {
            JsonReader.quote(value, out)
            first = false
        }

        fun strs(values: List<String>) {
            out.append('[')
            values.forEachIndexed { i, value ->
                if (i > 0) out.append(',')
                JsonReader.quote(value, out)
            }
            out.append(']')
            first = false
        }

        fun num(value: Float) = raw(numberText(value))

        fun bool(value: Boolean) = raw(value.toString())

        fun raw(text: String) {
            out.append(text)
            first = false
        }
    }

    fun decode(data: String): InkDocument {
        if (data.isBlank()) return InkDocument()
        return try {
            val reader = JsonReader(data)
            var paper = PaperStyle.LINED
            var pages: List<InkPage>? = null
            var legacyStrokes: List<InkStroke> = emptyList()
            var layers: List<InkLayer>? = null
            reader.obj { key ->
                when (key) {
                    "paper" -> paper = (reader.element() as? JsonPrimitive)?.contentOrNull
                        ?.let { runCatching { PaperStyle.valueOf(it) }.getOrNull() } ?: PaperStyle.LINED
                    "pages" -> if (reader.peek() == '[') {
                        val list = ArrayList<InkPage>()
                        reader.arr { list += readPage(reader) }
                        pages = list
                    } else {
                        reader.skip()
                    }
                    // Versions 1-2: one unbounded canvas.
                    "strokes" -> legacyStrokes = readStrokes(reader)
                    "layers" -> layers = (reader.element() as? JsonArray)?.map { element ->
                        val l = element.jsonObject
                        InkLayer(l.string("id"), l.string("name"), l.boolean("visible", true))
                    }
                    else -> reader.skip()
                }
            }
            // An old note becomes a single page big enough for its ink.
            val allPages = pages ?: listOf(InkPage(id = "page-1", strokes = legacyStrokes).grownToFit())
            InkDocument(allPages.ifEmpty { listOf(InkPage()) }, paper, layers.orEmpty().ifEmpty { listOf(InkLayer.BASE) })
        } catch (e: Exception) {
            InkDocument()
        }
    }

    // A page: its strokes read straight into arrays, the rest (small) as a JsonObject.
    private fun readPage(reader: JsonReader): InkPage {
        if (reader.peek() != '{') {
            reader.skip()
            return InkPage()
        }
        val rest = LinkedHashMap<String, JsonElement>()
        var strokes: List<InkStroke> = emptyList()
        reader.obj { key ->
            if (key == "strokes") strokes = readStrokes(reader) else rest[key] = reader.element()
        }
        return decodePage(JsonObject(rest)).copy(strokes = strokes)
    }

    private fun readStrokes(reader: JsonReader): List<InkStroke> {
        if (reader.peek() != '[') {
            reader.skip()
            return emptyList()
        }
        val strokes = ArrayList<InkStroke>()
        reader.arr {
            if (reader.peek() != '{') {
                reader.skip()
                return@arr
            }
            var id = ""
            var color = 0
            var width = 0f
            var highlighter = false
            var points: FloatArray? = null
            var widths: FloatArray? = null
            var layer = ""
            var nib: Float? = null
            var square = false
            reader.obj { key ->
                when (key) {
                    "p" -> points = reader.floats()
                    "pw" -> widths = reader.floats()
                    else -> {
                        val v = reader.element() as? JsonPrimitive
                        when (key) {
                            "id" -> id = v?.contentOrNull.orEmpty()
                            "c" -> color = v?.intOrNull ?: 0
                            "w" -> width = v?.doubleOrNull?.toFloat() ?: 0f
                            "h" -> highlighter = v?.booleanOrNull ?: false
                            "l" -> layer = v?.contentOrNull.orEmpty()
                            "nib" -> nib = v?.doubleOrNull?.toFloat() ?: 0f
                            "sq" -> square = v?.booleanOrNull ?: false
                        }
                    }
                }
            }
            strokes += InkStroke(
                color = color,
                width = width,
                highlighter = highlighter,
                points = points ?: FloatArray(0),
                widths = widths,
                layerId = layer.ifEmpty { InkLayer.BASE_ID },
                nibAngle = nib,
                squareEnds = square,
                // Version 1 notes predate stroke ids.
                id = id.ifEmpty { newStrokeId() },
            )
        }
        return strokes
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
        val linksJson = json.array("links") ?: JsonArray(emptyList())
        val links = linksJson.mapNotNull { element ->
            // A damaged link is skipped; it must not cost the whole note.
            val o = element as? JsonObject ?: return@mapNotNull null
            val to = o.objectOrNull("to") ?: return@mapNotNull null
            val id = o.string("id")
            val noteId = to.string("note")
            // Ids name files, so a hostile or damaged one is dropped rather than trusted.
            if (!SafeName.isSafeFromCloud(id) || !SafeName.isSafeFromCloud(noteId)) return@mapNotNull null
            PageLink(
                id = id,
                strokeIds = o.strings("s"),
                textIds = o.strings("t"),
                imageIds = o.strings("i"),
                target = LinkTarget(
                    noteId = noteId,
                    pageId = to.string("page").takeIf { it.isNotEmpty() },
                    objectIds = to.strings("obj"),
                    stickyId = to.string("sticky").takeIf { it.isNotEmpty() },
                ),
            )
        }
        return InkPage(
            texts = texts,
            links = links,
            invertBackground = bgJson?.boolean("invert") ?: false,
            images = images,
            placement = placement,
            // The id names the page's file; an unsafe one (hostile or damaged note) gets a new id.
            id = json.string("id").takeIf(SafeName::isSafeFromCloud) ?: newId(),
            width = json.double("w", InkPage.PAGE_WIDTH.toDouble()).toFloat(),
            height = json.double("h", InkPage.PAGE_HEIGHT.toDouble()).toFloat(),
            background = bg,
        )
    }

    /**
     * A number as short as it can be written and still read back as exactly the same float:
     * whole numbers as integers (3, not 3.0), others like 412.3. Going through Double directly
     * would write 412.29998779296875, almost three times the text for every pen point.
     */
    private fun numberText(value: Float): String {
        if (value.isNaN() || value.isInfinite()) return "0"
        val whole = value.toDouble()
        if (whole == whole.toLong().toDouble()) return whole.toLong().toString()
        // Float's own text is the shortest that reads back as this float; a Double prints the same
        // way (412.3), and Kotlin on iOS writes 1.0E-5 style exponents the same as the JVM.
        return value.toString().toDouble().toString()
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

    private fun JsonObject.strings(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

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
