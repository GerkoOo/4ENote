package com.gerko.fourenote.data.sync

import com.gerko.fourenote.data.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteFileTest {
    @Test
    fun aLockedNoteComesBackLocked() {
        val note = Note(id = "n1", title = "Diary", locked = true)
        val back = NoteFile.decode(NoteFile.encode(note, pdfId = null))
        assertTrue(back.locked)
        assertEquals("Diary", back.title)
    }

    @Test
    fun anUnlockedNoteComesBackUnlocked() {
        val note = Note(id = "n1", title = "Notes")
        val text = NoteFile.encode(note, pdfId = null)
        // Left out of the file entirely, so older versions read it as before.
        assertFalse(text.contains("locked"))
        assertFalse(NoteFile.decode(text).locked)
    }

    @Test
    fun aFileWithoutTheFieldIsUnlocked() {
        val text = """{"format":"4enote","v":1,"id":"n1","title":"Old","createdAt":1,"updatedAt":2,"ink":""}"""
        assertFalse(NoteFile.decode(text).locked)
    }
}
