package com.gerko.fourenote.data.ink

import org.junit.Assert.assertEquals
import org.junit.Test

class InkSerializerTest {
    private fun stroke(id: String, x: Float) =
        InkStroke(color = 7, width = 3f, highlighter = false, points = floatArrayOf(x, 10f, x + 5f, 20f), id = id)

    @Test
    fun textBoxesRoundTrip() {
        val text = PageText(
            x = 40f, y = 80f, width = 300f, text = "Area \$\\pi r^2\$\nline two",
            style = TextStyleSpec(size = 24f, color = 0xFFFF0000.toInt(), bold = true, italic = true, font = TextFont.MONO),
            autoMath = false,
        )
        val doc = InkDocument(listOf(InkPage(id = "p", texts = listOf(text))))
        assertEquals(listOf(text), InkSerializer.decode(InkSerializer.encode(doc)).pages[0].texts)
    }

    @Test
    fun pagesWithBackgroundsRoundTrip() {
        val doc = InkDocument(
            pages = listOf(
                InkPage(id = "a", strokes = listOf(stroke("s1", 0f))),
                InkPage(id = "b", width = 794f, height = 600f, background = PageBackground.Image("scan-1.jpg")),
                InkPage(id = "c", background = PageBackground.PdfPage("slides.pdf", pageIndex = 4)),
            ),
            paper = PaperStyle.GRID,
        )
        val back = InkSerializer.decode(InkSerializer.encode(doc))
        assertEquals(listOf("a", "b", "c"), back.pages.map { it.id })
        assertEquals(PaperStyle.GRID, back.paper)
        assertEquals(listOf("s1"), back.pages[0].strokes.map { it.id })
        assertEquals(600f, back.pages[1].height)
        assertEquals(PageBackground.Image("scan-1.jpg"), back.pages[1].background)
        assertEquals(PageBackground.PdfPage("slides.pdf", 4), back.pages[2].background)
    }

    @Test
    fun oldSinglePageNotesBecomeOnePageThatFitsTheirInk() {
        // Version 2 format: strokes on one unbounded canvas, here drawn past the A4 width.
        val v2 = """{"v":2,"paper":"LINED","strokes":[{"id":"s1","c":1,"w":3,"h":false,"p":[100,100,1200,300]}]}"""
        val doc = InkSerializer.decode(v2)
        assertEquals(1, doc.pages.size)
        assertEquals("s1", doc.pages[0].strokes.single().id)
        assertEquals(true, doc.pages[0].width >= 1200f)
        // Decoding twice gives the same page id, so sync can merge old notes.
        assertEquals(doc.pages[0].id, InkSerializer.decode(v2).pages[0].id)
    }
}

class InkLayersTest {
    private fun stroke(id: String, layer: String) =
        InkStroke(1, 3f, false, floatArrayOf(0f, 0f, 5f, 5f), layerId = layer, id = id)

    @Test
    fun layersAndStrokeLayerRoundTrip() {
        val doc = InkDocument(
            pages = listOf(InkPage(strokes = listOf(stroke("a", InkLayer.BASE_ID), stroke("b", "notes")))),
            layers = listOf(InkLayer.BASE, InkLayer("notes", "Notes", visible = false)),
        )
        val back = InkSerializer.decode(InkSerializer.encode(doc))
        assertEquals(doc.layers, back.layers)
        assertEquals(listOf(InkLayer.BASE_ID, "notes"), back.pages[0].strokes.map { it.layerId })
    }

    @Test
    fun hiddenLayersAreLeftOutAndUpperLayersDrawOnTop() {
        val page = InkPage(strokes = listOf(stroke("top", "upper"), stroke("bottom", InkLayer.BASE_ID), stroke("gone", "hidden")))
        val doc = InkDocument(
            pages = listOf(page),
            layers = listOf(InkLayer.BASE, InkLayer("upper", "Upper"), InkLayer("hidden", "Hidden", visible = false)),
        )
        assertEquals(listOf("bottom", "top"), doc.visibleStrokes(page).map { it.id })
    }

    @Test
    fun notesFromBeforeLayersGetOneBaseLayer() {
        val v3 = """{"v":3,"paper":"LINED","pages":[{"id":"p","w":794,"h":1123,"strokes":[{"id":"s","c":1,"w":3,"h":false,"p":[0,0,5,5]}]}]}"""
        val doc = InkSerializer.decode(v3)
        assertEquals(listOf(InkLayer.BASE), doc.layers)
        assertEquals(InkLayer.BASE_ID, doc.pages[0].strokes[0].layerId)
    }
}

class BackgroundPlacementTest {
    @Test
    fun movedAndResizedImageIsSaved() {
        val page = InkPage(
            id = "p",
            background = PageBackground.Image("photo.jpg"),
            placement = BackgroundPlacement(x = 40f, y = -12.5f, scale = 0.6f),
        )
        val back = InkSerializer.decode(InkSerializer.encode(InkDocument(listOf(page))))
        assertEquals(BackgroundPlacement(40f, -12.5f, 0.6f), back.pages[0].placement)
    }

    @Test
    fun imagesFromBeforeFillTheirPage() {
        val old = """{"v":3,"pages":[{"id":"p","w":794,"h":600,"bg":{"type":"image","file":"a.jpg"},"strokes":[]}]}"""
        assertEquals(BackgroundPlacement(), InkSerializer.decode(old).pages[0].placement)
    }
}

class PageImageTest {
    private val image = PageImage(id = "i", file = "a.png", x = 100f, y = 100f, width = 80f, height = 40f)

    @Test
    fun picturesOnAPageAreSaved() {
        val page = InkPage(id = "p", images = listOf(image.copy(rotation = 30f, opacity = 0.5f)))
        val back = InkSerializer.decode(InkSerializer.encode(InkDocument(listOf(page))))
        assertEquals(listOf(image.copy(rotation = 30f, opacity = 0.5f)), back.pages[0].images)
    }

    @Test
    fun stretchingWidthKeepsTheLeftEdgeAndHeight() {
        // 80 wide, so its left edge is at x = 60. Drag the right edge out to x = 200.
        val wider = image.stretchedTo(200f, 100f, horizontal = true)
        assertEquals(140f, wider.width, 0.01f)
        assertEquals(40f, wider.height, 0.01f)
        assertEquals(60f, wider.x - wider.width / 2, 0.01f)
    }

    @Test
    fun stretchingHeightOfARotatedPictureFollowsItsOwnAxis() {
        // Turned a quarter turn clockwise, the picture's "down" points left on the page,
        // so dragging its bottom-edge dot left makes it taller while its top edge stays put.
        val turned = image.copy(rotation = 90f)
        val taller = turned.stretchedTo(40f, 100f, horizontal = false)
        assertEquals(80f, taller.width, 0.01f)
        assertEquals(80f, taller.height, 0.01f)
        // The top edge (on the right, at page x = 120) has not moved.
        assertEquals(120f, taller.x + taller.height / 2, 0.01f)
        assertEquals(100f, taller.y, 0.01f)
    }

    @Test
    fun hitTestFollowsRotation() {
        // A wide picture: 40 units right of its center is on it, 40 units below is not...
        assertEquals(true, image.contains(140f, 100f))
        assertEquals(false, image.contains(100f, 140f))
        // ...until it is turned a quarter turn.
        val turned = image.copy(rotation = 90f)
        assertEquals(false, turned.contains(140f, 100f))
        assertEquals(true, turned.contains(100f, 140f))
    }
}
