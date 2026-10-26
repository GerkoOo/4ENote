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
    fun lockingAndUnlockingRewriteExistingPictures() = runBlocking {
        val picture = store.storeImage(noteId, png().inputStream(), keepTransparency = true)
        val file = store.file(noteId, picture.file)
        val original = file.readBytes()

        store.setSealed(noteId, true)
        assertTrue(NotesKey.isEncrypted(file.readBytes()))
        assertArrayEquals(original, store.readPlain(file))

        store.setSealed(noteId, false)
        assertArrayEquals(original, file.readBytes())
        assertFalse(store.isSealed(noteId))
    }

    /** A real PDF of several pages, each with a noisy picture, so it spans many 64 KB pieces. */
    private fun bigPdf(): ByteArray {
        val document = android.graphics.pdf.PdfDocument()
        val random = kotlin.random.Random(3)
        repeat(3) { index ->
            val page = document.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(300, 400, index + 1).create())
            val noise = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
            for (x in 0 until 200) for (y in 0 until 200) noise.setPixel(x, y, random.nextInt() or 0xFF000000.toInt())
            page.canvas.drawBitmap(noise, 50f, 50f + index * 20, null)
            document.finishPage(page)
        }
        return ByteArrayOutputStream().also { document.writeTo(it); document.close() }.toByteArray()
    }

    private fun render(fd: android.os.ParcelFileDescriptor, index: Int): IntArray = fd.use {
        android.graphics.pdf.PdfRenderer(it).use { renderer ->
            renderer.openPage(index).use { page ->
                val bitmap = Bitmap.createBitmap(150, 200, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                IntArray(150 * 200).also { pixels -> bitmap.getPixels(pixels, 0, 150, 0, 0, 150, 200) }
            }
        }
    }

    @Test
    fun aLockedNotesPdfIsEncryptedInPiecesAndStillRenders() = runBlocking {
        val pdf = bigPdf()
        assertTrue("needs several pieces: ${pdf.size}", pdf.size > 3 * SealedPdf.CHUNK)
        val pages = store.importPdf(noteId, pdf.inputStream())
        val name = (pages[0].background as com.gerko.fourenote.data.ink.PageBackground.PdfPage).file
        val file = store.file(noteId, name)
        val plainPage2 = render(store.openPdf(file), 2)

        store.setSealed(noteId, true)
        val sealedBytes = file.readBytes()
        assertTrue(SealedPdf.isSealed(sealedBytes))
        assertFalse("no plain PDF text left", String(sealedBytes, Charsets.ISO_8859_1).contains("%PDF"))
        assertArrayEquals(plainPage2, render(store.openPdf(file), 2))
        assertArrayEquals(pdf, store.openPlain(file).use { it.readBytes() })
        assertEquals(pdf.size.toLong(), store.plainLength(file))

        store.setSealed(noteId, false)
        assertArrayEquals(pdf, file.readBytes())
    }

    @Test
    fun aPdfImportedIntoALockedNoteIsSealedRightAway() = runBlocking {
        store.setSealed(noteId, true)
        val pages = store.importPdf(noteId, bigPdf().inputStream())
        assertEquals(3, pages.size)
        val name = (pages[0].background as com.gerko.fourenote.data.ink.PageBackground.PdfPage).file
        assertTrue(SealedPdf.isSealed(store.file(noteId, name).readBytes()))
        // Nothing plain left behind under another name.
        assertEquals(listOf(name), store.dir(noteId).list()!!.filter { !it.startsWith(".sealed") })
    }

    @Test
    fun aTamperedPieceOfASealedPdfDoesNotRead() = runBlocking {
        val pages = store.importPdf(noteId, bigPdf().inputStream())
        val name = (pages[0].background as com.gerko.fourenote.data.ink.PageBackground.PdfPage).file
        val file = store.file(noteId, name)
        store.setSealed(noteId, true)
        val bytes = file.readBytes()
        bytes[SealedPdf.CHUNK + 100] = (bytes[SealedPdf.CHUNK + 100].toInt() xor 1).toByte()
        file.writeBytes(bytes)
        try {
            store.openPlain(file).use { it.readBytes() }
            org.junit.Assert.fail("A changed byte must not read as a PDF")
        } catch (expected: Exception) {
        }
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
