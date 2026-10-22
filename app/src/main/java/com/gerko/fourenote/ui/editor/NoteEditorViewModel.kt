package com.gerko.fourenote.ui.editor

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gerko.fourenote.data.Note
import com.gerko.fourenote.data.NoteRepository
import com.gerko.fourenote.data.attachments.AttachmentStore
import com.gerko.fourenote.data.recognition.HandwritingLanguage
import com.gerko.fourenote.data.recognition.HandwritingLanguages
import com.gerko.fourenote.data.recognition.HandwritingRecognizer
import com.gerko.fourenote.data.recognition.MathRecognizer
import com.gerko.fourenote.data.ink.BackgroundPlacement
import com.gerko.fourenote.data.ink.Calligraphy
import com.gerko.fourenote.data.ink.InkDocument
import com.gerko.fourenote.data.ink.InkLayer
import com.gerko.fourenote.data.ink.InkPage
import com.gerko.fourenote.data.ink.PageBackground
import com.gerko.fourenote.data.ink.PageImage
import com.gerko.fourenote.data.ink.InkSerializer
import com.gerko.fourenote.data.ink.InkStroke
import com.gerko.fourenote.data.ink.PaperStyle
import com.gerko.fourenote.data.ink.Scribble
import com.gerko.fourenote.data.ink.Smoothing
import com.gerko.fourenote.data.ink.Tool
import com.gerko.fourenote.data.ink.isInsideLasso
import com.gerko.fourenote.data.ink.transformed
import com.gerko.fourenote.data.ink.withColor
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Locale

val InkWhite = Color(0xFFFFFFFF)
val InkBlack = Color(0xFF1B1B1F)
val PenColors = listOf(
    InkWhite, InkBlack, Color(0xFF1E5BD8), Color(0xFFD32F2F), Color(0xFF2E7D32), Color(0xFF7B1FA2),
)
val HighlighterColors = listOf(
    Color(0xFFFFEB3B), Color(0xFF76FF03), Color(0xFF40C4FF), Color(0xFFFF80AB), Color(0xFFFFAB40),
)
val PenWidths = listOf(1.5f, 3f, 6f)
val HighlighterWidths = listOf(12f, 20f, 30f)
val InkPenWidths = listOf(2.5f, 4.5f, 8f)
val CalligraphyWidths = listOf(6f, 10f, 16f)

sealed interface RecognitionState {
    /** [downloading] is true while the model is fetched for the first time; [progress] is 0..1 when known. */
    data class Working(val downloading: Boolean, val progress: Float? = null) : RecognitionState

    /** The best reading first, then alternatives. */
    data class Done(val readings: List<String>) : RecognitionState

    data class Failed(val message: String) : RecognitionState
}

/** Live state of one page: its fixed shape and background, and its editable ink. */
class PageState(
    val id: String,
    val width: Float,
    val height: Float,
    val background: PageBackground?,
    strokes: List<InkStroke>,
    placement: BackgroundPlacement,
    images: List<PageImage>,
    invertBackground: Boolean,
) {
    val strokes = mutableStateListOf<InkStroke>().apply { addAll(strokes) }
    var placement by mutableStateOf(placement)
    val images = mutableStateListOf<PageImage>().apply { addAll(images) }
    var invertBackground by mutableStateOf(invertBackground)

    fun toPage() = InkPage(id, width, height, background, strokes.toList(), placement, images.toList(), invertBackground)

    companion object {
        fun of(page: InkPage) = PageState(
            page.id, page.width, page.height, page.background, page.strokes, page.placement, page.images,
            page.invertBackground,
        )
    }
}

/**
 * Editor state for one note: a list of pages, each with its own ink. Every
 * change is saved immediately, so leaving the screen never loses ink.
 * Undo/redo store snapshots of all pages, which is cheap because pages and
 * strokes are immutable once snapshotted and shared between snapshots.
 */
