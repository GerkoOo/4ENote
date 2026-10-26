package com.gerko.fourenote.data.sync

import com.gerko.fourenote.data.Note
import com.gerko.fourenote.data.ink.InkDocument
import com.gerko.fourenote.data.ink.InkPage
import com.gerko.fourenote.data.ink.InkSerializer
import com.gerko.fourenote.data.ink.InkStroke
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Two simulated tablets, A and B, syncing through one shared cloud folder. */
class SyncEngineTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 1_000L
    private val clock = { now++ }
    private lateinit var cloudDir: java.io.File
    private lateinit var a: Device
    private lateinit var b: Device

    /** Mirrors the Room-backed repository: saving marks a note dirty with a fresh timestamp. */
    private inner class Device : SyncStore {
        val inbox = com.gerko.fourenote.data.scan.ScanInbox(tmp.newFolder())
        var folders = listOf<com.gerko.fourenote.data.Folder>()
        override suspend fun foldersForSync() = folders
        override suspend fun saveFoldersFromSync(folders: List<com.gerko.fourenote.data.Folder>) {
            this.folders = folders
        }
        val notes = linkedMapOf<String, Note>()
        val open = mutableSetOf<String>()
        val keys = com.gerko.fourenote.data.security.NotesKeyring(
            com.gerko.fourenote.data.security.NotesKeyringTest.MemoryStore(),
            com.gerko.fourenote.data.security.NotesKeyringTest.PlainSealer,
        )
        val engine = SyncEngine(
            this,
            LocalFolderCloudProvider(cloudDir),
            pdfExporter = { note -> tmp.newFile().apply { writeText("PDF ${note.title}") } },
            clock = clock,
            inbox = inbox,
            keys = keys,
        )

        fun save(note: Note) {
            notes[note.id] = note.copy(updatedAt = clock(), dirty = true)
        }

        fun delete(id: String) {
            notes[id] = notes.getValue(id).copy(deleted = true, dirty = true, updatedAt = clock())
        }

        fun strokeIds(id: String) = InkSerializer.decode(notes.getValue(id).inkData).pages.flatMap { it.strokes }.map { it.id }.toSet()

        fun pageIds(id: String) = InkSerializer.decode(notes.getValue(id).inkData).pages.map { it.id }

        fun sync() = runBlocking { engine.sync() }

        override suspend fun notesForSync() = notes.values.toList()
        override fun isOpen(noteId: String) = noteId in open
        override suspend fun insertFromCloud(note: Note, snapshot: ByteArray) {
            notes[note.id] = note.copy(syncedSnapshot = NoteFile.fromCloud(snapshot))
        }

        override suspend fun replaceIfUnchanged(note: Note, expectedUpdatedAt: Long, dirty: Boolean): Boolean {
            val current = notes[note.id] ?: return false
            if (current.updatedAt != expectedUpdatedAt) return false
            notes[note.id] = current.copy(
                title = note.title, inkData = note.inkData, createdAt = note.createdAt,
                updatedAt = note.updatedAt, dirty = dirty, locked = note.locked,
            )
            return true
        }

        override suspend fun markSynced(noteId: String, file: RemoteFile, pdfId: String?, snapshot: ByteArray, expectedUpdatedAt: Long) {
            val current = notes[noteId] ?: return
            notes[noteId] = current.copy(
                remoteId = file.id, remotePdfId = pdfId, remoteVersion = file.version,
                syncedSnapshot = NoteFile.fromCloud(snapshot), lastSyncedAt = clock(),
                dirty = current.updatedAt != expectedUpdatedAt,
            )
        }

        override suspend fun purge(noteId: String) {
            notes.remove(noteId)
            files.remove(noteId)
        }

        /** Pictures per note: file name to content. */
        val files = mutableMapOf<String, MutableMap<String, java.io.File>>()

        fun addPicture(noteId: String, name: String, content: String) {
            files.getOrPut(noteId) { mutableMapOf() }[name] = tmp.newFile().apply { writeText(content) }
        }

        override fun attachments(noteId: String): Map<String, java.io.File> = files[noteId].orEmpty()

        val delivered = mutableListOf<Pair<String, List<com.gerko.fourenote.data.ink.PageImage>>>()

        override suspend fun deliverFilledFrames(noteId: String, frames: List<com.gerko.fourenote.data.ink.PageImage>) {
            delivered += noteId to frames
        }

        override suspend fun saveAttachment(noteId: String, name: String, content: ByteArray) {
            files.getOrPut(noteId) { mutableMapOf() }[name] = tmp.newFile().apply { writeBytes(content) }
        }
    }

    private fun stroke(id: String) = InkStroke(color = 0, width = 3f, highlighter = false, points = floatArrayOf(0f, 0f, 10f, 10f), id = id)

    private fun ink(vararg ids: String) = InkSerializer.encode(InkDocument(listOf(InkPage(id = "p1", strokes = ids.map(::stroke)))))

    private fun Device.addStrokes(noteId: String, vararg ids: String) {
        val note = notes.getValue(noteId)
        val doc = InkSerializer.decode(note.inkData)
        val first = doc.pages.first()
        val pages = listOf(first.copy(strokes = first.strokes + ids.map(::stroke))) + doc.pages.drop(1)
        save(note.copy(inkData = InkSerializer.encode(doc.copy(pages = pages))))
    }

    private fun Device.removeStroke(noteId: String, id: String) {
        val note = notes.getValue(noteId)
        val doc = InkSerializer.decode(note.inkData)
        val pages = doc.pages.map { page -> page.copy(strokes = page.strokes.filter { it.id != id }) }
        save(note.copy(inkData = InkSerializer.encode(doc.copy(pages = pages))))
    }

    private fun cloudFiles() = cloudDir.listFiles().orEmpty().map { it.name }.sorted()

    /** Creates a note with strokes s1, s2 on A and syncs it to B. */
    private fun sharedNote(): String {
        a.save(Note(id = "n1", title = "Biology", inkData = ink("s1", "s2")))
        a.sync()
        b.sync()
        return "n1"
    }

    @Before
    fun setUp() {
        cloudDir = tmp.newFolder("cloud")
        a = Device()
        b = Device()
    }

    @Test
    fun noteCreatedOnOneDeviceAppearsOnTheOther() {
        sharedNote()
        assertEquals("Biology", b.notes.getValue("n1").title)
        assertEquals(setOf("s1", "s2"), b.strokeIds("n1"))
        assertEquals(false, b.notes.getValue("n1").dirty)
        assertEquals(listOf("Biology (n1).pdf", "n1.4enote"), cloudFiles())
        assertEquals("PDF Biology", java.io.File(cloudDir, "Biology (n1).pdf").readText())
    }

    @Test
    fun editOnOneDeviceUpdatesTheOther() {
        sharedNote()
        b.addStrokes("n1", "s3")
        b.sync()
        val result = a.sync()
        assertEquals(1, result.downloaded)
        assertEquals(setOf("s1", "s2", "s3"), a.strokeIds("n1"))
    }

    @Test
    fun simultaneousEditsOnBothDevicesAreMerged() {
        sharedNote()
        a.addStrokes("n1", "fromA")
        b.addStrokes("n1", "fromB")
        a.sync()
        assertEquals(1, b.sync().merged)
        a.sync()
        val expected = setOf("s1", "s2", "fromA", "fromB")
        assertEquals(expected, a.strokeIds("n1"))
        assertEquals(expected, b.strokeIds("n1"))
    }

    @Test
    fun eraseOnOneDeviceAndAddOnTheOtherBothSurvive() {
        sharedNote()
        a.removeStroke("n1", "s1")
        b.addStrokes("n1", "s3")
        a.sync()
        b.sync()
        a.sync()
        assertEquals(setOf("s2", "s3"), a.strokeIds("n1"))
        assertEquals(setOf("s2", "s3"), b.strokeIds("n1"))
    }

    @Test
    fun deleteOnOneDeviceRemovesItEverywhere() {
        sharedNote()
        a.delete("n1")
        assertEquals(1, a.sync().deletedInCloud)
        assertEquals(1, b.sync().deletedHere)
        assertNull(b.notes["n1"])
        assertTrue(cloudFiles().isEmpty())
    }

    @Test
    fun editBeatsDeleteFromAnotherDevice() {
        sharedNote()
        a.delete("n1")
        b.addStrokes("n1", "s3")
        a.sync()
        b.sync()
        a.sync()
        assertEquals(setOf("s1", "s2", "s3"), a.strokeIds("n1"))
        assertEquals(setOf("s1", "s2", "s3"), b.strokeIds("n1"))
    }

    @Test
    fun renameOnBothDevicesKeepsTheNewestTitle() {
        sharedNote()
        a.save(a.notes.getValue("n1").copy(title = "Bio (A)"))
        b.save(b.notes.getValue("n1").copy(title = "Bio (B)"))
        a.sync()
        b.sync()
        a.sync()
        assertEquals("Bio (B)", a.notes.getValue("n1").title)
        assertEquals("Bio (B)", b.notes.getValue("n1").title)
    }

    @Test
    fun pdfCopyIsUpdatedInPlaceNotDuplicated() {
        sharedNote()
        b.save(b.notes.getValue("n1").copy(title = "Chemistry"))
        b.sync()
        assertEquals(listOf("Chemistry (n1).pdf", "n1.4enote"), cloudFiles())
    }

    private fun Device.addPage(noteId: String, pageId: String) {
        val note = notes.getValue(noteId)
        val doc = InkSerializer.decode(note.inkData)
        save(note.copy(inkData = InkSerializer.encode(doc.copy(pages = doc.pages + InkPage(id = pageId)))))
    }

    @Test
    fun pagesAddedOnBothDevicesAreAllKept() {
        sharedNote()
        a.addPage("n1", "pageA")
        b.addPage("n1", "pageB")
        a.sync()
        b.sync()
        a.sync()
        // Both devices end up with every page, in the same order.
        assertEquals(setOf("p1", "pageA", "pageB"), a.pageIds("n1").toSet())
        assertEquals(a.pageIds("n1"), b.pageIds("n1"))
    }

    private fun Device.editImages(noteId: String, change: (List<com.gerko.fourenote.data.ink.PageImage>) -> List<com.gerko.fourenote.data.ink.PageImage>) {
        val note = notes.getValue(noteId)
        val doc = InkSerializer.decode(note.inkData)
        val first = doc.pages.first()
        save(note.copy(inkData = InkSerializer.encode(doc.copy(pages = listOf(first.copy(images = change(first.images))) + doc.pages.drop(1)))))
    }

    private fun Device.imageX(noteId: String) = InkSerializer.decode(notes.getValue(noteId).inkData).pages.first().images.single().x

    @Test
    fun pictureMovedOnOneDeviceWhileTheOtherWritesKeepsTheMove() {
        sharedNote()
        a.editImages("n1") { listOf(com.gerko.fourenote.data.ink.PageImage(id = "img", file = "a.png", x = 10f, y = 10f, width = 50f, height = 50f)) }
        a.sync()
        b.sync()
        // B moves the picture while A writes on the same page.
        b.editImages("n1") { images -> images.map { it.copy(x = 300f) } }
        a.addStrokes("n1", "fromA")
        b.sync()
        a.sync()
        b.sync()
        assertEquals(300f, a.imageX("n1"))
        assertEquals(300f, b.imageX("n1"))
        assertTrue("fromA" in b.strokeIds("n1"))
    }

    @Test
    fun noteTooLongForAPdfCopyStillSyncs() {
        val keys = com.gerko.fourenote.data.security.NotesKeyring(
            com.gerko.fourenote.data.security.NotesKeyringTest.MemoryStore(),
            com.gerko.fourenote.data.security.NotesKeyringTest.PlainSealer,
        )
        val engine = SyncEngine(a, LocalFolderCloudProvider(cloudDir), pdfExporter = { null }, clock = clock)
        a.save(Note(id = "big", title = "Lecture", inkData = ink("s1")))
        runBlocking { engine.sync() }
        b.sync()
        assertEquals(setOf("s1"), b.strokeIds("big"))
        assertEquals(listOf("big.4enote"), cloudFiles())
    }

    @Test
    fun missingPdfCopyIsAddedOnALaterSync() {
        // First synced by a version that skipped the PDF copy...
        val withoutPdf = SyncEngine(a, LocalFolderCloudProvider(cloudDir), pdfExporter = { null }, clock = clock)
        a.save(Note(id = "big", title = "Lecture", inkData = ink("s1")))
        runBlocking { withoutPdf.sync() }
        assertEquals(listOf("big.4enote"), cloudFiles())
        // ...then the next sync, with a working exporter, adds it without any edit.
        assertEquals(1, a.sync().uploaded)
        assertEquals(listOf("Lecture (big).pdf", "big.4enote"), cloudFiles())
        // And once it is there, quiet syncs upload nothing.
        assertEquals(0, a.sync().uploaded)
    }

    @Test
    fun aLockedNoteUploadsNoPdfCopy() {
        a.save(Note(id = "n1", title = "Diary", inkData = ink("s1"), locked = true))
        a.sync()
        b.sync()
        assertEquals(listOf("n1.4enote"), cloudFiles())
        assertTrue(b.notes.getValue("n1").locked)
        assertEquals(setOf("s1"), b.strokeIds("n1"))
    }

    @Test
    fun lockingASyncedNoteRemovesItsPdfCopy() {
        sharedNote()
        assertEquals(listOf("Biology (n1).pdf", "n1.4enote"), cloudFiles())
        a.save(a.notes.getValue("n1").copy(locked = true))
        a.sync()
        assertEquals(listOf("n1.4enote"), cloudFiles())
        assertNull(a.notes.getValue("n1").remotePdfId)
        // And the lock reaches the other device.
        b.sync()
        assertTrue(b.notes.getValue("n1").locked)
    }

    @Test
    fun noteOpenInTheEditorIsLeftAloneUntilClosed() {
        sharedNote()
        b.addStrokes("n1", "s3")
        b.sync()
        a.open += "n1"
        assertEquals(1, a.sync().skippedOpen)
        assertEquals(setOf("s1", "s2"), a.strokeIds("n1"))
        a.open -= "n1"
        a.sync()
        assertEquals(setOf("s1", "s2", "s3"), a.strokeIds("n1"))
    }

    @Test
    fun picturesTravelWithTheirNote() {
        val note = Note(id = "n1", title = "With picture")
        a.save(note)
        a.addPicture("n1", "photo.jpg", "JPEG bytes")
        a.sync()
        b.sync()
        assertEquals("JPEG bytes", b.attachments("n1").getValue("photo.jpg").readText())

        // A picture added on B reaches A too.
        b.addPicture("n1", "scan.jpg", "scan bytes")
        b.sync()
        a.sync()
        assertEquals(setOf("photo.jpg", "scan.jpg"), a.attachments("n1").keys)
    }

    @Test
    fun picturesOfDeletedNotesLeaveTheCloud() {
        a.save(Note(id = "n1", title = "Gone soon"))
        a.addPicture("n1", "photo.jpg", "JPEG bytes")
        a.sync()
        b.sync()
        a.delete("n1")
        a.sync()
        b.sync()
        assertTrue(cloudDir.listFiles().orEmpty().none { it.name.contains("~") })
    }

    @Test
    fun aPhotoFillsTheFrameEvenIfTheFrameWasMovedMeanwhile() {
        val frame = com.gerko.fourenote.data.ink.PageImage(id = "frame", file = "", x = 100f, y = 100f, width = 200f, height = 150f, requested = true)
        val page = InkPage(id = "p1", images = listOf(frame))
        a.save(Note(id = "n1", title = "Lab", inkData = InkSerializer.encode(InkDocument(listOf(page)))))
        a.sync()
        b.sync()

        // The tablet (A) moves the empty frame; the phone (B) fills it, both before syncing.
        a.save(a.notes.getValue("n1").copy(inkData = InkSerializer.encode(InkDocument(listOf(page.copy(images = listOf(frame.copy(x = 300f))))))))
        b.save(b.notes.getValue("n1").copy(inkData = InkSerializer.encode(InkDocument(listOf(page.copy(images = listOf(frame.copy(file = "photo.jpg", height = 120f))))))))
        b.sync()
        a.sync()

        val image = InkSerializer.decode(a.notes.getValue("n1").inkData).pages[0].images.single()
        assertEquals("photo.jpg", image.file)
    }

    @Test
    fun aPhotoReachesTheFrameWhileTheNoteIsOpen() {
        val frame = com.gerko.fourenote.data.ink.PageImage(id = "frame", file = "", x = 100f, y = 100f, width = 200f, height = 150f, requested = true)
        val page = InkPage(id = "p1", images = listOf(frame))
        a.save(Note(id = "n1", title = "Lab", inkData = InkSerializer.encode(InkDocument(listOf(page)))))
        a.sync()
        b.sync()

        a.open += "n1" // the tablet has the note open
        b.save(b.notes.getValue("n1").copy(inkData = InkSerializer.encode(InkDocument(listOf(page.copy(images = listOf(frame.copy(file = "photo.jpg"))))))))
        b.addPicture("n1", "photo.jpg", "JPEG")
        b.sync()
        a.sync()

        assertEquals("photo.jpg", a.delivered.single().second.single().file)
        // The photo itself arrived too, before the frame was handed over.
        assertEquals("JPEG", a.attachments("n1").getValue("photo.jpg").readText())
    }

    @Test
    fun anOpenNoteIsStillUploaded() {
        a.save(Note(id = "n1", title = "Open here"))
        a.open += "n1"
        a.sync()
        b.sync()
        assertEquals("Open here", b.notes.getValue("n1").title)
        // But an open note is never replaced from the cloud.
        b.save(b.notes.getValue("n1").copy(title = "Changed on B"))
        b.sync()
        a.sync()
        assertEquals("Open here", a.notes.getValue("n1").title)
    }

    @Test
    fun aScanForLaterReachesTheOtherDeviceAndLeavesEverywhereOnceUsed() {
        val scan = b.inbox.add("JPEG".encodeToByteArray(), frame = 2)
        b.sync()
        a.sync()
        val arrived = a.inbox.pending().single()
        assertEquals(scan.name, arrived.name)
        assertEquals(2, arrived.frame)
        assertEquals("JPEG", arrived.file.readText())

        // The tablet puts it into its frame: gone from the cloud, then from the phone too.
        a.inbox.markUsed(arrived.name)
        a.sync()
        b.sync()
        assertTrue(a.inbox.pending().isEmpty())
        assertTrue(b.inbox.pending().isEmpty())
        assertTrue(cloudDir.listFiles().orEmpty().none { it.name.startsWith(com.gerko.fourenote.data.scan.ScanInbox.CLOUD_PREFIX) })
        // And it never comes back.
        b.sync()
        a.sync()
        assertTrue(a.inbox.pending().isEmpty())
    }

    @Test
    fun aDiscardedScanIsNotUploadedAgain() {
        val scan = b.inbox.add("JPEG".encodeToByteArray(), frame = 0)
        b.sync()
        b.inbox.discard(scan.name)
        b.sync()
        a.sync()
        assertTrue(a.inbox.pending().isEmpty())
    }

    @Test
    fun foldersAndTheNotesInThemReachTheOtherDevice() {
        a.folders = listOf(com.gerko.fourenote.data.Folder("f1", "School", null, 100))
        a.save(Note(id = "n1", title = "Maths", folderId = "f1"))
        a.sync()
        b.sync()
        assertEquals("School", b.folders.single().name)
        assertEquals("f1", b.notes.getValue("n1").folderId)

        // Renamed on B, deleted later on A: the later change wins on both.
        b.folders = listOf(b.folders.single().copy(name = "Uni", updatedAt = 200))
        b.sync()
        a.sync()
        assertEquals("Uni", a.folders.single().name)
        a.folders = listOf(a.folders.single().copy(deleted = true, updatedAt = 300))
        a.sync()
        b.sync()
        assertTrue(b.folders.single().deleted)
    }

    private val password = "Blue-Kettle-42!".toCharArray()

    private fun cloudText(name: String) = NoteFile.fromCloud(java.io.File(cloudDir, name).readBytes())

    @Test
    fun lockedNoteIsEncryptedInTheCloudAndOpensOnADeviceWithThePassword() {
        a.keys.passwordSet(password)
        a.save(Note(id = "n1", title = "Diary", inkData = ink("secret"), locked = true))
        a.addPicture("n1", "photo.jpg", "PHOTO")
        a.sync()
        assertTrue(cloudFiles().contains("lock.json"))
        assertTrue(cloudFiles().contains("n1~photo.jpg.4el"))
        assertTrue(cloudFiles().none { it == "n1~photo.jpg" || it.endsWith(".pdf") })
        assertTrue(!cloudText("n1.4enote").contains("Diary") && !cloudText("n1.4enote").contains("secret"))
        assertTrue(!cloudText("n1~photo.jpg.4el").contains("PHOTO"))

        // Without the password, B leaves it alone.
        val result = b.sync()
        assertEquals(1, result.needPassword)
        assertNull(b.notes["n1"])
        assertEquals(com.gerko.fourenote.data.security.NotesKeyring.State.NEEDS_PASSWORD, b.keys.state.value)
        assertTrue(cloudFiles().contains("n1.4enote"))

        assertTrue(b.keys.unlockWith(password))
        assertEquals(0, b.sync().needPassword)
        assertEquals("Diary", b.notes.getValue("n1").title)
        assertEquals(setOf("secret"), b.strokeIds("n1"))
        assertEquals("PHOTO", b.files.getValue("n1").getValue("photo.jpg").readText())

        // Edits on both sides still merge through the encryption.
        a.addStrokes("n1", "fromA")
        b.addStrokes("n1", "fromB")
        a.sync()
        assertEquals(1, b.sync().merged)
        a.sync()
        assertEquals(setOf("secret", "fromA", "fromB"), a.strokeIds("n1"))
        assertTrue(!cloudText("n1.4enote").contains("fromA"))
    }

    @Test
    fun noteLockedBeforeThePasswordIsEncryptedOnceItIsSet() {
        a.save(Note(id = "n1", title = "Diary", inkData = ink("s1"), locked = true))
        a.addPicture("n1", "photo.jpg", "PHOTO")
        a.sync()
        assertTrue(cloudText("n1.4enote").contains("Diary"))
        a.keys.passwordSet(password)
        a.sync()
        assertTrue(!cloudText("n1.4enote").contains("Diary"))
        assertTrue(cloudFiles().contains("n1~photo.jpg.4el") && !cloudFiles().contains("n1~photo.jpg"))
        // Nothing more to do on the next sync.
        assertEquals(0, a.sync().uploaded)
    }

    @Test
    fun unlockingANoteStoresItReadableAgain() {
        a.keys.passwordSet(password)
        a.save(Note(id = "n1", title = "Diary", inkData = ink("s1"), locked = true))
        a.addPicture("n1", "photo.jpg", "PHOTO")
        a.sync()
        a.save(a.notes.getValue("n1").copy(locked = false))
        a.sync()
        assertTrue(cloudText("n1.4enote").contains("Diary"))
        assertTrue(cloudFiles().contains("n1~photo.jpg") && !cloudFiles().contains("n1~photo.jpg.4el"))
    }

    @Test
    fun deviceWithoutThePasswordDoesNotOverwriteOrDeleteEncryptedPictures() {
        sharedNote()
        b.addPicture("n1", "photo.jpg", "PHOTO")
        b.sync()
        a.sync()
        a.keys.passwordSet(password)
        a.save(a.notes.getValue("n1").copy(locked = true))
        a.sync()
        // B still has the note unlocked and plain, but no key: the encrypted cloud copy stays as it is.
        b.addStrokes("n1", "fromB")
        b.sync()
        assertTrue(cloudFiles().contains("n1~photo.jpg.4el") && !cloudFiles().contains("n1~photo.jpg"))
        assertTrue(!cloudText("n1.4enote").contains("Biology"))
        assertTrue(b.notes.getValue("n1").dirty)
    }

    @Test
    fun encryptedNoteCopiedOverAnotherNotesNameIsRefused() {
        a.keys.passwordSet(password)
        a.save(Note(id = "n1", title = "Diary", inkData = ink("s1"), locked = true))
        a.sync()
        b.keys.reconcile(cloudText("lock.json"))
        b.keys.unlockWith(password)
        java.io.File(cloudDir, "n1.4enote").copyTo(java.io.File(cloudDir, "n2.4enote"))
        val result = b.sync()
        assertNull(b.notes["n2"])
        assertEquals(1, result.errors.size)
    }
}
