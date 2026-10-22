package com.gerko.fourenote.data.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Uses scan-test.jpg (a tilted sheet on a desk) pushed to the app's files; skipped without it. */
@RunWith(AndroidJUnit4::class)
class PageScannerTest {
    private val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!

    @Test
    fun findsStraightensAndCleansAPhotographedSheet() {
        val file = File(dir, "scan-test.jpg")
        assumeTrue("scan-test.jpg not on the device", file.isFile)
        val photo = BitmapFactory.decodeFile(file.path)
        val corners = PageScanner.findCorners(photo).points
        // The sheet's real corners in the test photo.
        val expected = floatArrayOf(260f, 220f, 1340f, 300f, 1420f, 1800f, 180f, 1720f)
        for (i in 0 until 8) assertEquals("coordinate $i", expected[i], corners[i], 30f)

        val started = System.currentTimeMillis()
        val clean = PageScanner.process(photo, PageFinder.Corners(corners), ScanSettings(ScanFilter.CLEAN))
        println("PageScannerTest: ${clean.width} x ${clean.height} in ${System.currentTimeMillis() - started} ms")
        // Upright and about the sheet's proportions (roughly 1100 x 1500).
        assertTrue(clean.height > clean.width)
        // The paper comes out white even under the shadow; the "writing" stays dark.
        val paperInShadow = clean.getPixel(clean.width / 6, clean.height * 9 / 10)
        assertTrue("paper ${Integer.toHexString(paperInShadow)}", android.graphics.Color.red(paperInShadow) > 225)
        File(dir, "scan-result.png").outputStream().use { clean.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun trainedModelFindsTheSheet() {
        val file = File(dir, "scan-test.jpg")
        assumeTrue("scan-test.jpg not on the device", file.isFile)
        val photo = BitmapFactory.decodeFile(file.path)
        val model = EdgeModel(InstrumentationRegistry.getInstrumentation().targetContext)
        val started = System.currentTimeMillis()
        val corners = model.find(photo)
        println("PageScannerTest model: ${corners?.points?.joinToString()} in ${System.currentTimeMillis() - started} ms")
        val expected = floatArrayOf(260f, 220f, 1340f, 300f, 1420f, 1800f, 180f, 1720f)
        for (i in 0 until 8) assertEquals("coordinate $i", expected[i], corners!!.points[i], 45f)
        model.close()
    }
}
