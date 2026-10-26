package com.gerko.fourenote.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AtomicFileTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun writesContent() {
        val file = File(tmp.root, "note.txt")
        writeAtomically(file) { it.write("hello".encodeToByteArray()) }
        assertEquals("hello", file.readText())
        assertEquals(listOf("note.txt"), tmp.root.list()!!.toList())
    }

    @Test
    fun failedWriteKeepsOldFileAndLeavesNoTemp() {
        val file = File(tmp.root, "note.txt")
        file.writeText("old")
        val failure = runCatching {
            writeAtomically(file) {
                it.write("new".encodeToByteArray())
                throw IllegalStateException("disk full")
            }
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals("old", file.readText())
        assertEquals(listOf("note.txt"), tmp.root.list()!!.toList())
    }

    @Test
    fun overwritingReplaces() {
        val file = File(tmp.root, "note.txt")
        writeAtomically(file) { it.write("first".encodeToByteArray()) }
        writeAtomically(file) { it.write("second".encodeToByteArray()) }
        assertArrayEquals("second".encodeToByteArray(), file.readBytes())
    }
}
