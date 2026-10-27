package com.gerko.fourenote.ios.store

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.gerko.fourenote.data.ink.InkDocument
import com.gerko.fourenote.data.ink.InkPage
import com.gerko.fourenote.data.ink.InkSerializer
import com.gerko.fourenote.data.ink.InkStroke
import com.gerko.fourenote.ios.home.HomeScreen
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NoteStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun store(now: () -> Long = { 1_000L }): NoteStore =
        NoteStore(NoteFiles(tmp.newFolder()), now)

    @Test
    fun createSavesAndReadsBack() {
        val store = store()
        val note = store.create("First")
        assertEquals("First", note.title)
        assertEquals(1, note.document.pages.size)

        val loaded = store.get(note.id)
        assertNotNull(loaded)
        assertEquals(note.id, loaded!!.id)
        assertEquals("First", loaded.title)
    }

    @Test
    fun saveWithStrokesRoundTripsInk() {
        val store = store()
        val note = store.create("Ink")
        val stroke = InkStroke(
            color = 0xFF1E5AA8.toInt(),
            width = 4f,
            highlighter = false,
            points = floatArrayOf(10f, 20f, 30f, 40f, 50f, 60f),
        )
        val doc = InkDocument(pages = listOf(InkPage(strokes = listOf(stroke))))
        store.save(note.copy(document = doc))

        val loaded = store.get(note.id)!!
        val loadedStroke = loaded.document.pages.single().strokes.single()
        assertEquals(stroke.color, loadedStroke.color)
        assertEquals(stroke.width, loadedStroke.width, 0.001f)
        assertTrue(stroke.points.contentEquals(loadedStroke.points))
        // The whole document survives the JSON round trip.
        assertEquals(InkSerializer.encode(doc), InkSerializer.encode(loaded.document))
    }

    @Test
    fun renameChangesTitle() {
        val store = store()
        val note = store.create("Old")
        store.rename(note.id, "New")
        assertEquals("New", store.get(note.id)!!.title)
    }

    @Test
    fun deleteRemovesTheNote() {
        val store = store()
        val note = store.create("Gone")
        store.delete(note.id)
        assertNull(store.get(note.id))
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun allIsNewestFirst() {
        var t = 1_000L
        val store = store(now = { t++ })
        val a = store.create("A")
        val b = store.create("B")
        val c = store.create("C")
        assertEquals(listOf(c.id, b.id, a.id), store.all().map { it.id })
    }

    @Test
    fun unreadableFileIsSkipped() {
        val folder = tmp.newFolder()
        File(folder, "broken.json").writeText("not json at all")
        val store = NoteStore(NoteFiles(folder))
        val good = store.create("Good")
        assertEquals(listOf(good.id), store.all().map { it.id })
    }

    @Test
    fun homeScreenRendersToPng() {
        val now = System.currentTimeMillis()
        val store = store { now }
        // The sample notes (with ink), edited a minute, an hour and two days ago.
        com.gerko.fourenote.ios.SampleNotes.all.zip(listOf(60_000L, 3_600_000L, 172_800_000L)).forEach { (sample, age) ->
            val note = store.create(sample.title)
            store.save(note.copy(document = sample.document, updatedAt = now - age))
        }
        val scene = ImageComposeScene(width = 1366, height = 1024, density = Density(1f)) {
            HomeScreen(store = store, onOpen = {}, onSettings = {})
        }
        val image = scene.render()
        val data = image.encodeToData(EncodedImageFormat.PNG)
            ?: error("Skia could not encode the screenshot")
        val out = File("build/screenshots/home.png")
        out.parentFile?.mkdirs()
        out.writeBytes(data.bytes)
        scene.close()
        assertTrue("Screenshot is empty", out.length() > 0L)
    }
}
