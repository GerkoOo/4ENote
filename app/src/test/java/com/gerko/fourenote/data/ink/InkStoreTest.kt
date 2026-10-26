package com.gerko.fourenote.data.ink

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.random.Random

class InkStoreTest {
    private val root: File = Files.createTempDirectory("inkstore").toFile()
    private val store = InkStore(root)

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    /** A page of dense handwriting: [count] strokes of 120 points each. */
    private fun densePage(id: String, count: Int = 400, seed: Int = 1): InkPage {
        val random = Random(seed)
        val strokes = List(count) { s ->
            var x = random.nextFloat() * 700f
            var y = random.nextFloat() * 1000f
            val points = FloatArray(240)
            for (i in 0 until 120) {
                x += random.nextFloat() * 4f - 1f
                y += random.nextFloat() * 4f - 2f
                points[2 * i] = x
                points[2 * i + 1] = y
            }
            InkStroke(
                color = random.nextInt(),
                width = 3f,
                highlighter = s % 50 == 0,
                points = points,
                widths = if (s % 3 == 0) FloatArray(120) { 0.5f + random.nextFloat() } else null,
                nibAngle = if (s % 7 == 0) 0.5f else null,
                squareEnds = s % 5 == 0,
            )
        }
        return InkPage(id = id, strokes = strokes)
    }

    private fun assertSameStrokes(expected: List<InkStroke>, actual: List<InkStroke>) {
        assertEquals(expected.size, actual.size)
        for ((e, a) in expected.zip(actual)) {
            assertEquals(e.id, a.id)
            assertEquals(e.layerId, a.layerId)
            assertEquals(e.color, a.color)
            assertEquals(e.width, a.width, 0f)
            assertEquals(e.highlighter, a.highlighter)
            assertEquals(e.nibAngle, a.nibAngle)
            assertEquals(e.squareEnds, a.squareEnds)
            // Kept to a tenth of a page unit, like the JSON format.
            assertArrayEquals(e.points, a.points, 0.051f)
            if (e.widths == null) assertNull(a.widths) else assertArrayEquals(e.widths, a.widths, 0.0051f)
        }
    }

    @Test
    fun pageCodecRoundTripsAndIsSmallerThanJson() {
        val page = densePage("p")
        val binary = InkPageCodec.encode(page.strokes)
        assertSameStrokes(page.strokes, InkPageCodec.decode(binary))
        val json = InkSerializer.encode(InkDocument(listOf(page))).length
        println("dense page: JSON ${json / 1024} KB, binary ${binary.size / 1024} KB")
        assertTrue("binary ${binary.size} vs JSON $json", binary.size * 3 < json)
    }

    @Test
    fun keepsADaysUploadsOfANoteAndAlwaysTheLastThree() = runBlocking {
        fun gz(text: String) = java.io.ByteArrayOutputStream().also { out ->
            java.util.zip.GZIPOutputStream(out).use { it.write(text.encodeToByteArray()) }
        }.toByteArray()
        val hour = 60 * 60 * 1000L
        store.save("note", InkDocument())
        // Five uploads within an hour, the version with characters a file name can't hold.
        for (i in 1..5) store.recordSent("note", "v$i/odd:\"", gz("text $i"), now = i * 60_000L)
        for (i in 1..5) assertEquals("text $i", store.readSent("note", "v$i/odd:\""))
        // A day and a half later: only the newest three stay.
        store.recordSent("note", "v6", gz("text 6"), now = 36 * hour)
        assertNull(store.readSent("note", "v1/odd:\""))
        assertNull(store.readSent("note", "v3/odd:\""))
        assertEquals("text 4", store.readSent("note", "v4/odd:\""))
        assertEquals("text 6", store.readSent("note", "v6"))
        // At most 30, however recent.
        for (i in 7..40) store.recordSent("note", "v$i", gz("text $i"), now = 36 * hour + i)
        assertNull(store.readSent("note", "v10"))
        assertEquals("text 11", store.readSent("note", "v11"))
    }

