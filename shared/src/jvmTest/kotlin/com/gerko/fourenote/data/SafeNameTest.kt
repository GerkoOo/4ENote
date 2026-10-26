package com.gerko.fourenote.data

import com.gerko.fourenote.data.ink.InkDocument
import com.gerko.fourenote.data.ink.InkPage
import com.gerko.fourenote.data.ink.InkSerializer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    fun aHostilePageIdGetsANewOne() {
        val doc = InkDocument(pages = listOf(InkPage(id = "page1")))
        val text = InkSerializer.encode(doc).replace("\"page1\"", "\"../../x\"")
        val id = InkSerializer.decode(text).pages.single().id
        assertNotEquals("../../x", id)
        assertTrue(SafeName.isSafe(id))
    }
}

class BlankNoteTest {
    @Test
    fun onlyANoteWithNothingInItIsBlank() {
        val page = com.gerko.fourenote.data.ink.InkPage()
        assertTrue(InkDocument(listOf(page)).isBlank)
        assertFalse(InkDocument(listOf(page, com.gerko.fourenote.data.ink.InkPage())).isBlank)
        assertFalse(InkDocument(listOf(page.copy(texts = listOf(com.gerko.fourenote.data.ink.PageText(x = 0f, y = 0f, width = 10f, text = "a"))))).isBlank)
    }
}
