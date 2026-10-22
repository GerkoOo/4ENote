package com.gerko.fourenote.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gerko.fourenote.data.attachments.AttachmentStore
import com.gerko.fourenote.data.ink.InkDocument
import com.gerko.fourenote.data.ink.InkPage
import com.gerko.fourenote.data.ink.InkSerializer
import com.gerko.fourenote.data.ink.InkStore
import com.gerko.fourenote.data.ink.InkStroke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

/** Moving notes that older versions stored in one database row (some too big to read back) into files. */
@RunWith(AndroidJUnit4::class)
class NoteRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val dao = db.noteDao()
    private val inkDir = File(context.cacheDir, "ink-test-${System.nanoTime()}")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun cleanUp() {
        scope.cancel()
        db.close()
        inkDir.deleteRecursively()
    }

    private fun repository() = NoteRepository(dao, AttachmentStore(context), InkStore(inkDir), scope)

    private fun denseDoc(pages: Int): InkDocument {
        val random = Random(7)
        return InkDocument(
            List(pages) { p ->
                InkPage(
                    id = "page-$p",
                    strokes = List(400) {
                        InkStroke(0xFF000000.toInt(), 3f, false, FloatArray(240) { random.nextFloat() * 700f })
                    },
                )
            },
        )
    }

    @Test
    fun movesAnOverSizedNoteOutOfTheDatabase() = runBlocking {
        val doc = denseDoc(pages = 8)
        val json = InkSerializer.encode(doc)
        assertTrue("test note must exceed the 2 MB row limit (${json.length})", json.length > 3_000_000)
        dao.upsert(Note(id = "big", title = "Lecture", inkData = json, syncedSnapshot = "{\"format\":\"4enote\"}"))
        dao.upsert(Note(id = "small", title = "Short", inkData = InkSerializer.encode(denseDoc(pages = 1).let { d ->
            d.copy(pages = listOf(d.pages[0].copy(strokes = d.pages[0].strokes.take(3))))
        })))

        val repository = repository()
        val loaded = repository.loadDocument("big")
        assertEquals(8, loaded.pages.size)
        assertEquals(8 * 400, loaded.pages.sumOf { it.strokes.size })
        assertEquals(3, repository.loadDocument("small").pages[0].strokes.size)

        // The database copy is gone, so no row is too big any more, and the list reads fine.
        assertEquals(0, dao.legacyInkLength("big"))
        assertEquals(0, dao.legacySnapshotLength("big"))
        assertEquals(setOf("Lecture", "Short"), repository.observeAll().first().map { it.title }.toSet())
        assertEquals("{\"format\":\"4enote\"}", repository.withContent(dao.getById("big")!!).syncedSnapshot)
    }

    @Test
    fun savesEditsToFilesAndMarksTheNoteChanged() = runBlocking {
        val repository = repository()
        repository.create(Note(id = "n", title = "Untitled"))
        val before = dao.getById("n")!!.updatedAt
        Thread.sleep(5)
        repository.saveDocument("n", "Renamed", denseDoc(pages = 2))
        val note = dao.getById("n")!!
        assertEquals("Renamed", note.title)
        assertTrue(note.updatedAt > before && note.dirty)
        assertEquals(2, repository.loadDocument("n").pages.size)
        assertEquals(1, repository.loadThumbnail("n")!!.pages.size)
    }
}
