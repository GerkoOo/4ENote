package com.gerko.fourenote.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gerko.fourenote.data.ink.LinkTarget

/**
 * A link being made whose target is picked by hand: the objects it hangs on in the source note,
 * the note whose objects it will point to, and where the reader was before leaving to pick them.
 */
data class PendingLink(
    val sourceNoteId: String,
    val sourcePageId: String,
    val strokeIds: List<String>,
    val imageIds: List<String>,
    val targetNoteId: String,
    /** Where the reader was in the source note, to go back to afterwards. */
    val back: TrailStop,
)

/**
 * Picking the objects a link points to, shared by every editor screen: the source note's editor
 * starts it, the target note's editor finishes it, and the source note's editor adds the link
 * once it is back. It lives outside the view models because the two notes are different screens.
 */
object LinkPick {
    /** The link being made, while the target note's editor is picking its objects. */
    var pending by mutableStateOf<PendingLink?>(null)

    /** A finished pick, waiting for the source note's editor to add the link. */
    var done by mutableStateOf<Pair<PendingLink, LinkTarget>?>(null)
}
