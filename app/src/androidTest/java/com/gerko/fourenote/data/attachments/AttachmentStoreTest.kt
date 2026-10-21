package com.gerko.fourenote.data.attachments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gerko.fourenote.data.ink.PageBackground
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class AttachmentStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = AttachmentStore(context)
    private val noteId = "attachment-test"

    @After
    fun cleanUp() = store.deleteAll(noteId)

    private fun imagePage(name: String) = store.file(noteId, name)

    @Test
    fun sidewaysCameraPhotoIsTurnedUpright() = runBlocking {
        // A landscape 400x200 picture whose EXIF says "rotate 90 degrees", as phone cameras store portrait shots.
        val photo = File(context.cacheDir, "sideways.jpg")
        photo.outputStream().use { Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(photo.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }

        val page = store.importImage(noteId, photo.inputStream())

        // Upright it is portrait: the page is taller than wide, and so is the stored image.
        assertTrue("page ${page.width}x${page.height} should be portrait", page.height > page.width)
        val stored = BitmapFactory.decodeFile(imagePage((page.background as PageBackground.Image).file).path)
        assertEquals(200, stored.width)
        assertEquals(400, stored.height)
    }

    @Test
    fun transparentPngGetsWhitePaper() = runBlocking {
        val png = ByteArrayOutputStream().also {
            Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.TRANSPARENT) }
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()

        val page = store.importImage(noteId, png.inputStream())

        val stored = BitmapFactory.decodeFile(imagePage((page.background as PageBackground.Image).file).path)
        val pixel = stored.getPixel(50, 50)
        assertTrue("expected white, got ${Integer.toHexString(pixel)}", Color.red(pixel) > 245 && Color.green(pixel) > 245 && Color.blue(pixel) > 245)
    }

    @Test
    fun hugePhotoIsScaledDown() = runBlocking {
        val big = ByteArrayOutputStream().also {
            Bitmap.createBitmap(6000, 4000, Bitmap.Config.RGB_565).compress(Bitmap.CompressFormat.JPEG, 50, it)
        }.toByteArray()

        val page = store.importImage(noteId, big.inputStream())

        val stored = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imagePage((page.background as PageBackground.Image).file).path, stored)
        assertEquals(3000, maxOf(stored.outWidth, stored.outHeight))
    }
}
