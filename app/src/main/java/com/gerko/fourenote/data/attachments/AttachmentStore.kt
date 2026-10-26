package com.gerko.fourenote.data.attachments

import com.gerko.fourenote.data.SafeName
import com.gerko.fourenote.data.writeAtomically
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.gerko.fourenote.data.ink.FileSealing
import com.gerko.fourenote.data.ink.InkPage
import com.gerko.fourenote.data.ink.PageBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Files a note's pages are drawn on (scanned photos, imported PDFs), kept in
 * a private folder per note. Pages refer to them by file name.
 *
 * A locked note's folder holds a `.sealed` marker, and then its files are encrypted with
 * [sealing] (the device key), each bound to its name: pictures whole, PDFs in pieces (see
 * [SealedPdf]) so they can still be rendered page by page. [openPlain] and [openPdf] read them.
 */
class AttachmentStore(val context: Context, private val sealing: FileSealing? = null) {
    val root = File(context.filesDir, "attachments")

    private fun marker(noteId: String) = File(dir(noteId), SEALED_MARKER)

    /** Whether this note's files are kept encrypted (see [setSealed]). */
    fun isSealed(noteId: String) = marker(noteId).isFile

    /** A file's plain content, whether it is kept encrypted or not. */
    fun readPlain(file: File): ByteArray = openPlain(file).use { it.readBytes() }

    /** A file's plain content as a stream: a plain file or a sealed PDF (even a huge one) is read as it goes. */
    fun openPlain(file: File): InputStream {
        val sealing = sealing ?: return file.inputStream().buffered()
        val head = head(file)
        return when {
            SealedPdf.isSealed(head) -> SealedPdf.inputStream(file, sealing, nameOf(file)).buffered()
            sealing.isSealed(head) -> sealing.open(file.readBytes(), nameOf(file)).inputStream()
            else -> file.inputStream().buffered()
        }
    }

    /** How many plain bytes [openPlain] gives (for uploads, which need the size first). */
    fun plainLength(file: File): Long {
        val sealing = sealing ?: return file.length()
        val head = head(file)
        return when {
            SealedPdf.isSealed(head) -> SealedPdf.plainLength(file, sealing, nameOf(file))
            sealing.isSealed(head) -> readPlain(file).size.toLong()
            else -> file.length()
        }
    }

    /** Whether this file is kept encrypted (in either form). */
    fun isSealedFile(file: File): Boolean {
        val head = head(file)
        return SealedPdf.isSealed(head) || sealing?.isSealed(head) == true
    }

    /** A read-only file descriptor of a PDF for Android's renderer, decrypting a sealed one as it is read. */
    fun openPdf(file: File): ParcelFileDescriptor {
        val sealing = sealing
        if (sealing == null || !SealedPdf.isSealed(head(file))) return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return SealedPdf.open(context, file, sealing, nameOf(file))
    }

    private fun head(file: File): ByteArray = file.inputStream().use { input ->
        val head = ByteArray(HEAD_SIZE)
        var read = 0
        while (read < head.size) {
            val n = input.read(head, read, head.size - read)
            if (n < 0) break
            read += n
        }
        head.copyOf(read)
    }

