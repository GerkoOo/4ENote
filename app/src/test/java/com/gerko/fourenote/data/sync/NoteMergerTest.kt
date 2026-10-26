package com.gerko.fourenote.data.sync

import com.gerko.fourenote.data.Note
import com.gerko.fourenote.data.ink.InkDocument
import com.gerko.fourenote.data.ink.InkPage
import com.gerko.fourenote.data.ink.InkSerializer
import com.gerko.fourenote.data.ink.InkStroke
import com.gerko.fourenote.data.ink.LinkTarget
import com.gerko.fourenote.data.ink.PageLink
import com.gerko.fourenote.data.ink.StickyNote
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteMergerTest {
    private fun stroke(id: String) = InkStroke(1, 3f, false, floatArrayOf(0f, 0f, 5f, 5f), id = id)

    private fun note(updatedAt: Long, page: InkPage) = Note(
        id = "n",
        title = "Note",
        updatedAt = updatedAt,
        inkData = InkSerializer.encode(InkDocument(listOf(page))),
    )

    private fun links(note: Note) = InkSerializer.decode(note.inkData).pages[0].links

    private val basePage = InkPage(id = "p", strokes = listOf(stroke("s1"), stroke("s2")))

    @Test
    fun linksAddedOnEachSideAreBothKept() {
        val base = note(1_000, basePage)
        val local = note(2_000, basePage.copy(links = listOf(
            PageLink(id = "local", strokeIds = listOf("s1"), target = LinkTarget(noteId = "a")),
        )))
        val remote = note(1_500, basePage.copy(links = listOf(
            PageLink(id = "remote", strokeIds = listOf("s2"), target = LinkTarget(noteId = "b")),
        )))
        assertEquals(setOf("local", "remote"), links(NoteMerger.merge(base, local, remote)).map { it.id }.toSet())
    }

    @Test
    fun linkDeletedOnOneSideIsRemoved() {
        val link = PageLink(id = "l", strokeIds = listOf("s1"), target = LinkTarget(noteId = "a"))
        val base = note(1_000, basePage.copy(links = listOf(link)))
        val local = note(2_000, basePage)
        val remote = note(1_500, basePage.copy(links = listOf(link)))
        assertEquals(emptyList<PageLink>(), links(NoteMerger.merge(base, local, remote)))
    }

    @Test
    fun linkWhoseAnchorsWereErasedOnTheOtherSideIsDropped() {
        val link = PageLink(id = "l", strokeIds = listOf("s1"), target = LinkTarget(noteId = "a"))
        val base = note(1_000, basePage.copy(links = listOf(link)))
        // The other side erased the stroke the link hangs from; this side only edited the title.
        val local = note(2_000, basePage.copy(links = listOf(link)))
        val remote = note(1_500, basePage.copy(strokes = listOf(stroke("s2")), links = listOf(link)))
        assertEquals(emptyList<PageLink>(), links(NoteMerger.merge(base, local, remote)))
    }

    private fun stickies(note: Note) = InkSerializer.decode(note.inkData).pages[0].stickies

    private val sticky = StickyNote(id = "st", x = 10f, y = 20f)

    @Test
    fun stickyAddedOnOneSideSurvives() {
        val base = note(1_000, basePage)
        val local = note(2_000, basePage.copy(stickies = listOf(sticky)))
        val remote = note(1_500, basePage)
        assertEquals(listOf("st"), stickies(NoteMerger.merge(base, local, remote)).map { it.id })
    }

    @Test
    fun inkAddedInsideTheSameStickyOnBothSidesKeepsBothStrokes() {
        val base = note(1_000, basePage.copy(stickies = listOf(sticky)))
        val local = note(2_000, basePage.copy(stickies = listOf(sticky.copy(strokes = listOf(stroke("l"))))))
        val remote = note(1_500, basePage.copy(stickies = listOf(sticky.copy(strokes = listOf(stroke("r"))))))
        val merged = stickies(NoteMerger.merge(base, local, remote)).single()
        assertEquals(setOf("l", "r"), merged.strokes.map { it.id }.toSet())
    }

    @Test
    fun stickyMovedOnBothSidesKeepsTheNewerPosition() {
        val base = note(1_000, basePage.copy(stickies = listOf(sticky)))
        val local = note(2_000, basePage.copy(stickies = listOf(sticky.copy(x = 100f, y = 200f))))
        val remote = note(1_500, basePage.copy(stickies = listOf(sticky.copy(x = 300f, y = 400f))))
        val merged = stickies(NoteMerger.merge(base, local, remote)).single()
        assertEquals(100f, merged.x)
        assertEquals(200f, merged.y)
    }

    @Test
    fun stickyDeletedOnOneSideIsDeleted() {
        val base = note(1_000, basePage.copy(stickies = listOf(sticky)))
        val local = note(2_000, basePage)
        val remote = note(1_500, basePage.copy(stickies = listOf(sticky)))
        assertEquals(emptyList<StickyNote>(), stickies(NoteMerger.merge(base, local, remote)))
    }

    @Test
    fun linkEditedOnBothSidesKeepsTheNewerVersion() {
        val base = note(1_000, basePage.copy(links = listOf(
            PageLink(id = "l", strokeIds = listOf("s1"), target = LinkTarget(noteId = "a")),
        )))
        val local = note(2_000, basePage.copy(links = listOf(
            PageLink(id = "l", strokeIds = listOf("s1"), target = LinkTarget(noteId = "local")),
        )))
        val remote = note(1_500, basePage.copy(links = listOf(
            PageLink(id = "l", strokeIds = listOf("s1"), target = LinkTarget(noteId = "remote")),
        )))
        assertEquals("local", links(NoteMerger.merge(base, local, remote)).single().target.noteId)
    }
}
