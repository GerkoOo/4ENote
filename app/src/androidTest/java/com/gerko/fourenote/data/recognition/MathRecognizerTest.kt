package com.gerko.fourenote.data.recognition

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gerko.fourenote.data.ink.InkStroke
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Needs the model in the app's files (downloaded in the app, or pushed with adb); skipped otherwise. */
@RunWith(AndroidJUnit4::class)
class MathRecognizerTest {
    private val recognizer = MathRecognizer(InstrumentationRegistry.getInstrumentation().targetContext)

    private fun stroke(vararg xy: Float) = InkStroke(color = 0, width = 3f, highlighter = false, points = xy)

    @Test
    fun readsHandDrawnFormula() = runBlocking {
        assumeTrue("maths model not on the device", recognizer.isDownloaded())
        // x² + y = 1, drawn as pen strokes in page units.
        val strokes = listOf(
            stroke(40f, 80f, 110f, 170f), stroke(110f, 80f, 40f, 170f),
            stroke(125f, 40f, 140f, 25f, 160f, 30f, 160f, 45f, 125f, 80f, 170f, 80f),
            stroke(210f, 125f, 270f, 125f), stroke(240f, 95f, 240f, 155f),
            stroke(310f, 80f, 340f, 130f), stroke(370f, 80f, 330f, 190f),
            stroke(410f, 110f, 470f, 110f), stroke(410f, 140f, 470f, 140f),
            stroke(510f, 95f, 530f, 80f, 530f, 170f),
        )
        val started = System.currentTimeMillis()
        val latex = recognizer.recognize(strokes)
        println("MathRecognizerTest: '$latex' in ${System.currentTimeMillis() - started} ms")
        recognizer.close()
        assertEquals("x^{2}+y=1", latex.replace(" ", "").lowercase())
    }
}
