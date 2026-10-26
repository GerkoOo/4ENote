package com.gerko.fourenote.data.ink

import com.gerko.fourenote.data.security.DeviceKey
import com.gerko.fourenote.data.security.NotesKey
import com.gerko.fourenote.data.security.NotesKeyring
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Locked notes' ink kept encrypted on the device, with the real device key (the Keystore stood in for). */
class SealedInkStoreTest {
    private val root: File = Files.createTempDirectory("sealed").toFile()

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

    private val key = DeviceKey(Memory(), Unwrapped)
    private val store = InkStore(root, key)

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun stroke(id: String) =
        InkStroke(color = 1, width = 3f, highlighter = false, points = floatArrayOf(12.5f, 40f, 13f, 41f), id = id)

    private val secretTitle = "my secret diary entry"

    private fun doc() = InkDocument(
        listOf(
            InkPage(id = "p1", strokes = listOf(stroke("a"), stroke("b")), texts = listOf(PageText(x = 1f, y = 1f, width = 100f, text = secretTitle))),
            InkPage(id = "p2", strokes = listOf(stroke("c"))),
        ),
    )

    private fun files(noteId: String) = File(root, noteId).walkTopDown().filter { it.isFile && it.name != "sealed" }.toList()

    private fun assertAllSealed(noteId: String, sealed: Boolean) {
        val files = files(noteId)
        assertTrue(files.isNotEmpty())
        for (file in files) assertEquals(file.path, sealed, NotesKey.isEncrypted(file.readBytes()))
    }

    private fun strokeIds(doc: InkDocument?) = doc!!.pages.map { page -> page.strokes.map { it.id } }

    @Test
    fun aLockedNoteIsEncryptedOnDiskAndReadsBack() = runBlocking {
        store.setSealed("n", true)
        store.save("n", doc())
        store.writeSnapshot("n", "snapshot $secretTitle")

        assertAllSealed("n", true)
        for (file in files("n")) assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains(secretTitle))
        val loaded = InkStore(root, key).load("n")
        assertEquals(strokeIds(doc()), strokeIds(loaded))
        assertEquals(secretTitle, loaded!!.pages[0].texts.single().text)
        assertEquals("snapshot $secretTitle", InkStore(root, key).readSnapshot("n"))
    }

    @Test
    fun lockingAndUnlockingRewriteExistingFiles() = runBlocking {
        store.save("n", doc())
        store.writeSnapshot("n", "base")
        assertAllSealed("n", false)

        store.setSealed("n", true)
        assertAllSealed("n", true)
        assertTrue(store.isSealed("n"))

        store.setSealed("n", false)
        assertAllSealed("n", false)
        assertFalse(store.isSealed("n"))
        assertEquals(strokeIds(doc()), strokeIds(InkStore(root, key).load("n")))
        assertEquals("base", store.readSnapshot("n"))
    }

    @Test
    fun aLockStoppedHalfwayStillReadsAndIsFinishedLater() = runBlocking {
        store.save("n", doc())
        store.setSealed("n", true)
        // As if the app died while sealing: one page back in plain form, and a leftover temp file.
        val page = File(root, "n/pages/p2.ink")
        InkStore(root).let { plain ->
            File(root, "n/sealed").delete()
            plain.save("n", InkDocument(listOf(InkPage(id = "p2", strokes = listOf(stroke("c"))))))
        }
        File(root, "n/sealed").writeText("")
        File(root, "n/pages/p9.ink.tmp").writeText("plain leftovers")
        assertFalse(NotesKey.isEncrypted(page.readBytes()))

        assertEquals(listOf(listOf("c")), strokeIds(InkStore(root, key).load("n")).takeLast(1))
        store.setSealed("n", true)
        assertAllSealed("n", true)
        assertFalse(File(root, "n/pages/p9.ink.tmp").exists())
    }

    @Test
    fun aSealedFileSwappedForAnotherDoesNotOpen() = runBlocking {
        store.setSealed("n", true)
        store.save("n", doc())
        File(root, "n/pages/p1.ink").copyTo(File(root, "n/pages/p2.ink"), overwrite = true)
        try {
            InkStore(root, key).load("n")
            fail("A page file put in another's place must not open")
        } catch (expected: Exception) {
        }
    }

    @Test
    fun aCopiedLockedNoteStillOpens() = runBlocking {
        store.setSealed("n", true)
        store.save("n", doc())
        store.copy("n", "copy")
        assertTrue(store.isSealed("copy"))
        assertEquals(strokeIds(doc()), strokeIds(InkStore(root, key).load("copy")))
    }

    @Test
    fun backupsGetPlainBytes() = runBlocking {
        store.setSealed("n", true)
        store.save("n", doc())
        val manifest = store.plainBytes("n", File(root, "n/manifest.json")).decodeToString()
        assertTrue(manifest.contains(secretTitle))
    }

    @Test
    fun unlockingANoteThatWasNeverLockedTouchesNothing() = runBlocking {
        val plainStore = InkStore(root)
        plainStore.save("n", doc())
        plainStore.setSealed("n", false)
        assertAllSealed("n", false)
    }

    @Test
    fun aStoredDeviceKeyThatNoLongerOpensIsNeverReplaced() {
        val memory = Memory().apply { sealedKey = "AAAA" }
        val broken = object : NotesKeyring.Sealer {
            override fun seal(plain: ByteArray) = plain
            override fun open(sealed: ByteArray): ByteArray = throw IllegalStateException("Keystore key gone")
        }
        try {
            DeviceKey(memory, broken).key()
            fail("A key that doesn't open must be an error")
        } catch (expected: IllegalStateException) {
        }
        assertEquals("AAAA", memory.sealedKey)
    }

    @Test
    fun theDeviceKeyIsStoredBeforeItIsUsed() {
        val memory = Memory()
        val first = DeviceKey(memory, Unwrapped).key()
        assertNotNull(memory.sealedKey)
        assertEquals(first.id, DeviceKey(memory, Unwrapped).key().id)
    }
}
