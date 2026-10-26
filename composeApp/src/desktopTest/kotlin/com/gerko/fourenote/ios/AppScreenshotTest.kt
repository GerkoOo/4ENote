package com.gerko.fourenote.ios

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppScreenshotTest {
    @Test
    fun rendersAppToPng() {
        val scene = ImageComposeScene(width = 1366, height = 1024, density = Density(1f)) {
            App()
        }
        val image = scene.render()
        val data = image.encodeToData(EncodedImageFormat.PNG)
            ?: error("Skia could not encode the screenshot")
        val out = File("build/screenshots/app.png")
        out.parentFile?.mkdirs()
        out.writeBytes(data.bytes)
        scene.close()
        assertTrue("Screenshot is empty", out.length() > 0L)
    }

    @Test
    fun everySampleNoteHasStrokes() {
        for (note in SampleNotes.all) {
            val strokes = note.document.pages.sumOf { it.strokes.size }
            assertTrue("Note '${note.title}' has no strokes", strokes > 0)
        }
    }
}
