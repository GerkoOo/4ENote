package com.gerko.fourenote.data.backup

import com.gerko.fourenote.data.Folder
import com.gerko.fourenote.data.Note
import com.gerko.fourenote.data.ink.InkDocument
import com.gerko.fourenote.data.ink.InkPage
import com.gerko.fourenote.data.ink.InkStore
import com.gerko.fourenote.data.ink.InkStroke
import com.gerko.fourenote.data.ink.LinkTarget
import com.gerko.fourenote.data.ink.PageLink
import com.gerko.fourenote.data.ink.PageText
import com.gerko.fourenote.data.ink.StickyNote
import com.gerko.fourenote.data.security.DeviceKey
import com.gerko.fourenote.data.security.NotesKey
import com.gerko.fourenote.data.security.NotesKeyring
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.random.Random

/**
 * A backup made on one device and restored on another: every note, its ink and its pictures
 * come back byte for byte, and a locked note stays encrypted on the device it lands on.
 */
class BackupRoundTripTest {
    private val work: File = Files.createTempDirectory("backup-round-trip").toFile()

    @After
    fun cleanUp() {
        work.deleteRecursively()
    }

    // The Keystore's part (wrapping the device key) is Android only; the key and AES-GCM are the real ones.
    private class Memory : DeviceKey.Store {
        override var sealedKey: String? = null
        override fun saveSealedKey(value: String) {
            sealedKey = value
        }
    }

    private object Unwrapped : NotesKeyring.Sealer {
        override fun seal(plain: ByteArray) = plain.copyOf()
        override fun open(sealed: ByteArray) = sealed.copyOf()
    }

    /** One tablet: its own ink and attachments folders, its own device key, its notes and folders. */
    private inner class Device(name: String) {
        val inkRoot = File(work, "$name/ink")
        val attachmentsRoot = File(work, "$name/attachments")
        val ink = InkStore(inkRoot, DeviceKey(Memory(), Unwrapped))
        val notes = linkedMapOf<String, Note>()
        var folders = listOf<Folder>()
        var now = 1_000L

        // Wired as NoteRepository does it.
        val backup = Backup(
            inkRoot = inkRoot,
            attachmentsRoot = attachmentsRoot,
            notes = { notes.values.toList() },
            folders = { folders },
            saveNote = { notes[it.id] = it },
            saveFolders = { folders = it },
            forgetInk = { id -> ink.forget(id) },
            readInk = { id, file -> ink.plainBytes(id, file) },
            sealInk = { id, locked -> ink.setSealed(id, locked) },
            clock = { now },
        )

        fun save(note: Note, doc: InkDocument) = runBlocking {
            if (note.locked) ink.setSealed(note.id, true)
            ink.save(note.id, doc)
            notes[note.id] = note
        }

        fun attach(noteId: String, name: String, bytes: ByteArray) {
            val dir = File(attachmentsRoot, noteId).apply { mkdirs() }
            File(dir, name).writeBytes(bytes)
        }

        fun export(): ByteArray =
            ByteArrayOutputStream().also { runBlocking { backup.export(it, "test") } }.toByteArray()

        fun restore(bytes: ByteArray) = runBlocking { backup.restore(bytes.inputStream()) }

        /** Every ink file of a note, by its path inside the note's folder. */
        fun inkFiles(noteId: String): Map<String, ByteArray> {
            val dir = File(inkRoot, noteId)
            return dir.walkTopDown()
                .filter { it.isFile && it.name != "sealed" && !it.name.endsWith(".tmp") }
                .associate { it.relativeTo(dir).invariantSeparatorsPath to ink.plainBytes(noteId, it) }
        }

        fun attachmentFiles(noteId: String): Map<String, ByteArray> =
            File(attachmentsRoot, noteId).listFiles().orEmpty()
                .filter { it.isFile && !it.name.startsWith(".") }
                .associate { it.name to it.readBytes() }

        fun strokeIds(noteId: String): List<List<String>> =
            runBlocking { InkStore(inkRoot, ink.sealingForTest()).load(noteId) }
                .let { doc -> doc!!.pages.map { page -> page.strokes.map { it.id } } }
    }

    // The store's own sealing, so a fresh store reads the same files (see SealedInkStoreTest).
    private fun InkStore.sealingForTest(): DeviceKey = DeviceKey(Memory(), Unwrapped)

    private fun stroke(random: Random, id: String) = InkStroke(
        color = random.nextInt(),
        width = 1f + random.nextFloat() * 4f,
        highlighter = random.nextBoolean(),
        points = FloatArray(20) { random.nextFloat() * 700f },
        id = id,
    )

    private fun page(random: Random, index: Int, strokes: Int) = InkPage(
        id = "page-$index",
        strokes = List(strokes) { stroke(random, "p$index-s$it") },
    )

