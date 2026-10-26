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
import com.gerko.fourenote.data.isInCloud
import com.gerko.fourenote.data.NoteRepository
import com.gerko.fourenote.data.attachments.AttachmentStore
import com.gerko.fourenote.data.recognition.HandwritingRecognizer
import com.gerko.fourenote.data.recognition.MathRecognizer
import com.gerko.fourenote.data.ink.Calligraphy
import com.gerko.fourenote.data.ink.InkDocument
import com.gerko.fourenote.data.ink.InkLayer
import com.gerko.fourenote.data.ink.InkPage
import com.gerko.fourenote.data.ink.PageImage
import com.gerko.fourenote.data.ink.TextStyleSpec
import com.gerko.fourenote.data.ink.InkStroke
import com.gerko.fourenote.data.ink.lineage
import com.gerko.fourenote.data.ink.PaperStyle
import com.gerko.fourenote.data.ink.Scribble
import com.gerko.fourenote.data.ink.StrikeOut
import com.gerko.fourenote.data.ink.Shapes
import com.gerko.fourenote.data.ink.Smoothing
import com.gerko.fourenote.data.ink.Tool
import com.gerko.fourenote.data.ink.transformed
import com.gerko.fourenote.data.ink.withoutDeadLinks
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Editor state for one note: a list of pages, each with its own ink. Every
 * change is saved immediately, so leaving the screen never loses ink.
 * Undo/redo store snapshots of all pages, which is cheap because pages and
 * strokes are immutable once snapshotted and shared between snapshots.
 */