    @Test
    fun anEditedStrokeKeepsWhatItReplacesInBothFormats() {
        val original = InkStroke(color = 1, width = 2f, highlighter = false, points = floatArrayOf(0f, 0f, 5f, 5f), id = "orig")
        val moved = original.transformed(10f, 0f, 1f, 1f, 0f, 0f)
        val copy = original.transformed(10f, 0f, 1f, 1f, 0f, 0f, replacing = false)
        assertEquals(listOf("orig"), moved.replaces)
        assertEquals(emptyList<String>(), copy.replaces)
        assertEquals(listOf("orig"), original.withColor(7).replaces)
        // A second edit keeps the first one's ancestry, newest first.
        assertEquals(listOf(moved.id, "orig"), moved.withColor(7).replaces)
        val binary = InkPageCodec.decode(InkPageCodec.encode(listOf(moved, copy)))
        assertEquals(listOf(listOf("orig"), emptyList()), binary.map { it.replaces })
        val json = InkSerializer.decode(InkSerializer.encode(InkDocument(listOf(InkPage(strokes = listOf(moved, copy))))))
        assertEquals(listOf(listOf("orig"), emptyList()), json.pages.single().strokes.map { it.replaces })
    }

    @Test
    fun savesAndLoadsAWholeNote() = runBlocking {
        val doc = InkDocument(
            pages = listOf(densePage("a", 50, 1), densePage("b", 50, 2).copy(background = PageBackground.PdfPage("x.pdf", 3))),
            paper = PaperStyle.DOTS,
            layers = listOf(InkLayer.BASE, InkLayer("top", "Top", visible = false)),
        )
        store.save("note", doc)
        val loaded = InkStore(root).load("note")!! // a fresh store: nothing cached
        assertEquals(doc.paper, loaded.paper)
        assertEquals(doc.layers, loaded.layers)
        assertEquals(listOf("a", "b"), loaded.pages.map { it.id })
        assertEquals(doc.pages[1].background, loaded.pages[1].background)
        for (i in doc.pages.indices) assertSameStrokes(doc.pages[i].strokes, loaded.pages[i].strokes)
        assertFalse(root.walkTopDown().any { it.name.endsWith(".tmp") })
    }

    @Test
    fun rewritesOnlyChangedPagesAndDropsDeletedOnes() = runBlocking {
        val a = densePage("a", 20, 1)
        val b = densePage("b", 20, 2)
        val c = densePage("c", 20, 3)
        store.save("note", InkDocument(listOf(a, b, c)))
        val pages = File(root, "note/pages")
        File(pages, "a.ink").setLastModified(0)
        File(pages, "b.ink").setLastModified(0)

        // Page b gets one more stroke, page c is deleted, page a is untouched.
        val newB = b.copy(strokes = b.strokes + densePage("x", 1, 9).strokes)
        store.save("note", InkDocument(listOf(a, newB)))
        assertEquals(0L, File(pages, "a.ink").lastModified())
        assertTrue(File(pages, "b.ink").lastModified() > 0)
        assertFalse(File(pages, "c.ink").exists())
        assertEquals(21, InkStore(root).load("note")!!.pages[1].strokes.size)
    }

    @Test
    fun handlesANoteFarBeyondTheOldDatabaseLimit() = runBlocking {
        // 40 dense pages: about 30 MB as JSON, which could never be read back from one database row.
        val doc = InkDocument(List(40) { densePage("p$it", seed = it) })
        val started = System.currentTimeMillis()
        store.save("big", doc)
        val saved = System.currentTimeMillis()
        val loaded = InkStore(root).load("big")!!
        val read = System.currentTimeMillis()
        val bytes = File(root, "big").walkTopDown().filter { it.isFile }.sumOf { it.length() }
        println("40 dense pages: ${bytes / 1024} KB on disk, saved in ${saved - started} ms, loaded in ${read - saved} ms")
        assertEquals(40 * 400, loaded.pages.sumOf { it.strokes.size })
    }

    @Test
    fun thumbnailReadsOnlyTheFirstPage() = runBlocking {
        store.save("note", InkDocument(listOf(densePage("a", 5, 1), densePage("b", 5, 2))))
        val first = store.loadFirstPage("note")!!
        assertEquals(1, first.pages.size)
        assertEquals(5, first.pages[0].strokes.size)
        assertNull(store.loadFirstPage("missing"))
    }

    @Test
    fun keepsSnapshotsAndCopiesNotesWithoutThem() = runBlocking {
        store.save("note", InkDocument(listOf(densePage("a", 5, 1))))
        store.writeSnapshot("note", "{\"format\":\"4enote\"}")
        assertEquals("{\"format\":\"4enote\"}", store.readSnapshot("note"))
        store.copy("note", "copy")
        assertEquals(5, store.load("copy")!!.pages[0].strokes.size)
        assertNull(store.readSnapshot("copy"))
        store.delete("note")
        assertNull(store.load("note"))
        assertTrue(store.exists("copy"))
    }
}
