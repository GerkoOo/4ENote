package com.gerko.fourenote.data

import com.gerko.fourenote.data.ink.InkDocument
import com.gerko.fourenote.data.ink.InkPage
import com.gerko.fourenote.data.ink.InkSerializer
import com.gerko.fourenote.data.sync.NoteFile
import com.gerko.fourenote.data.sync.readLimited
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Names from the cloud or a backup must never lead outside the app's note folders. */
class SafeNameTest {
    private val hostile = listOf("..", ".", "../x", "a/b", "a\\b", "", "x".repeat(201), "a b", "ä", "../../shared_prefs/sync.xml")

    @Test
    fun plainNamesAreSafe() {
        assertTrue(SafeName.isSafe("image-3f2c9a1e-0b7d-4c1a-9f00-1234567890ab.jpg"))
        assertTrue(SafeName.isSafe(".import-abc"))
        assertFalse(SafeName.isSafeFromCloud(".import-abc"))
        hostile.forEach { assertFalse(it, SafeName.isSafe(it)) }
    }

    @Test
    fun hostileCloudAttachmentNamesAreIgnored() {
        assertEquals("note1" to "image-1.jpg", NoteFile.attachmentOf("note1~image-1.jpg"))
        assertNull(NoteFile.attachmentOf("note1~../../shared_prefs/sync.xml"))
        assertNull(NoteFile.attachmentOf("../x~image-1.jpg"))
        assertNull(NoteFile.attachmentOf("note1~.hidden"))
    }

    @Test
    fun hostileCloudNoteNamesAreIgnored() {
        assertEquals("note1", NoteFile.noteIdOf("note1.4enote"))
        assertNull(NoteFile.noteIdOf("../../x.4enote"))
    }

    @Test
    fun aNoteFileWithAHostileIdIsRefused() {
        val text = NoteFile.encode(Note(id = "note1", title = "x"), null)
        assertEquals("note1", NoteFile.decode(text).id)
        val hostileText = text.replace("\"note1\"", "\"../../databases\"")
        assertThrows(IllegalArgumentException::class.java) { NoteFile.decode(hostileText) }
    }

    @Test
    fun aHostilePageIdGetsANewOne() {
        val doc = InkDocument(pages = listOf(InkPage(id = "page1")))
        val text = InkSerializer.encode(doc).replace("\"page1\"", "\"../../x\"")
        val id = InkSerializer.decode(text).pages.single().id
        assertNotEquals("../../x", id)
        assertTrue(SafeName.isSafe(id))
    }

    @Test
    fun downloadsHaveASizeLimit() {
        assertEquals(10, ByteArray(10).inputStream().readLimited(limit = 10).size)
        assertThrows(java.io.IOException::class.java) { ByteArray(11).inputStream().readLimited(limit = 10) }
    }
}
