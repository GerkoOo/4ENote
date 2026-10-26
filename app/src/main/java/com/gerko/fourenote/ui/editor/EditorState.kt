package com.gerko.fourenote.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.gerko.fourenote.data.ink.BackgroundPlacement
import com.gerko.fourenote.data.ink.InkPage
import com.gerko.fourenote.data.ink.InkStroke
import com.gerko.fourenote.data.ink.PageBackground
import com.gerko.fourenote.data.ink.PageImage
import com.gerko.fourenote.data.ink.PageLink
import com.gerko.fourenote.data.ink.PageText
import com.gerko.fourenote.data.ink.StickyNote
import com.gerko.fourenote.data.ink.withStrokesReplaced

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
/** Font size formulas are typeset at before they're scaled onto the page; big enough to stay sharp. */
internal const val FORMULA_RENDER_PX = 96f

/** Row height of an inserted table, in page units: about one line of handwriting. */
internal const val TABLE_ROW_HEIGHT = 44f

val InkPenWidths = listOf(2.5f, 4.5f, 8f)
val CalligraphyWidths = listOf(6f, 10f, 16f)

/** The colours a sticky note can have. */
val StickyColors = listOf(
    0xFFFFF59D.toInt(), 0xFFFFCC80.toInt(), 0xFFF8BBD0.toInt(),
    0xFFD1C4E9.toInt(), 0xFFB3E5FC.toInt(), 0xFFC5E1A5.toInt(),
)

/** Where a link leads, once checked against the notes on this device. */
sealed interface LinkResolution {
    /**
     * A page of this note, by index; -1 means the note's current page, so stay where you are.
     * [objectIds] are the objects the link points to that are still on the page.
     */
    data class SameNote(val pageIndex: Int, val objectIds: List<String> = emptyList()) : LinkResolution

    /**
     * A page of another note; a null [pageId] opens that note at its first page. [objectIds] are
     * the objects the link points to that are still on the page.
     */
    data class OtherNote(val noteId: String, val pageId: String?, val objectIds: List<String> = emptyList()) : LinkResolution

    /** The note the link points to is gone (deleted, in the trash, or never synced here). */
    data object NoteNotFound : LinkResolution

    /** The note is here but the page is not, e.g. it was deleted on another device. */
    data class PageNotFound(val noteId: String) : LinkResolution

    /** The page is here but none of the objects the link points to are, e.g. they were erased. */
    data class ObjectsNotFound(val noteId: String, val pageId: String) : LinkResolution
}

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
    texts: List<PageText>,
    links: List<PageLink>,
    stickies: List<StickyNote>,
) {
    val strokes = mutableStateListOf<InkStroke>().apply { addAll(strokes) }
    var placement by mutableStateOf(placement)
    val images = mutableStateListOf<PageImage>().apply { addAll(images) }
    var invertBackground by mutableStateOf(invertBackground)
    val texts = mutableStateListOf<PageText>().apply { addAll(texts) }
    val links = mutableStateListOf<PageLink>().apply { addAll(links) }
    val stickies = mutableStateListOf<StickyNote>().apply { addAll(stickies) }

    fun toPage() = InkPage(
        id, width, height, background, strokes.toList(), placement, images.toList(), invertBackground, texts.toList(),
        links = links.toList(),
        stickies = stickies.toList(),
    )

    /**
     * Carries the page's links over to strokes that were given new ids (see
     * [withStrokesReplaced]). Links whose anchors are all gone stay here, so undo can bring them
     * back; they are only dropped when the page is saved.
     */
    fun replaceLinkStrokes(replacements: Map<String, List<String>>) {
        if (replacements.isEmpty() || links.isEmpty()) return
        val replaced = links.withStrokesReplaced(replacements)
        if (replaced == links.toList()) return
        links.clear()
        links.addAll(replaced)
    }

    companion object {
        fun of(page: InkPage) = PageState(
            page.id, page.width, page.height, page.background, page.strokes, page.placement, page.images,
            page.invertBackground, page.texts, page.links, page.stickies,
        )
    }
}