    /**
     * Makes a note's pictures match its lock: encrypted with the device key when [sealed], plain
     * otherwise. It can stop at any point (reads take both forms) and simply runs again.
     */
    suspend fun setSealed(noteId: String, sealed: Boolean) = withContext(Dispatchers.IO) {
        val dir = dir(noteId)
        val marker = marker(noteId)
        // Unsealing drops the marker only once every picture is plain again.
        if (!sealed && !marker.isFile) return@withContext
        val sealing = sealing ?: error("No device key to seal pictures with")
        if (sealed && !marker.isFile) {
            dir.mkdirs()
            // The marker first: from here on every picture stored in this note is sealed.
            writeAtomically(marker) {}
        }
        for (file in dir.listFiles().orEmpty()) {
            if (!file.isFile || file.name == SEALED_MARKER) continue
            // Leftovers of an import or a save cut short may hold a plain picture.
            if (file.name.startsWith(".")) {
                if (sealed) file.delete()
                continue
            }
            val head = head(file)
            val isSealed = SealedPdf.isSealed(head) || sealing.isSealed(head)
            if (isSealed == sealed) continue
            if (sealed) {
                sealInto(file, file.inputStream().buffered(), file.length())
            } else {
                // Through a temporary file: the plain copy is written while the sealed one is read.
                val plain = File(dir, ".${file.name}.plain")
                openPlain(file).use { input -> writeAtomically(plain) { input.copyTo(it) } }
                java.nio.file.Files.move(plain.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            }
        }
        if (!sealed) marker.delete()
    }

    private fun isPdf(name: String) = name.endsWith(".pdf", ignoreCase = true)

    // The name a sealed file is bound to: its own (unique) file name, so a copied note still opens it.
    private fun nameOf(file: File) = "attachments/" + file.name

    /** Seals [length] plain bytes from [input] into [target]: PDFs in pieces, pictures whole. */
    private fun sealInto(target: File, input: InputStream, length: Long) {
        val sealing = sealing ?: error("No device key to seal files with")
        input.use { source ->
            if (isPdf(target.name)) {
                // Into a temporary file first: [target] may be the plain file being read.
                val sealed = File(target.parentFile, ".${target.name}.sealing")
                writeAtomically(sealed) { SealedPdf.seal(source, length, it, sealing, nameOf(target)) }
                java.nio.file.Files.move(sealed.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            } else {
                val plain = source.readBytes()
                writeAtomically(target) { it.write(sealing.seal(plain, nameOf(target))) }
            }
        }
    }

    /** Writes a note's file, encrypted when the note is sealed. */
    private fun writeNoteFile(noteId: String, target: File, write: (OutputStream) -> Unit) {
        if (!isSealed(noteId)) return writeAtomically(target, write)
        val plain = ByteArrayOutputStream().also(write).toByteArray()
        sealInto(target, plain.inputStream(), plain.size.toLong())
    }

    fun dir(noteId: String) = File(root, SafeName.require(noteId, "note id"))

    // An unsafe name (from a damaged or hostile note) points at a file that never exists.
    fun file(noteId: String, name: String) = File(dir(noteId), if (SafeName.isSafe(name)) name else ".unsafe-name")

    /** Copies a PDF into the note and returns one page per PDF page, shaped like it. */
    suspend fun importPdf(noteId: String, source: InputStream): List<InkPage> = withContext(Dispatchers.IO) {
        val name = "pdf-${UUID.randomUUID()}.pdf"
        // A locked note's PDF is read in plain under a hidden name, then sealed into place.
        val sealed = isSealed(noteId)
        val plain = copy(noteId, if (sealed) ".import-$name" else name, source)
        try {
            val pages = ParcelFileDescriptor.open(plain, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    List(renderer.pageCount) { index ->
                        renderer.openPage(index).use { page ->
                            InkPage.sizedFor(page.width, page.height, PageBackground.PdfPage(name, index))
                        }
                    }
                }
            }
            if (sealed) sealInto(file(noteId, name), plain.inputStream().buffered(), plain.length())
            pages
        } catch (e: Exception) {
            plain.delete()
            throw IllegalArgumentException("This PDF can't be opened (it may be damaged or password protected)", e)
        } finally {
            if (sealed) plain.delete()
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
            writeNoteFile(noteId, file(noteId, name)) {
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
        // Starts with a dot, so sync and backups (which skip such files) never take it for a picture.
        const val SEALED_MARKER = ".sealed"
        // Enough to tell a sealed file by its first bytes.
        const val HEAD_SIZE = 4
    }

    /** Copies one note's pictures and PDF pages to another note, for duplicating notes. */
    suspend fun copyAll(fromNoteId: String, toNoteId: String) = withContext(Dispatchers.IO) {
        val from = dir(fromNoteId)
        if (from.isDirectory) from.copyRecursively(dir(toNoteId), overwrite = true)
    }

    /** Stores a file that came from the cloud, under its own name, written whole or not at all. */
    suspend fun saveDownloaded(noteId: String, name: String, content: ByteArray) = withContext(Dispatchers.IO) {
        require(SafeName.isSafeFromCloud(name)) { "Unsafe attachment name" }
        val target = file(noteId, name)
        target.parentFile?.mkdirs()
        writeNoteFile(noteId, target) { it.write(content) }
    }

    fun deleteAll(noteId: String) {
        dir(noteId).deleteRecursively()
    }

    private fun copy(noteId: String, name: String, source: InputStream): File {
        val target = file(noteId, name)
        target.parentFile?.mkdirs()
        source.use { input -> writeAtomically(target) { input.copyTo(it) } }
        return target
    }
}
