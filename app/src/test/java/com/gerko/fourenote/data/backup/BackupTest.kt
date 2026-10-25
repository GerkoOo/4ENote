package com.gerko.fourenote.data.backup

import com.gerko.fourenote.data.Folder
import com.gerko.fourenote.data.Note
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File

class BackupTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private inner class Device {
        val ink = tmp.newFolder()
        val attachments = tmp.newFolder()
        val notes = linkedMapOf<String, Note>()
        var folders = listOf<Folder>()
        var now = 1_000L
        val backup = Backup(
            ink, attachments,
            notes = { notes.values.toList() },
            folders = { folders },
            saveNote = { notes[it.id] = it },
            saveFolders = { folders = it },
            forgetInk = {},
            clock = { now },
        )

        fun write(id: String, title: String, updatedAt: Long, page: String, folder: String? = null) {
            notes[id] = Note(id = id, title = title, updatedAt = updatedAt, folderId = folder, dirty = false)
            File(ink, "$id/pages").mkdirs()
            File(ink, "$id/manifest.json").writeText("""{"pages":["p1"]}""")
            File(ink, "$id/pages/p1.ink").writeText(page)
        }

        fun page(id: String) = File(ink, "$id/pages/p1.ink").readText()
    }

    private fun Device.save(): ByteArray = ByteArrayOutputStream().also { runBlocking { backup.export(it, "test") } }.toByteArray()

    @Test
    fun aDeviceGetsEverythingBackFromABackup() {
        val a = Device()
        a.folders = listOf(Folder("f1", "School", null, 10))
        a.write("n1", "Maths", 100, "ink of maths", folder = "f1")
        File(a.attachments, "n1").mkdirs()
        File(a.attachments, "n1/photo.jpg").writeText("JPEG")
        val file = a.save()

        val b = Device()
        val result = runBlocking { b.backup.restore(file.inputStream()) }
        assertEquals(1, result.added)
        assertEquals("ink of maths", b.page("n1"))
        assertEquals("JPEG", File(b.attachments, "n1/photo.jpg").readText())
        assertEquals("f1", b.notes.getValue("n1").folderId)
        assertEquals("School", b.folders.single().name)
        assertTrue(b.notes.getValue("n1").dirty) // so it syncs
    }

    @Test
    fun newerWorkHereIsKept() {
        val a = Device()
        a.write("n1", "Maths", 100, "old ink")
        val file = a.save()
        a.write("n1", "Maths", 200, "new ink") // written after the backup
        val result = runBlocking { a.backup.restore(file.inputStream()) }
        assertEquals(1, result.kept)
        assertEquals("new ink", a.page("n1"))
    }

    @Test
    fun aDeletedNoteComesBack() {
        val a = Device()
        a.write("n1", "Maths", 100, "ink")
        val file = a.save()
        a.notes["n1"] = a.notes.getValue("n1").copy(deleted = true, updatedAt = 300)
        runBlocking { a.backup.restore(file.inputStream()) }
        assertFalse(a.notes.getValue("n1").deleted)
    }

    @Test
    fun aNoteWithoutInkComesBackToo() {
        val a = Device()
        a.notes["n1"] = Note(id = "n1", title = "Only a title", updatedAt = 100, dirty = false)
        val file = a.save()
        val b = Device()
        val result = runBlocking { b.backup.restore(file.inputStream()) }
        assertEquals(1, result.added)
        assertEquals("Only a title", b.notes.getValue("n1").title)
    }

    @Test
    fun aRestoredNoteLeavesDeletedNotes() {
        val a = Device()
        a.write("n1", "Maths", 100, "ink")
        val file = a.save()
        a.notes["n1"] = a.notes.getValue("n1").copy(trashedAt = 50, updatedAt = 50)
        runBlocking { a.backup.restore(file.inputStream()) }
        assertEquals(null, a.notes.getValue("n1").trashedAt)
    }

    @Test(expected = IllegalArgumentException::class)
    fun anyOtherZipIsRefused() {
        val other = ByteArrayOutputStream().also { out ->
            java.util.zip.ZipOutputStream(out).use { z -> z.putNextEntry(java.util.zip.ZipEntry("hello.txt")); z.write(1) }
        }.toByteArray()
        runBlocking { Device().backup.restore(other.inputStream()) }
    }
}
