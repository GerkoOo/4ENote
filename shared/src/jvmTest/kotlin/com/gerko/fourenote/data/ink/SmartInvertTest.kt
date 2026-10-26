package com.gerko.fourenote.data.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartInvertTest {
    @Test
    fun whitePaperBecomesBlackAndBlackTextBecomesWhite() {
        assertEquals(Triple(0, 0, 0), SmartInvert.apply(255, 255, 255))
        assertEquals(Triple(255, 255, 255), SmartInvert.apply(0, 0, 0))
    }

    @Test
    fun greysFlipBrightness() {
        val (r, g, b) = SmartInvert.apply(200, 200, 200)
        assertTrue(r in 54..56 && g in 54..56 && b in 54..56)
    }

    @Test
    fun colorsKeepTheirHue() {
        // A plain invert would turn red cyan and blue yellow; the smart invert keeps them.
        val (rr, rg, rb) = SmartInvert.apply(220, 30, 30)
        assertTrue("red should stay red-dominant: ($rr, $rg, $rb)", rr > rg && rr > rb)
        val (br, bg, bb) = SmartInvert.apply(30, 60, 220)
        assertTrue("blue should stay blue-dominant: ($br, $bg, $bb)", bb > br && bb > bg)
    }

    @Test
    fun invertedPictureIsSaved() {
        val image = PageImage(id = "i", file = "a.png", x = 1f, y = 2f, width = 3f, height = 4f, inverted = true)
        val page = InkPage(id = "p", background = PageBackground.PdfPage("doc.pdf", 0), invertBackground = true, images = listOf(image))
        val back = InkSerializer.decode(InkSerializer.encode(InkDocument(listOf(page)))).pages[0]
        assertEquals(true, back.invertBackground)
        assertEquals(true, back.images[0].inverted)
    }
}