    private fun content(random: Random): Pair<List<Note>, Map<String, InkDocument>> {
        val folders = listOf(
            Folder(id = "folder-1", name = "School", updatedAt = 10),
            Folder(id = "folder-2", name = "Home", updatedAt = 20),
        )
        val docs = LinkedHashMap<String, InkDocument>()
        val notes = ArrayList<Note>()

        fun add(id: String, title: String, doc: InkDocument, folder: String?, locked: Boolean = false) {
            docs[id] = doc
            notes += Note(
                id = id,
                title = title,
                createdAt = 100L + notes.size,
                updatedAt = 200L + notes.size,
                folderId = folder,
                locked = locked,
                dirty = false,
            )
        }

        // A long note: many pages, each with a fair amount of ink.
        add("long", "Long note", InkDocument(List(60) { page(random, it, 80) }), "folder-1")

        // Texts, links and stickies, so the manifest carries more than strokes.
        val richPage = page(random, 0, 5).copy(
            texts = listOf(PageText(id = "text-1", x = 10f, y = 20f, width = 300f, text = "Hello, backup")),
            links = listOf(
                PageLink(
                    id = "link-1",
                    strokeIds = listOf("p0-s0"),
                    target = LinkTarget(noteId = "long", pageId = "page-3"),
                ),
            ),
            stickies = listOf(
                StickyNote(
                    id = "sticky-1",
                    x = 100f,
                    y = 100f,
                    strokes = List(3) { stroke(random, "sticky-s$it") },
                    texts = listOf(PageText(id = "sticky-text", x = 4f, y = 4f, width = 100f, text = "note")),
                ),
            ),
        )
        add("rich", "Texts, links and stickies", InkDocument(listOf(richPage)), "folder-2")

        // A locked note: its files are encrypted on the device.
        add("locked", "Locked note", InkDocument(List(2) { page(random, it, 10) }), "folder-1", locked = true)

        // The rest: small notes, some at the top of the home page.
        for (i in 0 until 9) {
            val folder = when (i % 3) {
                0 -> "folder-1"
                1 -> "folder-2"
                else -> null
            }
            add("small-$i", "Small $i", InkDocument(listOf(page(random, 0, 1 + i))), folder)
        }
        return notes to docs
    }

    private fun attachmentsFor(random: Random, noteId: String): Map<String, ByteArray> =
        (1..random.nextInt(1, 4)).associate { n ->
            "image-$n.jpg" to random.nextBytes(random.nextInt(50_000, 500_001))
        }

    @Test
    fun aBackupComesBackWholeOnAnotherDevice() {
        val random = Random(7)
        val (notes, docs) = content(random)
        val a = Device("a")
        a.folders = listOf(
            Folder(id = "folder-1", name = "School", updatedAt = 10),
            Folder(id = "folder-2", name = "Home", updatedAt = 20),
        )
        val attachments = LinkedHashMap<String, Map<String, ByteArray>>()
        for (note in notes) {
            a.save(note, docs.getValue(note.id))
            val files = attachmentsFor(random, note.id)
            attachments[note.id] = files
            for ((name, bytes) in files) a.attach(note.id, name, bytes)
        }

        val file = a.export()

        val b = Device("b")
        val result = b.restore(file)
        assertEquals(notes.size, result.added)

        // Details survive.
        assertEquals(notes.map { it.id }.toSet(), b.notes.keys)
        for (note in notes) {
            val back = b.notes.getValue(note.id)
            assertEquals(note.title, back.title)
            assertEquals(note.createdAt, back.createdAt)
            assertEquals(note.folderId, back.folderId)
            assertEquals(note.locked, back.locked)
        }
        assertEquals(a.folders.map { it.id to it.name }.toSet(), b.folders.map { it.id to it.name }.toSet())

        // Ink and pictures come back byte for byte, as plain bytes.
        for (note in notes) {
            val fromA = a.inkFiles(note.id)
            val fromB = b.inkFiles(note.id)
            assertEquals("ink files of ${note.id}", fromA.keys, fromB.keys)
            for ((name, bytes) in fromA) assertArrayEquals("$name of ${note.id}", bytes, fromB.getValue(name))
            assertEquals("attachments of ${note.id}", attachments.getValue(note.id).keys, b.attachmentFiles(note.id).keys)
            for ((name, bytes) in attachments.getValue(note.id)) {
                assertArrayEquals("$name of ${note.id}", bytes, b.attachmentFiles(note.id).getValue(name))
            }
            assertEquals("strokes of ${note.id}", a.strokeIds(note.id), b.strokeIds(note.id))
        }

        // The locked note is encrypted on B; the others are not.
        assertTrue(b.ink.isSealed("locked"))
        for (note in notes) {
            val sealed = note.locked
            assertEquals("sealed marker of ${note.id}", sealed, b.ink.isSealed(note.id))
            for (file in File(b.inkRoot, note.id).walkTopDown().filter { it.isFile && it.name != "sealed" }) {
                assertEquals("${file.path} sealed", sealed, NotesKey.isEncrypted(file.readBytes()))
            }
        }
    }

    @Test
    fun restoringOntoTheSameDeviceChangesNothing() {
        val random = Random(7)
        val (notes, docs) = content(random)
        val a = Device("a")
        for (note in notes) {
            a.save(note, docs.getValue(note.id))
            for ((name, bytes) in attachmentsFor(random, note.id)) a.attach(note.id, name, bytes)
        }
        val before = notes.associate { it.id to a.inkFiles(it.id) }
        val sealedBefore = notes.associate { it.id to a.ink.isSealed(it.id) }

        val file = a.export()
        a.now = 5_000L
        a.restore(file)

        for (note in notes) {
            val after = a.inkFiles(note.id)
            assertEquals("ink files of ${note.id}", before.getValue(note.id).keys, after.keys)
            for ((name, bytes) in before.getValue(note.id)) assertArrayEquals("$name of ${note.id}", bytes, after.getValue(name))
            assertEquals("sealed marker of ${note.id}", sealedBefore.getValue(note.id), a.ink.isSealed(note.id))
        }
        assertTrue(a.ink.isSealed("locked"))
        for (file in File(a.inkRoot, "locked").walkTopDown().filter { it.isFile && it.name != "sealed" }) {
            assertTrue("${file.path} sealed", NotesKey.isEncrypted(file.readBytes()))
        }
        assertFalse(a.ink.isSealed("long"))
    }
}
