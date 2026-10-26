package com.gerko.fourenote.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** One step back along followed links: where the reader was before jumping. */
data class TrailStop(
    val noteId: String,
    val title: String,
    val pageIndex: Int,
    val scrollOffset: Int,
    val zoom: Float,
    val panX: Float,
    val panY: Float,
    val linkId: String,
)

/**
 * The way back along followed links, shared by every editor screen: following a link pushes a
 * stop, and Back pops one. It lives outside the view models because a jump can cross notes, and
 * the note left behind is closed (its view model cleared) on the way.
 */
object LinkTrail {
    val stops = mutableStateListOf<TrailStop>()

    /** A stop to go back to, set before leaving a note; the editor of that note applies it once loaded and clears it. */
    var restore by mutableStateOf<TrailStop?>(null)

    /** (noteId, pageId) to scroll to once that note opens, after following a link to another note's page. */
    var openPage by mutableStateOf<Pair<String, String>?>(null)

    fun clear() {
        stops.clear()
        restore = null
        openPage = null
    }
}
