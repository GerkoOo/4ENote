package com.gerko.fourenote.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.gerko.fourenote.data.attachments.AttachmentStore
import com.gerko.fourenote.data.ink.BackgroundPlacement
import com.gerko.fourenote.data.ink.Calligraphy
import com.gerko.fourenote.data.ink.InkDocument
import com.gerko.fourenote.data.ink.InkPage
import com.gerko.fourenote.data.ink.InkStroke
import com.gerko.fourenote.data.ink.PageBackground
import com.gerko.fourenote.data.ink.PageText
import com.gerko.fourenote.ui.editor.renderTextBox
import com.gerko.fourenote.data.ink.PageImage
import com.gerko.fourenote.data.ink.PaperStyle
import com.gerko.fourenote.data.ink.outline
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.blend.BlendMode
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import java.io.File

/**
 * Exports a note as a PDF the way dedicated note apps do: pages that came
 * from an imported PDF are copied over unchanged (vector, sharp and small),
 * scans are embedded as they are stored, and ink is drawn on top as vector
 * paths. PdfBox keeps its working data in temporary files and the result is
 * written straight to [target], so even a 500-page note needs little memory.
 */
object PdfExporter {
    private const val TAG = "PdfExporter"

    /** Page units are 96 per inch (an A4 page is 794 wide); PDF points are 72 per inch. */
    private const val POINTS_PER_UNIT = 0.75f
    private const val TEXT_PX_PER_UNIT = 4f

    /** Pictures are scaled down to at most this many pixels on their long side. */
    private const val MAX_IMAGE_SIDE = 2000

    /** Bézier constant for drawing a circle with four curves. */
    private const val CIRCLE_K = 0.5523f

    private val RULE = floatArrayOf(0xB9 / 255f, 0xD3 / 255f, 0xEA / 255f)
    private val MARGIN = floatArrayOf(0xF0 / 255f, 0xA8 / 255f, 0xA8 / 255f)

    fun export(noteId: String, doc: InkDocument, attachments: AttachmentStore, target: File) {
        PDFBoxResourceLoader.init(attachments.context)
        val sources = HashMap<String, PDDocument>()
        try {
            PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { out ->
                for (page in doc.pages) {
                    exportPage(out, page, doc, noteId, attachments, sources)
                }
                target.outputStream().buffered().use { out.save(it) }
            }
        } finally {
            // Copied pages share data with their source PDFs, so those stay open until saved.
            sources.values.forEach { runCatching { it.close() } }
        }
        Log.i(TAG, "note ${noteId.take(8)}: ${doc.pages.size} pages, ${target.length() / 1024} KB")
    }

    private fun exportPage(
        out: PDDocument,
        page: InkPage,
        doc: InkDocument,
        noteId: String,
        attachments: AttachmentStore,
        sources: MutableMap<String, PDDocument>,
    ) {
        val pdfBackground = page.background as? PageBackground.PdfPage
        val sourcePage = pdfBackground?.let { bg ->
            val file = attachments.file(noteId, bg.file)
            if (!file.exists()) return@let null
            val source = sources.getOrPut(bg.file) { PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()) }
            source.takeIf { bg.pageIndex < it.numberOfPages }?.getPage(bg.pageIndex)
        }
        // Rotated source pages are drawn as an image instead, to keep their orientation simple.
        val copied = sourcePage?.takeIf { it.rotation % 360 == 0 }?.let { out.importPage(it) }

        val pdfPage: PDPage
        val box: PDRectangle
        if (copied != null) {
            pdfPage = copied
            box = copied.cropBox
            if (page.placement != BackgroundPlacement()) moveCopiedContent(out, copied, page, box)
        } else {
            box = PDRectangle(page.width * POINTS_PER_UNIT, page.height * POINTS_PER_UNIT)
            pdfPage = PDPage(box)
            out.addPage(pdfPage)
        }

