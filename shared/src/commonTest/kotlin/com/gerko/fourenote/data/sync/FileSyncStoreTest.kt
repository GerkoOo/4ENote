package com.gerko.fourenote.data.sync

import com.gerko.fourenote.data.Note
import com.gerko.fourenote.data.ink.InkDocument
import com.gerko.fourenote.data.ink.InkPage
import com.gerko.fourenote.data.ink.InkSerializer
import com.gerko.fourenote.data.ink.InkStroke
import com.gerko.fourenote.data.newId
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.SYSTEM
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Two devices with file stores (as on the iPad) syncing through one in-memory cloud. Common code:
 * the iOS CI runs it on the simulator too.
 */
class FileSyncStoreTest {
    private class MemoryCloud : CloudProvider {
        val files = LinkedHashMap<String, Pair<String, ByteArray>>() // id -> name, bytes
        private var next = 1
        override suspend fun listFiles() = files.map { (id, f) -> RemoteFile(id, f.first, f.second.contentHashCode().toString() + ":" + id) }
        override suspend fun download(remoteId: String) = files.getValue(remoteId).second
        override suspend fun upload(name: String, mimeType: String, content: UploadContent, remoteId: String?): RemoteFile {
            val bytes = okio.Buffer().also { it.writeAll(content.open()) }.readByteArray()
            val id = remoteId ?: "f${next++}"
            files[id] = name to bytes
            return RemoteFile(id, name, bytes.contentHashCode().toString() + ":" + id)
        }
        override suspend fun delete(remoteId: String) { files.remove(remoteId) }
    }

    private var time = 1_000L
    private val cloud = MemoryCloud()

    private inner class Device(val id: String) {
        val store = FileSyncStore(FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "4enote-test-${newId()}", FileSystem.SYSTEM, clock = { time++ })
        suspend fun sync() = SyncEngine(store, cloud, clock = { time++ }, deviceId = id).sync()
        fun draw(noteId: String, x: Float) {
            val note = store.get(noteId)!!
            val doc = InkSerializer.decode(note.inkData)
            val page = doc.pages[0]
            val stroke = InkStroke(color = 0, width = 2f, highlighter = false, points = floatArrayOf(x, x, 1f, x + 5, x + 5, 1f))
            store.save(note.copy(inkData = InkSerializer.encode(doc.copy(pages = listOf(page.copy(strokes = page.strokes + stroke))))))
        }
        fun xs(noteId: String) = InkSerializer.decode(store.get(noteId)!!.inkData).pages[0].strokes.map { it.points[0] }.sorted()
    }

    @Test
    fun offlineEditsOnTwoDevicesBothSurvive() = runTest {
        val a = Device("aaaaaaaa")
        val b = Device("bbbbbbbb")
        val note = Note(title = "Shared", inkData = InkSerializer.encode(InkDocument(pages = listOf(InkPage()))))
        a.store.save(note)
        a.draw(note.id, 10f)
        assertTrue(a.sync().errors.isEmpty())
        assertEquals(1, b.sync().downloaded)
        assertEquals(listOf(10f), b.xs(note.id))

        // Both offline, both draw on the same page.
        a.draw(note.id, 20f)
        b.draw(note.id, 30f)
        a.sync()
        b.sync()
        a.sync()
        assertEquals(listOf(10f, 20f, 30f), a.xs(note.id))
        assertEquals(listOf(10f, 20f, 30f), b.xs(note.id))

        // Erasing on one device reaches the other and stays erased.
        val doc = InkSerializer.decode(a.store.get(note.id)!!.inkData)
        val page = doc.pages[0]
        a.store.save(a.store.get(note.id)!!.copy(inkData = InkSerializer.encode(doc.copy(pages = listOf(page.copy(strokes = page.strokes.filter { it.points[0] != 20f }))))))
        a.sync()
        b.sync()
        a.sync()
        assertEquals(listOf(10f, 30f), a.xs(note.id))
        assertEquals(listOf(10f, 30f), b.xs(note.id))
        assertTrue(a.store.notes().none { it.dirty } && b.store.notes().none { it.dirty })
    }

    @Test
    fun deleteReachesTheOtherDevice() = runTest {
        val a = Device("aaaaaaaa")
        val b = Device("bbbbbbbb")
        val note = Note(title = "Gone soon", inkData = InkSerializer.encode(InkDocument()))
        a.store.save(note)
        a.sync()
        b.sync()
        b.store.delete(note.id)
        b.sync()
        a.sync()
        assertNull(a.store.get(note.id))
        assertTrue(cloud.files.isEmpty())
    }

    @Test
    fun noteOpenInTheEditorIsLeftAlone() = runTest {
        val a = Device("aaaaaaaa")
        val b = Device("bbbbbbbb")
        val note = Note(title = "Open", inkData = InkSerializer.encode(InkDocument()))
        a.store.save(note)
        a.sync()
        b.sync()
        a.draw(note.id, 5f)
        a.sync()
        b.store.setOpen(note.id, true)
        b.sync()
        assertEquals(emptyList(), b.xs(note.id))
        b.store.setOpen(note.id, false)
        b.sync()
        assertEquals(listOf(5f), b.xs(note.id))
    }
}
