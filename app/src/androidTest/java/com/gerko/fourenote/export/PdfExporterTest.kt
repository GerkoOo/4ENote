package com.gerko.fourenote.export

import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gerko.fourenote.data.Note
import com.gerko.fourenote.data.attachments.AttachmentStore
import com.gerko.fourenote.data.ink.Calligraphy
import com.gerko.fourenote.data.ink.InkDocument
import com.gerko.fourenote.data.ink.InkPage
import com.gerko.fourenote.data.ink.InkSerializer
import com.gerko.fourenote.data.ink.InkStroke
import com.gerko.fourenote.data.ink.PageImage
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfExporterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val attachments = AttachmentStore(context)
    private val noteId = "pdf-export-test"

    @Before
    fun setUp() = PDFBoxResourceLoader.init(context)

    @After
    fun cleanUp() = attachments.deleteAll(noteId)

    /** A lecture-like PDF whose pages carry real text ("Lecture page N"). */
    private fun lecturePdf(pages: Int): File {
        val file = File(context.cacheDir, "lecture-$pages.pdf")
        PDDocument().use { doc ->
            repeat(pages) { i ->
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA, 24f)
                    cs.newLineAtOffset(72f, 700f)
                    cs.showText("Lecture page ${i + 1}")
                    cs.endText()
                }
            }
            doc.save(file)
        }
        return file
    }

    private fun stroke(vararg xy: Float, widths: FloatArray? = null, nib: Float? = null, highlighter: Boolean = false) =
        InkStroke(0xFF1E5BD8.toInt(), 4f, highlighter, xy, widths, nibAngle = nib)

    private fun export(doc: InkDocument): File {
        val note = Note(id = noteId, title = "Test", inkData = InkSerializer.encode(doc))
        return File(context.cacheDir, "export-${System.nanoTime()}.pdf").also { PdfExporter.export(note, attachments, it) }
    }

    @Test
    fun pdfPagesAreCopiedAsRealPagesWithInkOnTop() = runBlocking {
        val pdfPages = attachments.importPdf(noteId, lecturePdf(3).inputStream())
        val picture = attachments.storeImage(
            noteId,
            File(context.cacheDir, "pic.png").apply {
                outputStream().use { android.graphics.Bitmap.createBitmap(64, 32, android.graphics.Bitmap.Config.ARGB_8888).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }.inputStream(),
            keepTransparency = true,
        )
        val annotated = pdfPages[1].copy(
            strokes = listOf(
                stroke(100f, 100f, 200f, 150f, 300f, 120f),
                stroke(100f, 300f, 200f, 320f, 300f, 310f, widths = floatArrayOf(0.5f, 1.2f, 0.6f)),
                stroke(100f, 500f, 100f, 600f, nib = Calligraphy.nibRadians(0f)),
                stroke(80f, 700f, 400f, 700f, highlighter = true),
            ),
            images = listOf(PageImage(file = picture.file, x = 400f, y = 400f, width = 200f, height = 100f, rotation = 30f, opacity = 0.5f)),
        )
        val doc = InkDocument(listOf(pdfPages[0], annotated, pdfPages[2], InkPage()))

        val out = export(doc)

        PDDocument.load(out).use { result ->
            assertEquals(4, result.numberOfPages)
            // The original text is still real text: the page was copied, not turned into a picture.
            val stripper = PDFTextStripper().apply { startPage = 2; endPage = 2 }
            assertTrue(stripper.getText(result).contains("Lecture page 2"))
            // A blank note page is A4 in PDF points.
            val blank = result.getPage(3).mediaBox
            assertEquals(595f, blank.width, 1.5f)
            assertEquals(842f, blank.height, 1.5f)
        }
        assertTrue("export is ${out.length()} bytes", out.length() < 200_000)
    }

    @Test
    fun longPdfNoteExportsWithoutUsingMuchMemory() = runBlocking {
        val pages = attachments.importPdf(noteId, lecturePdf(300).inputStream())
        assertEquals(300, pages.size)
        val doc = InkDocument(pages.mapIndexed { i, p -> if (i % 10 == 0) p.copy(strokes = listOf(stroke(50f, 50f, 300f, 300f))) else p })

        System.gc()
        val before = Debug.getNativeHeapAllocatedSize() + Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
        val started = System.currentTimeMillis()
        val out = export(doc)
        val seconds = (System.currentTimeMillis() - started) / 1000.0
        val after = Debug.getNativeHeapAllocatedSize() + Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
        val grownMb = (after - before) / 1_048_576.0

        android.util.Log.i("PdfExporterTest", "300 pages: ${out.length() / 1024} KB in $seconds s, memory grew $grownMb MB")
        PDDocument.load(out).use { assertEquals(300, it.numberOfPages) }
        assertTrue("memory grew by $grownMb MB", grownMb < 150)
    }
}
