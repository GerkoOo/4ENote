package com.gerko.fourenote.data.sync

import com.gerko.fourenote.data.ink.InkDocument
import com.gerko.fourenote.data.ink.InkPage
import com.gerko.fourenote.data.ink.InkSerializer
import com.gerko.fourenote.data.ink.InkStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteFileFormatTest {
    private fun stroke(id: String) = InkStroke(color = 0, width = 1f, highlighter = false, points = floatArrayOf(0f, 0f, 1f, 1f), id = id)

    // One page id for every version, as a note keeps its pages: a new id would be a new page.
    private fun ink(vararg ids: String) = InkSerializer.encode(InkDocument(pages = listOf(InkPage(id = "p1", strokes = ids.map(::stroke)))))

    @Test
    fun roundTripKeepsEveryField() {
        val content = NoteContent(
            id = "n1",
            title = "Shopping",
            inkData = ink("s1", "s2"),
            createdAt = 1_000,
            updatedAt = 2_000,
            folderId = "f1",
            locked = true,
            rev = 7,
            remotePdfId = "pdf-1",
        )
        val text = NoteFileFormat.encode(content, pdfId = "pdf-1")
        val readSeen = HashMap<String, String>()
        val back = NoteFileFormat.decode(text, readSeen)
        assertEquals(content, back)
        assertTrue(readSeen.isEmpty())
    }

    @Test
    fun seenIsWrittenSorted() {
        val content = NoteContent(id = "n1", title = "t")
        val text = NoteFileFormat.encode(content, pdfId = null)
        // No seen: no "seen" key at all.
        assertTrue(!text.contains("\"seen\""))
        val withSeen = StringBuilder().also {
            NoteFileFormat.encodeTo(content, null, it, mapOf("zzzzzzzz" to "v2", "abcd1234" to "v1"))
        }.toString()
        val at = withSeen.indexOf("\"seen\"")
        assertTrue(at >= 0)
        assertTrue(withSeen.indexOf("abcd1234") < withSeen.indexOf("zzzzzzzz"))
    }

    @Test
    fun seenIsReadBack() {
        val content = NoteContent(id = "n1", title = "t")
        val text = StringBuilder().also {
            NoteFileFormat.encodeTo(content, null, it, mapOf("abcd1234" to "v1", NoteFileFormat.LEGACY_DEVICE to "v0"))
        }.toString()
        val seen = HashMap<String, String>()
        NoteFileFormat.decode(text, seen)
        assertEquals(mapOf("abcd1234" to "v1", NoteFileFormat.LEGACY_DEVICE to "v0"), seen)
    }

    @Test
    fun noteFileOfReadsDeviceAndLegacy() {
        assertEquals("n1" to "abcd1234", NoteFileFormat.noteFileOf("n1.abcd1234.4enote"))
        assertEquals("n1" to NoteFileFormat.LEGACY_DEVICE, NoteFileFormat.noteFileOf("n1.4enote"))
        assertNull(NoteFileFormat.noteFileOf("../x.4enote"))
        assertNull(NoteFileFormat.noteFileOf("n1.txt"))
    }

    @Test
    fun mergeKeepsStrokesAddedOnBothSides() {
        val base = NoteContent(id = "n1", title = "t", inkData = ink("s1"))
        val local = base.copy(inkData = ink("s1", "s2"), rev = 1)
        val remote = base.copy(inkData = ink("s1", "s3"), rev = 1)
        val merged = NoteContentMerger.merge(base, local, remote)
        val ids = InkSerializer.decode(merged.inkData).pages.single().strokes.map { it.id }.toSet()
        assertEquals(setOf("s1", "s2", "s3"), ids)
    }
}