        // resetContext wraps the copied page's own drawing so it can't affect ours.
        PDPageContentStream(out, pdfPage, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            // From here on draw in page units: origin top-left, y pointing down, like the app.
            cs.transform(
                Matrix(box.width / page.width, 0f, 0f, -box.height / page.height, box.lowerLeftX, box.upperRightY),
            )
            if (copied == null) drawBackground(out, cs, page, doc.paper, noteId, attachments)
            for (image in page.images) if (!image.waitingForPhoto) drawPicture(out, cs, attachments, attachments.file(noteId, image.file), image)
            for (stroke in doc.visibleStrokes(page)) drawStroke(cs, stroke)
            for (text in page.texts) if (text.text.isNotBlank()) drawText(out, cs, text)
        }
    }

    /** Moves/scales a copied PDF page's content to where the user placed it on the page. */
    private fun moveCopiedContent(out: PDDocument, pdfPage: PDPage, page: InkPage, box: PDRectangle) {
        val p = page.placement
        val sx = box.width / page.width
        val sy = box.height / page.height
        // In PDF space (y up): x' = s*x + tx, y' = s*y + ty, keeping the top-left corner where the user put it.
        val tx = box.lowerLeftX + p.x * sx - p.scale * box.lowerLeftX
        val ty = (box.upperRightY - p.y * sy) - p.scale * box.upperRightY
        PDPageContentStream(out, pdfPage, PDPageContentStream.AppendMode.PREPEND, true).use {
            it.saveGraphicsState()
            it.transform(Matrix(p.scale, 0f, 0f, p.scale, tx, ty))
        }
        PDPageContentStream(out, pdfPage, PDPageContentStream.AppendMode.APPEND, true).use { it.restoreGraphicsState() }
    }

    private fun drawBackground(
        out: PDDocument,
        cs: PDPageContentStream,
        page: InkPage,
        paper: PaperStyle,
        noteId: String,
        attachments: AttachmentStore,
    ) {
        val background = page.background
        if (background == null) {
            drawRules(cs, paper, page.width, page.height)
            return
        }
        val file = attachments.file(noteId, background.file)
        val image = runCatching {
            when (background) {
                is PageBackground.Image -> imageFor(out, attachments, file)
                // Only rotated PDF pages get here: draw them as an image at print resolution.
                is PageBackground.PdfPage -> renderPdfPage(file, background.pageIndex, (page.width * 2).toInt())
                    ?.let { bitmap -> JPEGFactory.createFromImage(out, bitmap, 0.85f).also { bitmap.recycle() } }
            }
        }.getOrNull() ?: return
        val p = page.placement
        cs.saveGraphicsState()
        cs.addRect(0f, 0f, page.width, page.height)
        cs.clip()
        drawImageInRect(cs, image, p.x, p.y, page.width * p.scale, page.height * p.scale)
        cs.restoreGraphicsState()
    }

    /**
     * JPEGs are embedded as stored (no decoding); other formats are decoded and compressed
     * losslessly. Read through [attachments]: a locked note's pictures are encrypted on the device.
     */
    private fun imageFor(out: PDDocument, attachments: AttachmentStore, file: File): PDImageXObject? {
        if (!file.exists()) return null
        val bytes = attachments.readPlain(file)
        if (file.extension.equals("jpg", true) || file.extension.equals("jpeg", true)) {
            return JPEGFactory.createFromByteArray(out, bytes)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_SIDE) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        return LosslessFactory.createFromImage(out, bitmap).also { bitmap.recycle() }
    }

    /** A picture as shown on the page: only its cropped part, with its colours inverted if set. */
    private fun editedImageFor(out: PDDocument, attachments: AttachmentStore, file: File, image: PageImage): PDImageXObject? {
        if (!file.exists()) return null
        val bytes = attachments.readPlain(file)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_SIDE) sample *= 2
        val full = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val left = (image.cropLeft * full.width).toInt().coerceIn(0, full.width - 1)
        val top = (image.cropTop * full.height).toInt().coerceIn(0, full.height - 1)
        val width = ((image.cropRight - image.cropLeft) * full.width).toInt().coerceIn(1, full.width - left)
        val height = ((image.cropBottom - image.cropTop) * full.height).toInt().coerceIn(1, full.height - top)
        val shown = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val matrix = when {
            image.negative -> com.gerko.fourenote.ui.editor.NEGATIVE_MATRIX
            image.inverted -> com.gerko.fourenote.data.ink.SmartInvert.MATRIX
            else -> null
        }
        val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
            if (matrix != null) colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
        }
        android.graphics.Canvas(shown).drawBitmap(full, android.graphics.Rect(left, top, left + width, top + height), android.graphics.Rect(0, 0, width, height), paint)
        full.recycle()
        return LosslessFactory.createFromImage(out, shown).also { shown.recycle() }
    }

    private fun renderPdfPage(file: File, index: Int, width: Int): Bitmap? =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                if (index >= renderer.pageCount) return null
                renderer.openPage(index).use { page ->
                    val height = (width.toLong() * page.height / page.width).toInt().coerceAtLeast(1)
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                        it.eraseColor(Color.WHITE)
                        page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    }
                }
            }
        }

    /** Draws [image] upright into a rectangle given in page units (y down). */
    // Text boxes, typeset maths included, are drawn as sharp transparent pictures (4 pixels per page unit).
    private fun drawText(out: PDDocument, cs: PDPageContentStream, text: PageText) {
        val bitmap = renderTextBox(text, TEXT_PX_PER_UNIT)
        val image = LosslessFactory.createFromImage(out, bitmap)
        drawImageInRect(cs, image, text.x, text.y, bitmap.width / TEXT_PX_PER_UNIT, bitmap.height / TEXT_PX_PER_UNIT)
        bitmap.recycle()
    }

    private fun drawImageInRect(cs: PDPageContentStream, image: PDImageXObject, x: Float, y: Float, w: Float, h: Float) {
        // PDF draws images into the unit square with y up; flip it so the picture isn't upside down.
        cs.drawImage(image, Matrix(w, 0f, 0f, -h, x, y + h))
    }

    /** A picture placed on the page, rotated about its center and faded as on screen. */
    private fun drawPicture(out: PDDocument, cs: PDPageContentStream, attachments: AttachmentStore, file: File, image: PageImage) {
        val xObject = runCatching {
            if (image.cropped || image.inverted || image.negative) editedImageFor(out, attachments, file, image) else imageFor(out, attachments, file)
        }.getOrNull() ?: return
        cs.saveGraphicsState()
        if (image.opacity < 1f) {
            cs.setGraphicsStateParameters(PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = image.opacity })
        }
        cs.transform(Matrix.getTranslateInstance(image.x, image.y))
        // With y pointing down, a positive angle turns clockwise, as in the app.
        cs.transform(Matrix.getRotateInstance(Math.toRadians(image.rotation.toDouble()), 0f, 0f))
        drawImageInRect(cs, xObject, -image.width / 2, -image.height / 2, image.width, image.height)
        cs.restoreGraphicsState()
    }

    private fun drawStroke(cs: PDPageContentStream, stroke: InkStroke) {
        val r = Color.red(stroke.color) / 255f
        val g = Color.green(stroke.color) / 255f
        val b = Color.blue(stroke.color) / 255f
        cs.saveGraphicsState()
        when {
            stroke.nibAngle != null -> {
                cs.setNonStrokingColor(r, g, b)
                for (shape in Calligraphy.nibShapes(stroke)) polygon(cs, shape)
                cs.fill()
            }
            stroke.widths != null -> {
                cs.setNonStrokingColor(r, g, b)
                polygon(cs, stroke.outline())
                val factors = checkNotNull(stroke.widths)
                for (i in listOf(0, stroke.points.size / 2 - 1)) {
                    if (i < 0 || stroke.squareEnds) continue
                    circle(cs, stroke.points[2 * i], stroke.points[2 * i + 1], stroke.width * factors[i] / 2)
                }
                cs.fill()
            }
            else -> {
                if (stroke.highlighter) {
                    cs.setGraphicsStateParameters(
                        PDExtendedGraphicsState().apply {
                            strokingAlphaConstant = 0.35f
                            blendMode = BlendMode.MULTIPLY
                        },
                    )
                }
                cs.setStrokingColor(r, g, b)
                cs.setLineWidth(stroke.width)
                cs.setLineCapStyle(if (stroke.highlighter) 2 else 1)
                cs.setLineJoinStyle(1)
                centerline(cs, stroke.points)
                cs.stroke()
            }
        }
        cs.restoreGraphicsState()
    }

    /** Same midpoint smoothing as the screen; PDF has only cubic curves, so each quadratic is converted. */
    private fun centerline(cs: PDPageContentStream, p: FloatArray) {
        val n = p.size / 2
        if (n == 0) return
        cs.moveTo(p[0], p[1])
        if (n == 1) {
            cs.lineTo(p[0] + 0.01f, p[1])
            return
        }
        var x0 = p[0]
        var y0 = p[1]
        for (i in 1 until n - 1) {
            val qx = p[2 * i]
            val qy = p[2 * i + 1]
            val mx = (qx + p[2 * i + 2]) / 2
            val my = (qy + p[2 * i + 3]) / 2
            cs.curveTo(
                x0 + 2f / 3f * (qx - x0), y0 + 2f / 3f * (qy - y0),
                mx + 2f / 3f * (qx - mx), my + 2f / 3f * (qy - my),
                mx, my,
            )
            x0 = mx
            y0 = my
        }
        cs.lineTo(p[2 * n - 2], p[2 * n - 1])
    }

    private fun polygon(cs: PDPageContentStream, xy: FloatArray) {
        if (xy.size < 6) return
        cs.moveTo(xy[0], xy[1])
        for (i in 1 until xy.size / 2) cs.lineTo(xy[2 * i], xy[2 * i + 1])
        cs.closePath()
    }

    private fun circle(cs: PDPageContentStream, cx: Float, cy: Float, r: Float) {
        val k = CIRCLE_K * r
        cs.moveTo(cx + r, cy)
        cs.curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
        cs.curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
        cs.curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
        cs.curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
        cs.closePath()
    }

    private fun drawRules(cs: PDPageContentStream, paper: PaperStyle, width: Float, height: Float) {
        fun line(color: FloatArray, x1: Float, y1: Float, x2: Float, y2: Float) {
            cs.setStrokingColor(color[0], color[1], color[2])
            cs.moveTo(x1, y1)
            cs.lineTo(x2, y2)
            cs.stroke()
        }
        cs.saveGraphicsState()
        cs.setLineWidth(0.6f)
        when (paper) {
            PaperStyle.BLANK -> Unit
            PaperStyle.LINED -> {
                var y = 64f
                while (y < height) {
                    line(RULE, 0f, y, width, y)
                    y += 32f
                }
                line(MARGIN, 72f, 0f, 72f, height)
            }
            PaperStyle.GRID -> {
                var x = 24f
                while (x < width) {
                    line(RULE, x, 0f, x, height)
                    x += 24f
                }
                var y = 24f
                while (y < height) {
                    line(RULE, 0f, y, width, y)
                    y += 24f
                }
            }
            PaperStyle.DOTS -> {
                // Each dot is a zero-length line with a round cap: one small path for the whole page.
                cs.setStrokingColor(RULE[0], RULE[1], RULE[2])
                cs.setLineWidth(1.6f)
                cs.setLineCapStyle(1)
                var y = 24f
                while (y < height) {
                    var x = 24f
                    while (x < width) {
                        cs.moveTo(x, y)
                        cs.lineTo(x, y)
                        x += 24f
                    }
                    y += 24f
                }
                cs.stroke()
            }
        }
        cs.restoreGraphicsState()
    }
}
