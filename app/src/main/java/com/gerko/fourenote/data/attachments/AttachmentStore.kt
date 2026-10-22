package com.gerko.fourenote.data.attachments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.gerko.fourenote.data.ink.InkPage
import com.gerko.fourenote.data.ink.PageBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID

/**
 * Files a note's pages are drawn on (scanned photos, imported PDFs), kept in
 * a private folder per note. Pages refer to them by file name.
 */
class AttachmentStore(val context: Context) {
    private val root = File(context.filesDir, "attachments")

    fun dir(noteId: String) = File(root, noteId)

    fun file(noteId: String, name: String) = File(dir(noteId), name)

    /** Copies a PDF into the note and returns one page per PDF page, shaped like it. */
    suspend fun importPdf(noteId: String, source: InputStream): List<InkPage> = withContext(Dispatchers.IO) {
        val name = "pdf-${UUID.randomUUID()}.pdf"
        val target = copy(noteId, name, source)
        try {
            ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    List(renderer.pageCount) { index ->
                        renderer.openPage(index).use { page ->
                            InkPage.sizedFor(page.width, page.height, PageBackground.PdfPage(name, index))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            target.delete()
            throw IllegalArgumentException("This PDF can't be opened (it may be damaged or password protected)", e)
        }
    }

    /**
     * Stores a scanned or picked image (JPEG, PNG, WebP, HEIC, GIF, BMP...) in
     * the note and returns a page shaped like it. Images are turned upright
     * using their EXIF orientation, scaled to at most [MAX_IMAGE_SIDE] pixels,
     * and saved as JPEG on white, so every page background has one format.
     */
    suspend fun importImage(noteId: String, source: InputStream): InkPage {
        val image = storeImage(noteId, source)
        return InkPage.sizedFor(image.widthPx, image.heightPx, PageBackground.Image(image.file))
    }

    /** An image saved into a note's attachments, with its stored pixel size. */
    data class StoredImage(val file: String, val widthPx: Int, val heightPx: Int)

    /**
     * Saves an image as in [importImage]. With [keepTransparency] (pictures
     * placed on a page) see-through images stay see-through and are saved as
     * PNG; otherwise they are put on white and saved as JPEG.
     */
    suspend fun storeImage(noteId: String, source: InputStream, keepTransparency: Boolean = false): StoredImage =
        withContext(Dispatchers.IO) {
        val original = copy(noteId, ".import-${UUID.randomUUID()}", source)
        try {
            val bitmap = decodeUpright(original, onWhite = !keepTransparency)
                ?: throw IllegalArgumentException("This image can't be read")
            val png = keepTransparency && bitmap.hasAlpha()
            val name = "image-${UUID.randomUUID()}.${if (png) "png" else "jpg"}"
            file(noteId, name).outputStream().use {
                bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)
            }
            StoredImage(name, bitmap.width, bitmap.height).also { bitmap.recycle() }
        } finally {
            original.delete()
        }
    }

    /** Reads a photo the right way up (following its EXIF rotation), at most MAX_IMAGE_SIDE pixels on its long side. */
    fun decodeUpright(file: File, onWhite: Boolean = true): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_SIDE * 2) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null

        val matrix = Matrix()
        when (runCatching { ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
        }
        val longSide = maxOf(decoded.width, decoded.height)
        if (longSide > MAX_IMAGE_SIDE) matrix.postScale(MAX_IMAGE_SIDE.toFloat() / longSide, MAX_IMAGE_SIDE.toFloat() / longSide)
        val upright = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        if (!onWhite) {
            if (upright !== decoded) decoded.recycle()
            return upright
        }
        // JPEG has no transparency: put transparent images (PNG, WebP) on white paper.
        val opaque = Bitmap.createBitmap(upright.width, upright.height, Bitmap.Config.ARGB_8888)
        Canvas(opaque).apply {
            drawColor(Color.WHITE)
            drawBitmap(upright, 0f, 0f, null)
        }
        if (upright !== decoded) upright.recycle()
        decoded.recycle()
        return opaque
    }

    private companion object {
        const val MAX_IMAGE_SIDE = 3000
        const val JPEG_QUALITY = 90
    }

    /** Copies one note's pictures and PDF pages to another note, for duplicating notes. */
    suspend fun copyAll(fromNoteId: String, toNoteId: String) = withContext(Dispatchers.IO) {
        val from = dir(fromNoteId)
        if (from.isDirectory) from.copyRecursively(dir(toNoteId), overwrite = true)
    }

    /** Stores a file that came from the cloud, under its own name, written whole or not at all. */
    suspend fun saveDownloaded(noteId: String, name: String, content: ByteArray) = withContext(Dispatchers.IO) {
        val target = file(noteId, name)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, ".$name.part")
        temp.writeBytes(content)
        temp.renameTo(target)
    }

    fun deleteAll(noteId: String) {
        dir(noteId).deleteRecursively()
    }

    private fun copy(noteId: String, name: String, source: InputStream): File {
        val target = file(noteId, name)
        target.parentFile?.mkdirs()
        source.use { input -> target.outputStream().use { input.copyTo(it) } }
        return target
    }
}
