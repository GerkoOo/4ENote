package com.gerko.fourenote.data.attachments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.gerko.fourenote.data.ink.PageBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Turns page backgrounds into bitmaps at the width they are shown, caching
 * recent ones. PDF pages are rendered on demand, so a 300-page PDF costs
 * memory only for the pages on screen.
 */
class BackgroundLoader(private val attachments: AttachmentStore) {
    // PdfRenderer allows one open page at a time per document; serialize all rendering.
    private val renderLock = Mutex()

    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Returns the background scaled to about [widthPx] wide, or null if its file is missing. */
    suspend fun load(noteId: String, background: PageBackground, widthPx: Int): Bitmap? {
        val width = widthPx.coerceIn(64, MAX_WIDTH_PX)
        val key = "$noteId/${background.file}#${(background as? PageBackground.PdfPage)?.pageIndex}@$width"
        cache.get(key)?.let { return it }
        val file = attachments.file(noteId, background.file)
        if (!file.exists()) return null
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                when (background) {
                    is PageBackground.Image -> decodeImage(attachments.readPlain(file), width)
                    is PageBackground.PdfPage -> renderLock.withLock { renderPdfPage(file, background.pageIndex, width) }
                }
            }.getOrNull()
        } ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    // From bytes, not the file: a locked note's pictures are encrypted on the device.
    private fun decodeImage(bytes: ByteArray, width: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun renderPdfPage(file: java.io.File, index: Int, width: Int): Bitmap =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                renderer.openPage(index).use { page ->
                    val height = (width.toLong() * page.height / page.width).toInt().coerceAtLeast(1)
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                        it.eraseColor(Color.WHITE)
                        page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }

    private companion object {
        const val MAX_WIDTH_PX = 2000
    }
}
