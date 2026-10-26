package com.gerko.fourenote.data.attachments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/** A locked note's pictures kept encrypted on the device, with the real device key (the Keystore stood in for). */
@RunWith(AndroidJUnit4::class)
class SealedAttachmentStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

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

    private val store = AttachmentStore(context, DeviceKey(Memory(), Unwrapped))
    private val noteId = "sealed-attachment-test"

    @After
    fun cleanUp() = store.deleteAll(noteId)

    private fun png(): ByteArray = ByteArrayOutputStream().also {
        Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(0x80FF0000.toInt()) }
            .compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()

    @Test
    fun aPictureStoredInALockedNoteIsEncryptedOnDiskAndReadsBack() = runBlocking {
        store.setSealed(noteId, true)
        val stored = store.storeImage(noteId, png().inputStream(), keepTransparency = true)
        val file = store.file(noteId, stored.file)
        assertTrue(NotesKey.isEncrypted(file.readBytes()))
        assertEquals(null, BitmapFactory.decodeFile(file.path))
        val plain = store.readPlain(file)
        val bitmap = BitmapFactory.decodeByteArray(plain, 0, plain.size)
        assertEquals(64, bitmap.width)
    }

    @Test
    fun lockingAndUnlockingRewriteExistingPicturesButNotPdfs() = runBlocking {
        val picture = store.storeImage(noteId, png().inputStream(), keepTransparency = true)
        val file = store.file(noteId, picture.file)
        val original = file.readBytes()
        val pdf = store.file(noteId, "pdf-x.pdf").apply { writeBytes("%PDF-1.4 fake".encodeToByteArray()) }

        store.setSealed(noteId, true)
        assertTrue(NotesKey.isEncrypted(file.readBytes()))
        assertFalse("PDFs stay readable for now", NotesKey.isEncrypted(pdf.readBytes()))
        assertArrayEquals(original, store.readPlain(file))

        store.setSealed(noteId, false)
        assertArrayEquals(original, file.readBytes())
        assertFalse(store.isSealed(noteId))
    }

    @Test
    fun aDownloadedPictureForALockedNoteIsSealedToo() = runBlocking {
        store.setSealed(noteId, true)
        val content = png()
        store.saveDownloaded(noteId, "image-from-cloud.png", content)
        val file = store.file(noteId, "image-from-cloud.png")
        assertTrue(NotesKey.isEncrypted(file.readBytes()))
        assertArrayEquals(content, store.openPlain(file).use { it.readBytes() })
    }

    @Test
    fun leftoversOfAnImportAreRemovedWhenLocking() = runBlocking {
        store.dir(noteId).mkdirs()
        val leftover = store.file(noteId, ".import-123").apply { writeBytes(png()) }
        store.setSealed(noteId, true)
        assertFalse(leftover.exists())
    }
}