class NoteEditorViewModel(
    val noteId: String,
    private val repository: NoteRepository,
    private val attachments: AttachmentStore,
    private val toolPreferences: ToolPreferences,
    private val onClosed: () -> Unit,
) : ViewModel() {
    private var note: Note? = null

    val pages = mutableStateListOf<PageState>()
    var title by mutableStateOf("")
        private set
    var paper by mutableStateOf(PaperStyle.LINED)
        private set
    var loaded by mutableStateOf(false)
        private set

    /** The note's layers, bottom to top. */
    val layers = mutableStateListOf(InkLayer.BASE)

    /** New ink goes on this layer; the eraser and lasso only touch it. */
    var activeLayerId by mutableStateOf(InkLayer.BASE_ID)
        private set

    /** A one-off message for the user, such as an import error; cleared once shown. */
    var message by mutableStateOf<String?>(null)
        private set

    /** True while the note is being exported as a PDF. */
    var exporting by mutableStateOf(false)
        private set

    /**
     * Exports the note as it is on screen to a PDF in the cache and hands the
     * file to [onReady] (for sharing). Original PDF pages are copied, ink is vector.
     */
    fun exportPdf(onReady: (java.io.File) -> Unit) {
        val base = note ?: return
        if (exporting) return
        val snapshot = InkDocument(pages.map { it.toPage() }, paper, layers.toList())
        val name = title.ifBlank { "Untitled" }.replace(Regex("""[\\/:*?"<>|]"""), "_").take(80)
        viewModelScope.launch {
            exporting = true
            try {
                val file = withContext(Dispatchers.IO) {
                    val dir = java.io.File(attachments.context.cacheDir, "exports").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    java.io.File(dir, "$name.pdf").also {
                        com.gerko.fourenote.export.PdfExporter.export(
                            base.copy(title = title, inkData = InkSerializer.encode(snapshot)),
                            attachments,
                            it,
                        )
                    }
                }
                onReady(file)
            } catch (e: Exception) {
                Log.w(TAG, "PDF export failed", e)
                message = "Couldn't export the PDF: ${e.message}"
            } finally {
                exporting = false
            }
        }
    }

    /** True while a PDF or scan is being copied in. */
    var importing by mutableStateOf(false)
        private set

    var tool by mutableStateOf(Tool.PEN)
        private set
    var penColor by mutableStateOf(PenColors[0])
    var penWidth by mutableStateOf(PenWidths[1])
    var inkPenWidth by mutableStateOf(InkPenWidths[1])
    var calligraphyWidth by mutableStateOf(CalligraphyWidths[1])

    /** Calligraphy nib angle in degrees from horizontal; 0 is a flat nib. */
    var nibDegrees by mutableStateOf(Calligraphy.DEFAULT_NIB_DEGREES)
    var highlighterColor by mutableStateOf(HighlighterColors[0])
    var highlighterWidth by mutableStateOf(HighlighterWidths[1])
    var stylusOnly by mutableStateOf(false)

    /** Handwriting stabilization, 0 (raw) to [Smoothing.MAX_LEVEL]. */
    var smoothing by mutableStateOf(Smoothing.DEFAULT_LEVEL)

    /** Scrubbing back and forth over ink with a pen erases it. */
    var scribbleToErase by mutableStateOf(true)

    /** False rubs out only the part under the eraser; true removes whole strokes. */
    var eraseWholeStrokes by mutableStateOf(false)

    // Declared before the init block below, which restores them from the saved settings.
    val customColors = mutableStateListOf<Color>()
    var primaryButtonAction by mutableStateOf(PenButtonAction.OPEN_DIAL)
    var secondaryButtonAction by mutableStateOf(PenButtonAction.ERASE)

    /** The last pen button or eraser signal the editor received, shown in the pen settings to help set them up. */
    var lastPenSignal by mutableStateOf<String?>(null)
    var twoFingerDoubleTapUndo by mutableStateOf(true)
    var threeFingerDoubleTapRedo by mutableStateOf(true)

    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    /** Strokes picked with the lasso, all on [selectionPageId]. */
    var selection by mutableStateOf<Set<InkStroke>>(emptySet())
        private set
    var selectionPageId by mutableStateOf<String?>(null)
        private set

    private var eraseSnapshot: Snapshot? = null

    /** The picture selected with the Select tool, as (page id, image id). */
    var selectedImage by mutableStateOf<Pair<String, String>?>(null)
        private set

    private var opacityUndoRecorded = false

    /** The page whose background image is being moved/resized; drawing pauses meanwhile. */
    var adjustingPageId by mutableStateOf<String?>(null)
        private set

    private val recognizer = HandwritingRecognizer()
    private var mathRecognizer: MathRecognizer? = null

    /** True when [recognition] reads the selection as maths (LaTeX) rather than text. */
    var recognizingMath by mutableStateOf(false)
        private set

    /** Handwriting-to-text state for the selection; null when the dialog is closed. */
    var recognition by mutableStateOf<RecognitionState?>(null)
        private set

    var handwritingLanguage by mutableStateOf(
        HandwritingLanguages.firstOrNull { it.tag.substringBefore('-') == Locale.getDefault().language }
            ?: HandwritingLanguages.first(),
    )
        private set

    /** Everything undo can restore. */
    private data class Snapshot(val pages: List<InkPage>, val layers: List<InkLayer>)

    init {
        restoreTools()
        repository.markOpen(noteId)
        viewModelScope.launch {
            val loadedNote = repository.getById(noteId) ?: Note(id = noteId, title = "Untitled")
            val doc = InkSerializer.decode(loadedNote.inkData)
            note = loadedNote
            title = loadedNote.title
            paper = doc.paper
            pages.addAll(doc.pages.map(PageState::of))
            layers.clear()
            layers.addAll(doc.layers)
            activeLayerId = doc.layers.last().id
            loaded = true
        }
    }

    private fun page(pageId: String) = pages.firstOrNull { it.id == pageId }

    /** Restores the last pen setup, then saves every later change so the next note starts the same. */
    private fun restoreTools() {
        val saved = toolPreferences.load()
        tool = saved.tool
        penColor = PenColors.firstOrNull { it == saved.penColor } ?: saved.penColor
        highlighterColor = saved.highlighterColor
        penWidth = saved.penWidth
        inkPenWidth = saved.inkPenWidth
        calligraphyWidth = saved.calligraphyWidth
        nibDegrees = saved.nibDegrees
        customColors.addAll(saved.customColors)
        primaryButtonAction = saved.primaryButtonAction
        secondaryButtonAction = saved.secondaryButtonAction
        twoFingerDoubleTapUndo = saved.twoFingerDoubleTapUndo
        threeFingerDoubleTapRedo = saved.threeFingerDoubleTapRedo
        highlighterWidth = saved.highlighterWidth
        smoothing = saved.smoothing
        stylusOnly = saved.stylusOnly
        scribbleToErase = saved.scribbleToErase
        eraseWholeStrokes = saved.eraseWholeStrokes
        HandwritingLanguages.firstOrNull { it.tag == saved.handwritingLanguage }?.let { handwritingLanguage = it }
        viewModelScope.launch {
            snapshotFlow {
                ToolSettings(
                    tool, penColor, highlighterColor, penWidth, inkPenWidth, calligraphyWidth, highlighterWidth,
                    smoothing, stylusOnly, scribbleToErase, eraseWholeStrokes, handwritingLanguage.tag, nibDegrees,
                    customColors.toList(), primaryButtonAction, secondaryButtonAction, twoFingerDoubleTapUndo, threeFingerDoubleTapRedo,
                )
            }.drop(1).collect(toolPreferences::save)
        }
    }

    /** Colors offered for the current tool (highlighters have their own). */
    /** The standard palette for the current tool, followed by the user's own colors. */
    val toolColors: List<Color> get() = (if (tool == Tool.HIGHLIGHTER) HighlighterColors else PenColors) + customColors

    /** Adds a color from the picker to the palette (once) and starts using it. */
    fun addCustomColor(color: Color) {
        if (color !in customColors && color !in PenColors && color !in HighlighterColors) customColors.add(color)
        setToolColor(color)
    }

    fun removeCustomColor(color: Color) {
        customColors.remove(color)
    }

    fun switchToPreviousTool() = selectTool(previousTool)

    /** A pen button click: switch to [target], or back to the previous tool if it's already selected. */
    fun toggleTool(target: Tool) = if (tool == target) switchToPreviousTool() else selectTool(target)

    /** Color and width a tool draws with, for strokes made with a tool lent by the pen button. */
    fun styleFor(tool: Tool): Pair<Color, Float> = when (tool) {
        Tool.HIGHLIGHTER -> highlighterColor to highlighterWidth
        Tool.INK_PEN -> penColor to inkPenWidth
        Tool.CALLIGRAPHY -> penColor to calligraphyWidth
        else -> penColor to penWidth
    }

    val toolColor: Color get() = if (tool == Tool.HIGHLIGHTER) highlighterColor else penColor

    fun setToolColor(color: Color) {
        if (tool == Tool.HIGHLIGHTER) highlighterColor = color else penColor = color
    }

    val toolWidths: List<Float>
        get() = when (tool) {
            Tool.HIGHLIGHTER -> HighlighterWidths
            Tool.INK_PEN -> InkPenWidths
            Tool.CALLIGRAPHY -> CalligraphyWidths
            else -> PenWidths
        }

    /**
     * The finer choice of thicknesses on the pen dial: the tool's three presets, the steps
     * between them, and one thinner and one thicker.
     */
    val dialWidths: List<Float>
        get() = toolWidths.let { (thin, medium, thick) ->
            listOf(thin / 2, thin, (thin + medium) / 2, medium, (medium + thick) / 2, thick, thick * 1.5f)
        }

    val toolWidth: Float
        get() = when (tool) {
            Tool.HIGHLIGHTER -> highlighterWidth
            Tool.INK_PEN -> inkPenWidth
            Tool.CALLIGRAPHY -> calligraphyWidth
            else -> penWidth
        }

    fun setToolWidth(width: Float) {
        when (tool) {
            Tool.HIGHLIGHTER -> highlighterWidth = width
            Tool.INK_PEN -> inkPenWidth = width
            Tool.CALLIGRAPHY -> calligraphyWidth = width
            else -> penWidth = width
        }
    }

    /** The drawing tool used before the current one, for the pen button's "last tool" action. */
    private var previousTool = Tool.PEN

    fun selectTool(newTool: Tool) {
        if (newTool != tool) previousTool = tool
        tool = newTool
        clearSelection()
        selectedImage = null
    }

    fun selectWithLasso(pageId: String, polygon: FloatArray) {
        selectedImage = null
        val strokes = page(pageId)?.strokes ?: return
        selection = strokes.filter { it.layerId == activeLayerId && it.isInsideLasso(polygon) }.toSet()
        selectionPageId = pageId.takeIf { selection.isNotEmpty() }
    }

    fun clearSelection() {
        selection = emptySet()
        selectionPageId = null
    }

    /** Moves and/or resizes the selection; see [InkStroke.transformed]. */
    fun transformSelection(dx: Float, dy: Float, scaleX: Float, scaleY: Float, pivotX: Float, pivotY: Float) {
        replaceSelection { it.transformed(dx, dy, scaleX, scaleY, pivotX, pivotY) }
    }

    fun recolorSelection(color: Color) {
        val argb = android.graphics.Color.argb(
            (color.alpha * 255).toInt(),
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt(),
        )
        replaceSelection { it.withColor(argb) }
    }

    fun deleteSelection() {
        val strokes = selectionPageId?.let(::page)?.strokes ?: return
        recordUndo()
        strokes.removeAll { it in selection }
        clearSelection()
        persist()
    }

    /** Adds an offset copy of the selection and selects the copy. */
    fun duplicateSelection() {
        val strokes = selectionPageId?.let(::page)?.strokes ?: return
        recordUndo()
        val copies = strokes.filter { it in selection }
            .map { it.transformed(DUPLICATE_OFFSET, DUPLICATE_OFFSET, 1f, 1f, 0f, 0f) }
        strokes.addAll(copies)
        selection = copies.toSet()
        persist()
    }

    private fun replaceSelection(transform: (InkStroke) -> InkStroke) {
        val strokes = selectionPageId?.let(::page)?.strokes ?: return
        if (selection.isEmpty()) return
        recordUndo()
        val replaced = mutableSetOf<InkStroke>()
        for (i in strokes.indices) {
            if (strokes[i] in selection) {
                strokes[i] = transform(strokes[i]).also { replaced.add(it) }
            }
        }
        selection = replaced
        persist()
    }

    fun addStroke(pageId: String, stroke: InkStroke) {
        val strokes = page(pageId)?.strokes ?: return
        if (scribbleToErase && !stroke.highlighter && Scribble.isScribble(stroke)) {
            val erased = Scribble.erasedBy(stroke, strokes.filter { it.layerId == activeLayerId }, ERASER_RADIUS_DP / 2)
            if (erased.isNotEmpty()) {
                recordUndo()
                val gone = erased.toSet()
                strokes.removeAll { it in gone }
                clearSelection()
                persist()
                return
            }
            // Nothing underneath: it was a zigzag on purpose, so keep it as ink.
        }
        recordUndo()
        strokes.add(
            InkStroke(stroke.color, stroke.width, stroke.highlighter, stroke.points, stroke.widths, activeLayerId, stroke.id, stroke.nibAngle),
        )
        // Drawing on a hidden layer would look like nothing happened.
        val index = layers.indexOfFirst { it.id == activeLayerId }
        if (index >= 0 && !layers[index].visible) layers[index] = layers[index].copy(visible = true)
        persist()
    }

    fun selectLayer(layerId: String) {
        activeLayerId = layerId
        clearSelection()
        val index = layers.indexOfFirst { it.id == layerId }
        if (index >= 0 && !layers[index].visible) setLayerVisible(layerId, true)
    }

    /** Adds a layer on top and makes it the active one. */
    fun addLayer() {
        recordUndo()
        val layer = InkLayer(name = "Layer ${layers.size + 1}")
        layers.add(layer)
        activeLayerId = layer.id
        clearSelection()
        persist()
    }

    fun setLayerVisible(layerId: String, visible: Boolean) {
        val index = layers.indexOfFirst { it.id == layerId }
        if (index < 0) return
        recordUndo()
        layers[index] = layers[index].copy(visible = visible)
        if (!visible && selectionPageId != null) clearSelection()
        persist()
    }

    /** Deletes a layer and its ink on every page. The last layer cannot be deleted. */
    fun deleteLayer(layerId: String) {
        if (layers.size <= 1) return
        recordUndo()
        layers.removeAll { it.id == layerId }
        for (page in pages) page.strokes.removeAll { it.layerId == layerId }
        if (activeLayerId == layerId) activeLayerId = layers.last().id
        clearSelection()
        persist()
    }

    fun beginErase() {
        eraseSnapshot = snapshot()
    }

    fun eraseAt(pageId: String, x: Float, y: Float) {
        val strokes = page(pageId)?.strokes ?: return
        if (eraseWholeStrokes) {
            strokes.removeAll { it.layerId == activeLayerId && it.isHitBy(x, y, ERASER_RADIUS_DP) }
            return
        }
        var i = 0
        while (i < strokes.size) {
            val pieces = if (strokes[i].layerId == activeLayerId) strokes[i].erasedAround(x, y, ERASER_RADIUS_DP) else null
            if (pieces == null) {
                i++
            } else {
                strokes.removeAt(i)
                strokes.addAll(i, pieces)
                i += pieces.size
            }
        }
    }

    fun endErase() {
        val before = eraseSnapshot ?: return
        eraseSnapshot = null
        if (before != snapshot()) {
            recordUndo(before)
            persist()
        }
    }

    /** Clears the active layer's ink on one page. */
    fun clearPage(pageId: String) {
        val strokes = page(pageId)?.strokes ?: return
        if (strokes.none { it.layerId == activeLayerId }) return
        recordUndo()
        strokes.removeAll { it.layerId == activeLayerId }
        clearSelection()
        persist()
    }

    /** Adds a blank page after [afterPageId], or at the end. Returns its index. */
    fun addBlankPage(afterPageId: String? = null): Int {
        recordUndo()
        val index = afterPageId?.let { id -> pages.indexOfFirst { it.id == id } + 1 }?.takeIf { it > 0 } ?: pages.size
        pages.add(index, PageState.of(InkPage()))
        persist()
        return index
    }

    /**
     * Places pictures on a page, centered and sized to about 60% of its width,
     * each a little offset from the last. Switches to Select with the last one
     * selected, so it can be moved straight away.
     */
    fun insertImages(pageId: String, opens: List<() -> InputStream?>) {
        val page = page(pageId) ?: return
        viewModelScope.launch {
            importing = true
            try {
                val stored = opens.mapNotNull { it() }.map { attachments.storeImage(noteId, it, keepTransparency = true) }
                if (stored.isEmpty()) return@launch
                recordUndo()
                stored.forEachIndexed { i, image ->
                    val width = minOf(page.width * 0.6f, page.width - 48f)
                    var height = width * image.heightPx / image.widthPx.coerceAtLeast(1)
                    val fit = minOf(1f, page.height * 0.6f / height)
                    height *= fit
                    page.images.add(
                        PageImage(
                            file = image.file,
                            x = page.width / 2 + i * 24f,
                            y = page.height / 2 + i * 24f,
                            width = width * fit,
                            height = height,
                        ),
                    )
                }
                persist()
                tool = Tool.LASSO
                clearSelection()
                selectedImage = page.id to page.images.last().id
            } catch (e: Exception) {
                message = e.message ?: "Import failed"
            } finally {
                importing = false
            }
        }
    }

    fun selectImage(pageId: String, imageId: String?) {
        clearSelection()
        selectedImage = imageId?.let { pageId to it }
    }

    private fun selected(): Pair<PageState, Int>? {
        val (pageId, imageId) = selectedImage ?: return null
        val page = page(pageId) ?: return null
        val index = page.images.indexOfFirst { it.id == imageId }
        return if (index < 0) null else page to index
    }

    val selectedImageObject: PageImage? get() = selected()?.let { (page, i) -> page.images[i] }

    /** Saves a finished move, resize or rotation of a picture. */
    fun updateImage(pageId: String, image: PageImage) {
        val page = page(pageId) ?: return
        val index = page.images.indexOfFirst { it.id == image.id }
        if (index < 0 || page.images[index] == image) return
        recordUndo()
        page.images[index] = image
        persist()
    }

    fun deleteSelectedImage() {
        val (page, index) = selected() ?: return
        recordUndo()
        page.images.removeAt(index)
        selectedImage = null
        persist()
    }

    fun duplicateSelectedImage() {
        val (page, index) = selected() ?: return
        recordUndo()
        val copy = page.images[index].let { it.copy(id = java.util.UUID.randomUUID().toString(), x = it.x + 24f, y = it.y + 24f) }
        page.images.add(copy)
        selectedImage = page.id to copy.id
        persist()
    }

    fun rotateSelectedImage(degrees: Float) {
        val (page, index) = selected() ?: return
        recordUndo()
        page.images[index] = page.images[index].let { it.copy(rotation = (it.rotation + degrees) % 360f) }
        persist()
    }

    fun toggleSelectedImageInverted() {
        val (page, index) = selected() ?: return
        recordUndo()
        page.images[index] = page.images[index].let { it.copy(inverted = !it.inverted) }
        persist()
    }

    fun bringSelectedImageToFront() {
        val (page, index) = selected() ?: return
        if (index == page.images.lastIndex) return
        recordUndo()
        page.images.add(page.images.removeAt(index))
        persist()
    }

    /** Live opacity while the slider moves; one undo step covers the whole drag. */
    fun setSelectedImageOpacity(opacity: Float) {
        val (page, index) = selected() ?: return
        if (!opacityUndoRecorded) {
            recordUndo()
            opacityUndoRecorded = true
        }
        page.images[index] = page.images[index].copy(opacity = opacity.coerceIn(0.05f, 1f))
        persist()
    }

    fun finishOpacityChange() {
        opacityUndoRecorded = false
    }

    /** Turns smart-inverted colors on or off for one PDF/scan page. */
    fun setBackgroundInverted(pageId: String, inverted: Boolean) {
        val page = page(pageId) ?: return
        if (page.background == null || page.invertBackground == inverted) return
        recordUndo()
        page.invertBackground = inverted
        persist()
    }

    /** Applies (or removes) inverted colors on every PDF/scan page of the note at once. */
    fun setAllBackgroundsInverted(inverted: Boolean) {
        val targets = pages.filter { it.background != null && it.invertBackground != inverted }
        if (targets.isEmpty()) return
        recordUndo()
        targets.forEach { it.invertBackground = inverted }
        persist()
    }

    fun startAdjustingBackground(pageId: String) {
        clearSelection()
        adjustingPageId = pageId
    }

    fun finishAdjustingBackground() {
        adjustingPageId = null
    }

    fun setBackgroundPlacement(pageId: String, placement: BackgroundPlacement) {
        val page = page(pageId) ?: return
        if (page.placement == placement) return
        recordUndo()
        page.placement = placement
        persist()
    }

    fun deletePage(pageId: String) {
        val index = pages.indexOfFirst { it.id == pageId }
        if (index < 0) return
        recordUndo()
        pages.removeAt(index)
        if (pages.isEmpty()) pages.add(PageState.of(InkPage()))
        if (selectionPageId == pageId) clearSelection()
        persist()
    }

    /**
     * Appends one page per PDF page, optionally with colors inverted for dark
     * reading; [onAdded] gets the index of the first new page.
     */
    fun importPdf(open: () -> InputStream?, inverted: Boolean, onAdded: (Int) -> Unit) =
        importPages(onAdded) {
            listOfNotNull(open()).flatMap { attachments.importPdf(noteId, it) }.map { it.copy(invertBackground = inverted) }
        }

    /** Appends one page per scanned image, in order. */
    fun importImages(opens: List<() -> InputStream?>, onAdded: (Int) -> Unit) =
        importPages(onAdded) { opens.mapNotNull { it() }.map { attachments.importImage(noteId, it) } }

    private fun importPages(onAdded: (Int) -> Unit, load: suspend () -> List<InkPage>) {
        viewModelScope.launch {
            importing = true
            try {
                val newPages = load()
                if (newPages.isEmpty()) {
                    Log.w(TAG, "Import produced no pages")
                    message = "Nothing to import"
                    return@launch
                }
                recordUndo()
                // A note that is still one empty page is replaced rather than left blank at the top.
                val replaceBlank = pages.size == 1 && pages[0].strokes.isEmpty() && pages[0].background == null
                if (replaceBlank) pages.clear()
                val first = pages.size
                pages.addAll(newPages.map(PageState::of))
                persist()
                onAdded(first)
            } catch (e: Exception) {
                Log.w(TAG, "Import failed", e)
                message = e.message ?: "Import failed"
            } finally {
                importing = false
            }
        }
    }

    /** Reads the lasso selection as text; results arrive in [recognition]. */
    fun recognizeSelection() {
        val page = selectionPageId?.let(::page) ?: return
        val strokes = page.strokes.filter { it in selection }
        if (strokes.isEmpty()) return
        recognizingMath = false
        viewModelScope.launch {
            val language = handwritingLanguage
            val needsDownload = !recognizer.isDownloadedSafely(language.tag)
            // ML Kit would otherwise wait silently forever for a connection.
            if (needsDownload && !online()) {
                recognition = RecognitionState.Failed(
                    "${language.label} handwriting needs a one-time download (about 20 MB). " +
                        "Connect to the internet and try again; after that it works offline.",
                )
                return@launch
            }
            recognition = RecognitionState.Working(downloading = needsDownload)
            recognition = try {
                val readings = recognizer.recognize(strokes, language.tag)
                if (readings.isEmpty()) RecognitionState.Failed("No text found") else RecognitionState.Done(readings)
            } catch (e: Exception) {
                RecognitionState.Failed(e.message ?: "Recognition failed")
            }
        }
    }

    /** Reads the lasso selection as one formula and writes it as LaTeX; results arrive in [recognition]. */
    fun recognizeSelectionAsMath() {
        val page = selectionPageId?.let(::page) ?: return
        val strokes = page.strokes.filter { it in selection }
        if (strokes.isEmpty()) return
        recognizingMath = true
        val math = mathRecognizer ?: MathRecognizer(attachments.context).also { mathRecognizer = it }
        viewModelScope.launch {
            recognition = try {
                if (!math.isDownloaded()) {
                    if (!online()) {
                        recognition = RecognitionState.Failed(
                            "Maths recognition needs a one-time download (about ${MathRecognizer.DOWNLOAD_MB} MB). " +
                                "Connect to the internet and try again; after that it works offline.",
                        )
                        return@launch
                    }
                    recognition = RecognitionState.Working(downloading = true, progress = 0f)
                    math.download { recognition = RecognitionState.Working(downloading = true, progress = it) }
                }
                recognition = RecognitionState.Working(downloading = false)
                val latex = math.recognize(strokes)
                if (latex.isEmpty()) RecognitionState.Failed("No formula found") else RecognitionState.Done(listOf(latex))
            } catch (e: Exception) {
                RecognitionState.Failed(e.message ?: "Recognition failed")
            }
        }
    }

    fun changeHandwritingLanguage(language: HandwritingLanguage) {
        handwritingLanguage = language
        if (recognition != null) recognizeSelection()
    }

    fun closeRecognition() {
        recognition = null
    }

    private fun online(): Boolean {
        val connectivity = attachments.context.getSystemService(android.net.ConnectivityManager::class.java)
        val caps = connectivity?.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun HandwritingRecognizer.isDownloadedSafely(tag: String) =
        runCatching { isDownloaded(tag) }.getOrDefault(true)

    fun showMessage(text: String) {
        message = text
    }

    fun messageShown() {
        message = null
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(snapshot())
        restore(previous)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(snapshot())
        restore(next)
    }

    fun rename(newTitle: String) {
        title = newTitle
        persist()
    }

    fun changePaper(style: PaperStyle) {
        paper = style
        persist()
    }

    private fun snapshot() = Snapshot(pages.map { it.toPage() }, layers.toList())

    private fun restore(snapshot: Snapshot) {
        clearSelection()
        selectedImage = null
        pages.clear()
        pages.addAll(snapshot.pages.map(PageState::of))
        layers.clear()
        layers.addAll(snapshot.layers)
        if (layers.none { it.id == activeLayerId }) activeLayerId = layers.last().id
        updateUndoFlags()
        persist()
    }

    private fun recordUndo(before: Snapshot = snapshot()) {
        undoStack.addLast(before)
        if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        redoStack.clear()
        updateUndoFlags()
    }

    private fun updateUndoFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    /** A change not yet written to the database. */
    private class PendingSave(val title: String, val doc: InkDocument)

    private var pendingSave: PendingSave? = null
    private var saveJob: Job? = null

    /**
     * Schedules a save. Turning a big note into text takes a while, so it
     * happens on a background thread shortly after editing pauses; doing it
     * after every stroke on the main thread made drawing stutter. Taking the
     * snapshot here is cheap (pages and strokes are shared, not copied).
     */
    private fun persist() {
        if (note == null) return
        pendingSave = PendingSave(title, InkDocument(pages.map { it.toPage() }, paper, layers.toList()))
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            writePendingSave()
        }
    }

    private suspend fun writePendingSave() {
        val pending = pendingSave ?: return
        val base = note ?: return
        pendingSave = null
        val updated = withContext(Dispatchers.Default) {
            base.copy(title = pending.title, inkData = InkSerializer.encode(pending.doc))
        }
        note = updated
        withContext(NonCancellable) { repository.save(updated) }
    }

    override fun onCleared() {
        recognizer.close()
        mathRecognizer?.close()
        // The screen is gone but the last edits may not be saved yet: finish that first,
        // then let sync know the note is free.
        CoroutineScope(Dispatchers.Default + NonCancellable).launch {
            writePendingSave()
            repository.markClosed(noteId)
            withContext(Dispatchers.Main) { onClosed() }
        }
    }

    class Factory(
        private val noteId: String,
        private val repository: NoteRepository,
        private val attachments: AttachmentStore,
        private val toolPreferences: ToolPreferences,
        private val onClosed: () -> Unit,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NoteEditorViewModel(noteId, repository, attachments, toolPreferences, onClosed) as T
    }

    private companion object {
        const val MAX_UNDO = 100
        const val SAVE_DELAY_MS = 400L
        const val TAG = "NoteEditor"
        const val DUPLICATE_OFFSET = 24f
    }
}