class NoteEditorViewModel(
    val noteId: String,
    internal val repository: NoteRepository,
    internal val attachments: AttachmentStore,
    internal val toolPreferences: ToolPreferences,
    internal val onClosed: () -> Unit,
    /** Asks for a sync now (or once online), e.g. after a photo frame is created. */
    internal val requestSync: () -> Unit = {},
) : ViewModel() {
    internal var note: Note? = null

    val pages = mutableStateListOf<PageState>()
    var title by mutableStateOf("")
        internal set
    var paper by mutableStateOf(PaperStyle.LINED)
        internal set
    var loaded by mutableStateOf(false)
        internal set

    /** The note's layers, bottom to top. */
    val layers = mutableStateListOf(InkLayer.BASE)

    /** New ink goes on this layer; the eraser and lasso only touch it. */
    var activeLayerId by mutableStateOf(InkLayer.BASE_ID)
        internal set

    /** A one-off message for the user, such as an import error; cleared once shown. */
    var message by mutableStateOf<String?>(null)
        internal set

    /** True while the note is being exported as a PDF. */
    var exporting by mutableStateOf(false)
        internal set

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
                    // Earlier exports may still be on their way to another app; only old ones go.
                    val hourAgo = System.currentTimeMillis() - 60 * 60 * 1000L
                    dir.listFiles()?.filter { it.lastModified() < hourAgo }?.forEach { it.delete() }
                    java.io.File(dir, "$name.pdf").also {
                        com.gerko.fourenote.export.PdfExporter.export(base.id, snapshot, attachments, it)
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
        internal set

    var tool by mutableStateOf(Tool.PEN)
        internal set
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

    /** New text boxes typeset `$…$` as maths while typing; the text bar's Auto LaTeX switch. */
    var autoLatex by mutableStateOf(true)

    /** Calligraphy style: pointed pen (bold only going down) or flat nib (bold both ways, angle set on the dial). */
    var pointedNib by mutableStateOf(true)
    var threeFingerDoubleTapRedo by mutableStateOf(true)
    var penAndFingerSelect by mutableStateOf(false)

    internal val undoStack = ArrayDeque<Snapshot>()
    internal val redoStack = ArrayDeque<Snapshot>()
    var canUndo by mutableStateOf(false)
        internal set
    var canRedo by mutableStateOf(false)
        internal set

    /** Strokes picked with the lasso, all on [selectionPageId]. */
    var selection by mutableStateOf<Set<InkStroke>>(emptySet())
        internal set
    var selectionPageId by mutableStateOf<String?>(null)
        internal set

    internal var eraseSnapshot: Snapshot? = null

    // Pieces cut during the current rub: cutting one again still replaces the original stroke.
    private val cutThisRub = HashSet<String>()

    /** The picture selected with the Select tool, as (page id, image id). */
    var selectedImage by mutableStateOf<Pair<String, String>?>(null)
        internal set

    /** The sticky note selected with the Sticky tool, as (page id, sticky id). */
    var selectedSticky by mutableStateOf<Pair<String, String>?>(null)
        internal set

    /** The colour new sticky notes get, and the one the selected sticky is changed to. */
    var stickyColor by mutableStateOf(StickyColors.first())
        internal set

    internal var opacityUndoRecorded = false

    /** The page whose background image is being moved/resized; drawing pauses meanwhile. */
    var adjustingPageId by mutableStateOf<String?>(null)
        internal set

    internal var textRecognizer: HandwritingRecognizer? = null
    internal var mathRecognizer: MathRecognizer? = null

    /** True when [recognition] reads the selection as maths (LaTeX) rather than text. */
    var recognizingMath by mutableStateOf(false)
        internal set

    // The handwriting last read (as text or maths), so the result can be placed on the page.
    internal var mathSource: Pair<String, List<InkStroke>>? = null

    /** Handwriting-to-text state for the selection; null when the dialog is closed. */
    var recognition by mutableStateOf<RecognitionState?>(null)
        internal set

    /** Everything undo can restore. */
    internal data class Snapshot(val pages: List<InkPage>, val layers: List<InkLayer>)

    init {
        restoreTools()
        repository.markOpen(noteId)
        // Photos for this note's frames, taken on another device while it is open here.
        viewModelScope.launch {
            repository.frameFills.collect { (id, frames) -> if (id == noteId) fillFrames(frames) }
        }
        viewModelScope.launch {
            val loadedNote = repository.getById(noteId) ?: Note(id = noteId, title = "Untitled")
            val doc = repository.loadDocument(noteId)
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

    internal fun page(pageId: String) = pages.firstOrNull { it.id == pageId }

    /** Restores the last pen setup, then saves every later change so the next note starts the same. */
    internal fun restoreTools() {
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
        penAndFingerSelect = saved.penAndFingerSelect
        autoLatex = saved.autoLatex
        pointedNib = saved.pointedNib
        highlighterWidth = saved.highlighterWidth
        smoothing = saved.smoothing
        stylusOnly = saved.stylusOnly
        scribbleToErase = saved.scribbleToErase
        eraseWholeStrokes = saved.eraseWholeStrokes
        viewModelScope.launch {
            snapshotFlow {
                ToolSettings(
                    tool, penColor, highlighterColor, penWidth, inkPenWidth, calligraphyWidth, highlighterWidth,
                    smoothing, stylusOnly, scribbleToErase, eraseWholeStrokes, null, nibDegrees,
                    customColors.toList(), primaryButtonAction, secondaryButtonAction, twoFingerDoubleTapUndo, threeFingerDoubleTapRedo,
                    autoLatex, pointedNib, penAndFingerSelect,
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
    internal var previousTool = Tool.PEN

    /**
     * The last stroke, if it was a closed loop: long-pressing inside it with a finger turns it
     * into a lasso selection of what it encloses. Page id and stroke.
     */
    var recentLoop by mutableStateOf<Pair<String, InkStroke>?>(null)
        internal set

    // The tool to go back to once a selection made from a loop, or a table just inserted, is done with.
    internal var toolBeforeLoop: Tool? = null

    /** Turns [recentLoop] into a selection of the ink (or else the picture) inside it. */
    /** The text box being typed in: page id and text id. */
    var editingText by mutableStateOf<Pair<String, String>?>(null)
        internal set

    /** The look of the text box being edited, and of the next new one. */
    var textStyle by mutableStateOf(TextStyleSpec())
        internal set

    /** Starts typing in an existing text box. One undo step covers the whole edit. */
    /** Stops typing; a box left empty is removed. */
    /** Changes the style of the box being typed in (if any) and of new boxes. */
    /** Saves a finished move or resize of a sticky note. One undo step per call. */
    /** Folds a sticky note into a small icon, or unfolds it. */
    /** Sets the colour of new sticky notes, and of the selected one if there is one. */
    fun selectTool(newTool: Tool) {
        if (newTool != Tool.TEXT) finishTextEditing()
        if (newTool != tool) previousTool = tool
        tool = newTool
        toolBeforeLoop = null
        clearSelection()
        selectedImage = null
        selectedSticky = null
    }

    /** Moves and/or resizes the selection; see [InkStroke.transformed]. */
    /** Adds an offset copy of the selection and selects the copy. */
    /** Adds a drawn stroke, or erases with it: a scrub, or a line held still through words ([held]). */
    fun addStroke(pageId: String, stroke: InkStroke, held: Boolean = false) {
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
        // A line through the middle of words and straight back over them strikes them out.
        val strike = if (scribbleToErase && !stroke.highlighter) StrikeOut.backAndForth(stroke) else null
        if (strike != null) {
            val struck = StrikeOut.struckBy(strike, strokes.filter { it.layerId == activeLayerId })
            if (struck.isNotEmpty()) {
                recordUndo()
                val gone = struck.toSet()
                strokes.removeAll { it in gone }
                clearSelection()
                persist()
                return
            }
        }
        recordUndo()
        val added = InkStroke(
            stroke.color, stroke.width, stroke.highlighter, stroke.points, stroke.widths, activeLayerId, stroke.id, stroke.nibAngle, stroke.squareEnds,
        )
        strokes.add(added)
        recentLoop = if (!added.highlighter && Shapes.isLoop(added.points)) pageId to added else null
        // Drawing on a hidden layer would look like nothing happened.
        val index = layers.indexOfFirst { it.id == activeLayerId }
        if (index >= 0 && !layers[index].visible) layers[index] = layers[index].copy(visible = true)
        persist()
    }

    /** Adds a layer on top and makes it the active one. */
    /** Deletes a layer and its ink on every page. The last layer cannot be deleted. */
    /** Deletes the given layers and their ink on every page, keeping at least one layer. One undo step. */
    /** Copies each of the given layers, with its ink on every page, just above the original. One undo step. */
    /** Moves the ink of the given layers onto the lowest of them and removes the rest. One undo step. */
    fun beginErase() {
        eraseSnapshot = snapshot()
        cutThisRub.clear()
    }

    fun eraseAt(pageId: String, x: Float, y: Float) {
        val page = page(pageId) ?: return
        val strokes = page.strokes
        if (eraseWholeStrokes) {
            strokes.removeAll { it.layerId == activeLayerId && it.isHitBy(x, y, ERASER_RADIUS_DP) }
            return
        }
        // A stroke rubbed through becomes pieces with new ids; links on it move to the pieces.
        val newIds = HashMap<String, List<String>>()
        var i = 0
        while (i < strokes.size) {
            val stroke = strokes[i]
            val replacing = if (stroke.id in cutThisRub) stroke.replaces else stroke.lineage()
            val pieces = if (stroke.layerId == activeLayerId) stroke.erasedAround(x, y, ERASER_RADIUS_DP, replacing) else null
            if (pieces == null) {
                i++
            } else {
                newIds[stroke.id] = pieces.map { it.id }
                pieces.mapTo(cutThisRub) { it.id }
                strokes.removeAt(i)
                strokes.addAll(i, pieces)
                i += pieces.size
            }
        }
        page.replaceLinkStrokes(newIds)
        remapTargetObjects(page.id, newIds)
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
    /** Adds a blank page after [afterPageId], or at the end. Returns its index. */
    val selectedImageObject: PageImage? get() = selected()?.let { (page, i) -> page.images[i] }

    /** Saves a finished move, resize or rotation of a picture. */
    /** Locks the selected picture in place, or unlocks it. */
    /** Shows the selected picture as a negative (blue becomes yellow), or normally again. */
    /** The stored file of a picture in this note, e.g. for cropping. */
    /** Live opacity while the slider moves; one undo step covers the whole drag. */
    /** Turns smart-inverted colors on or off for one PDF/scan page. */
    /** Applies (or removes) inverted colors on every PDF/scan page of the note at once. */
    /** Starts moving a page by hand: one undo step covers the whole move. */
    /** Appends one page per scanned image, in order. */
    /** A photo for the scanner, the right way up and at most 3000 pixels on its long side. */
    /** Reads the lasso selection as text; results arrive in [recognition]. */
    /** Reads the lasso selection as one formula and writes it as LaTeX; results arrive in [recognition]. */
    /** Adds a link from the given objects on [pageId] to [target]. One undo step. */
    /** Removes a link from a page; its anchors stay. */
    /** The notes a link can point to, this one included. */
    internal fun online(): Boolean {
        val connectivity = attachments.context.getSystemService(android.net.ConnectivityManager::class.java)
        val caps = connectivity?.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun showMessage(text: String) {
        message = text
    }

    fun messageShown() {
        message = null
    }

    fun undo() {
        recentLoop = null
        editingText = null
        selectedSticky = null
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(snapshot())
        restore(previous)
    }

    fun redo() {
        recentLoop = null
        editingText = null
        selectedSticky = null
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

    internal fun snapshot() = Snapshot(pages.map { it.toPage() }, layers.toList())

    internal fun restore(snapshot: Snapshot) {
        clearSelection()
        selectedImage = null
        selectedSticky = null
        pages.clear()
        pages.addAll(snapshot.pages.map(PageState::of))
        layers.clear()
        layers.addAll(snapshot.layers)
        if (layers.none { it.id == activeLayerId }) activeLayerId = layers.last().id
        updateUndoFlags()
        persist()
    }

    internal fun recordUndo(before: Snapshot = snapshot()) {
        undoStack.addLast(before)
        if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        redoStack.clear()
        updateUndoFlags()
    }

    internal fun updateUndoFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    /** A change not yet written to the database. */
    internal class PendingSave(val title: String, val doc: InkDocument)

    internal var pendingSave: PendingSave? = null
    internal var saveJob: Job? = null

    /**
     * Schedules a save. Turning a big note into text takes a while, so it
     * happens on a background thread shortly after editing pauses; doing it
     * after every stroke on the main thread made drawing stutter. Taking the
     * snapshot here is cheap (pages and strokes are shared, not copied).
     */
    internal fun persist() {
        if (note == null) return
        // Links whose anchors are gone are kept in memory (undo may bring them back) but not saved.
        pendingSave = PendingSave(title, InkDocument(pages.map { it.toPage().withoutDeadLinks() }, paper, layers.toList()))
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            writePendingSave()
        }
    }

    internal suspend fun writePendingSave() {
        val pending = pendingSave ?: return
        val base = note ?: return
        pendingSave = null
        note = base.copy(title = pending.title)
        withContext(NonCancellable) { repository.saveDocument(noteId, pending.title, pending.doc) }
    }

    override fun onCleared() {
        textRecognizer?.close()
        mathRecognizer?.close()
        // A new note left without anything in it isn't kept (one already in the cloud is).
        val blank = note != null && (title.isBlank() || title == "Untitled")
        val leftBlank = blank && InkDocument(pages.map { it.toPage() }, paper, layers.toList()).isBlank
        // The screen is gone but the last edits may not be saved yet: finish that first,
        // then let sync know the note is free.
        CoroutineScope(Dispatchers.Default + NonCancellable).launch {
            writePendingSave()
            repository.markClosed(noteId)
            if (leftBlank) repository.getById(noteId)?.takeIf { !it.isInCloud }?.let { repository.delete(it) }
            withContext(Dispatchers.Main) { onClosed() }
        }
    }

    class Factory(
        private val noteId: String,
        private val repository: NoteRepository,
        private val attachments: AttachmentStore,
        private val toolPreferences: ToolPreferences,
        private val onClosed: () -> Unit,
        private val requestSync: () -> Unit = {},
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NoteEditorViewModel(noteId, repository, attachments, toolPreferences, onClosed, requestSync) as T
    }

    internal companion object {
        const val DEFAULT_TEXT_WIDTH = 360f
        const val MAX_UNDO = 100
        const val SAVE_DELAY_MS = 400L
        const val TAG = "NoteEditor"
        const val DUPLICATE_OFFSET = 24f
        const val STICKY_SIZE = 200f
    }
}
