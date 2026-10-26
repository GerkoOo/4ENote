package com.gerko.fourenote.stress

import com.gerko.fourenote.data.Folder
import com.gerko.fourenote.data.Note
import com.gerko.fourenote.data.backup.Backup
import com.gerko.fourenote.data.ink.InkDocument
import com.gerko.fourenote.data.ink.InkPage
import com.gerko.fourenote.data.ink.InkStore
import com.gerko.fourenote.data.ink.InkStroke
import com.gerko.fourenote.data.ink.PageImage
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Not a test: writes a 4ENote backup full of extreme notes, to import on a test device and see
 * how the app copes. Skipped unless STRESS_OUT names the file to write:
 *
 *     STRESS_OUT=/tmp/stress.4enote-backup ./gradlew testDebugUnitTest --tests '*StressBackupGenerator*'
 *
 * Everything goes into a folder "Stress test", so it is easy to delete afterwards.
 */
class StressBackupGenerator {
    private val random = Random(42)
    private val colors = listOf(0xFF000000.toInt(), 0xFF1565C0.toInt(), 0xFFC62828.toInt(), 0xFF2E7D32.toInt())

    @Test
    fun writeStressBackup() {
        val out = System.getenv("STRESS_OUT")
        assumeTrue("Set STRESS_OUT to generate the stress backup", !out.isNullOrBlank())
        val work = Files.createTempDirectory("stress").toFile()
        val inkRoot = File(work, "ink")
        val attachments = File(work, "attachments")
        val store = InkStore(inkRoot)
        val folder = Folder(id = "stress-folder", name = "Stress test", updatedAt = NOW)
        val notes = mutableListOf<Note>()

        fun note(id: String, title: String, doc: InkDocument) {
            runBlocking { store.save(id, doc) }
            // How big the note gets as text, as sync holds it in memory (a Java string takes twice this).
            if (doc.pages.sumOf { it.strokes.size } > 1000) {
                try {
                    println("$title: ink text ${com.gerko.fourenote.data.ink.InkSerializer.encode(doc).length / 1_000_000} million characters")
                } catch (e: OutOfMemoryError) {
                    println("$title: ink text too big to hold as one string, even here")
                }
            }
            notes += Note(id = id, title = title, createdAt = NOW, updatedAt = NOW, folderId = folder.id, dirty = false)
        }

        // Many pages: scrolling, the page strip, PDF export and sync of a long note.
        note("stress-200-pages", "Stress: 200 pages × 150 strokes", InkDocument(List(200) { InkPage(strokes = handwriting(150, 40)) }))

        // One page packed with ink: drawing speed, the lasso and the eraser on a crowded page.
        note("stress-50k-strokes", "Stress: 50,000 strokes on one page", InkDocument(listOf(InkPage(strokes = handwriting(50_000, 30)))))

        // Very long strokes: smoothing and hit testing with thousands of points each.
        note("stress-long-strokes", "Stress: 300 strokes × 3,000 points", InkDocument(listOf(InkPage(strokes = List(300) { spiral(3_000) }))))

        // Big photos: memory when pages with large pictures are opened and zoomed.
        val photoPages = List(20) { i ->
            val name = "photo-$i.jpg"
            File(attachments, "stress-photos").mkdirs()
            photo(File(attachments, "stress-photos/$name"), i)
            InkPage(
                images = listOf(PageImage(file = name, x = InkPage.PAGE_WIDTH / 2, y = InkPage.PAGE_HEIGHT / 2, width = 700f, height = 525f)),
                strokes = handwriting(30, 30),
            )
        }
        note("stress-photos", "Stress: 20 photos of 12 megapixels", InkDocument(photoPages))

        // A PDF written all over: PDF pages under heavy ink, as when annotating a long script.
        // STRESS_PDF names the PDF (tools/stress/make_big_pdf.py pages <file> 300); skipped without it.
        System.getenv("STRESS_PDF")?.takeIf { it.isNotBlank() }?.let { path ->
            val name = "script.pdf"
            File(attachments, "stress-pdf-ink").mkdirs()
            File(path).copyTo(File(attachments, "stress-pdf-ink/$name"))
            val pageCount = System.getenv("STRESS_PDF_PAGES")?.toInt() ?: 300
            val pages = List(pageCount) { i ->
                InkPage(
                    width = InkPage.PAGE_WIDTH,
                    height = InkPage.PAGE_WIDTH * 842f / 595f,
                    background = com.gerko.fourenote.data.ink.PageBackground.PdfPage(name, i),
                    strokes = handwriting(200, 30),
                )
            }
            note("stress-pdf-ink", "Stress: $pageCount-page PDF × 200 strokes a page", InkDocument(pages))
        }

        // A long home page: the list, thumbnails and search.
        repeat(300) { i ->
            note("stress-small-%03d".format(i), "Stress list %03d".format(i), InkDocument(listOf(InkPage(strokes = handwriting(20, 20)))))
        }

        val backup = Backup(
            inkRoot, attachments,
            notes = { notes },
            folders = { listOf(folder) },
            saveNote = {}, saveFolders = {}, forgetInk = {},
            clock = { NOW },
        )
        val target = File(out!!)
        target.outputStream().use { runBlocking { backup.export(it, "stress-generator") } }
        println("Wrote ${notes.size} notes to $target (${target.length() / (1024 * 1024)} MB)")
        work.deleteRecursively()
    }

    // Wobbly strokes in rows, like handwriting: [count] strokes of [points] points each.
    private fun handwriting(count: Int, points: Int): List<InkStroke> = List(count) { i ->
        val row = (i / 12) % 30
        var x = 40f + (i % 12) * 60f
        var y = 60f + row * 35f
        val xy = FloatArray(points * 2)
        for (p in 0 until points) {
            x += random.nextFloat() * 4f
            y += (random.nextFloat() - 0.5f) * 6f
            xy[p * 2] = x
            xy[p * 2 + 1] = y
        }
        InkStroke(color = colors[i % colors.size], width = 2.5f, highlighter = false, points = xy)
    }

    private fun spiral(points: Int): InkStroke {
        val cx = 100f + random.nextFloat() * 600f
        val cy = 100f + random.nextFloat() * 900f
        val xy = FloatArray(points * 2)
        for (p in 0 until points) {
            val a = p * 0.05
            val r = 2f + p * 0.03f
            xy[p * 2] = cx + (r * cos(a)).toFloat()
            xy[p * 2 + 1] = cy + (r * sin(a)).toFloat()
        }
        return InkStroke(color = colors[random.nextInt(colors.size)], width = 1.5f, highlighter = false, points = xy)
    }

    // A 4000 × 3000 picture with noise, so the JPEG can't be squeezed small.
    private fun photo(file: File, seed: Int) {
        val w = 4000
        val h = 3000
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val r = Random(seed)
        for (y in 0 until h) for (x in 0 until w) {
            val base = ((x * 255 / w) shl 16) or ((y * 255 / h) shl 8) or (seed * 12 and 0xFF)
            image.setRGB(x, y, base xor (r.nextInt(64) * 0x010101))
        }
        ImageIO.write(image, "jpg", file)
    }

    private companion object {
        const val NOW = 1_790_000_000_000L
    }
}
