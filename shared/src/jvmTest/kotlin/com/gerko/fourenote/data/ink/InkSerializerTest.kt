package com.gerko.fourenote.data.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class InkSerializerTest {
    private fun stroke(id: String, x: Float) =
        InkStroke(color = 7, width = 3f, highlighter = false, points = floatArrayOf(x, 10f, x + 5f, 20f), id = id)

    @Test
    fun pointedPenStrokesKeepTheirSquareEnds() {
        val stroke = InkStroke(0, 8f, false, floatArrayOf(0f, 0f, 0f, 10f), floatArrayOf(1f, 0.2f), squareEnds = true)
        val back = InkSerializer.decode(InkSerializer.encode(InkDocument(listOf(InkPage(strokes = listOf(stroke)))))).pages[0].strokes[0]
        assertEquals(true, back.squareEnds)
        assertEquals(true, back.transformed(1f, 1f, 2f, 2f, 0f, 0f).squareEnds)
        assertEquals(true, back.withColor(5).squareEnds)
    }

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

class PageLinkTest {
    private fun stroke(id: String) = InkStroke(1, 3f, false, floatArrayOf(0f, 0f, 5f, 5f), id = id)

    @Test
    fun linksWithEveryKindOfTargetRoundTrip() {
        val links = listOf(
            PageLink(id = "l1", strokeIds = listOf("s1"), target = LinkTarget(noteId = "note-a")),
            PageLink(id = "l2", textIds = listOf("t1"), target = LinkTarget(noteId = "note-b", pageId = "page-2")),
            PageLink(
                id = "l3",
                imageIds = listOf("i1"),
                target = LinkTarget(noteId = "note-c", pageId = "page-3", objectIds = listOf("s9", "t9")),
            ),
            PageLink(id = "l4", strokeIds = listOf("s1"), target = LinkTarget(noteId = "note-d", stickyId = "sticky-1")),
        )
        val page = InkPage(
            id = "p",
            strokes = listOf(stroke("s1")),
            texts = listOf(PageText(id = "t1", x = 0f, y = 0f, width = 100f)),
            images = listOf(PageImage(id = "i1", file = "a.png", x = 0f, y = 0f, width = 10f, height = 10f)),
            links = links,
        )
        val back = InkSerializer.decode(InkSerializer.encode(InkDocument(listOf(page))))
        assertEquals(links, back.pages[0].links)
    }

    @Test
    fun pagesWithoutLinksWriteNoLinksKey() {
        val doc = InkDocument(listOf(InkPage(id = "p", strokes = listOf(stroke("s1")))))
        assertFalse(InkSerializer.encode(doc).contains("links"))
    }

    @Test
    fun unsafeIdsAndEmptyTargetsAreDropped() {
        val json = """{"v":3,"pages":[{"id":"p","w":794,"h":1123,"strokes":[],"links":[""" +
            """{"id":"ok","s":["s1"],"to":{"note":"note-a"}},""" +
            """{"id":"../evil","s":["s1"],"to":{"note":"note-a"}},""" +
            """{"id":"l2","s":["s1"],"to":{"note":"../evil"}},""" +
            """{"id":"l3","s":["s1"],"to":{"note":""}},""" +
            """42,{"id":"l4","s":["s1"]}""" +
            """]}]}"""
        val links = InkSerializer.decode(json).pages[0].links
        assertEquals(listOf("ok"), links.map { it.id })
    }

    @Test
    fun linksFollowStrokesToTheirNewIds() {
        val links = listOf(
            PageLink(id = "l1", strokeIds = listOf("a", "b"), target = LinkTarget(noteId = "n")),
            PageLink(id = "l2", strokeIds = listOf("c"), target = LinkTarget(noteId = "n")),
        )
        // "a" was split in two, "b" was erased, "c" was not touched.
        val replaced = links.withStrokesReplaced(mapOf("a" to listOf("a1", "a2"), "b" to emptyList()))
        assertEquals(listOf("a1", "a2"), replaced[0].strokeIds)
        assertEquals(listOf("c"), replaced[1].strokeIds)
        // A link that uses none of the replaced ids is not copied.
        assertSame(links[1], replaced[1])
    }

    @Test
    fun anchorBoundsCoverStrokesTextsAndPictures() {
        val stroke = InkStroke(1, 3f, false, floatArrayOf(10f, 20f, 50f, 60f), id = "s")
        val text = PageText(id = "t", x = 100f, y = 200f, width = 80f, style = TextStyleSpec(size = 10f))
        val image = PageImage(id = "i", file = "a.png", x = 300f, y = 300f, width = 40f, height = 20f)
        val strokesOnly = linkAnchorBounds(PageLink(strokeIds = listOf("s"), target = LinkTarget("n")), listOf(stroke), listOf(text), listOf(image))
        assertEquals(listOf(10f, 20f, 50f, 60f), strokesOnly!!.toList())
        val mixed = linkAnchorBounds(
            PageLink(strokeIds = listOf("s"), imageIds = listOf("i"), target = LinkTarget("n")),
            listOf(stroke), listOf(text), listOf(image),
        )
        assertEquals(listOf(10f, 20f, 320f, 310f), mixed!!.toList())
        val textOnly = linkAnchorBounds(PageLink(textIds = listOf("t"), target = LinkTarget("n")), listOf(stroke), listOf(text), listOf(image))
        assertEquals(listOf(100f, 200f, 180f, 214f), textOnly!!.toList())
        // Anchors that are no longer on the page give no box at all.
        assertEquals(null, linkAnchorBounds(PageLink(strokeIds = listOf("gone"), target = LinkTarget("n")), listOf(stroke), listOf(text), listOf(image)))
    }

    @Test
    fun deadLinksAreDropped() {
        val page = InkPage(
            id = "p",
            strokes = listOf(stroke("s1")),
            texts = listOf(PageText(id = "t1", x = 0f, y = 0f, width = 100f)),
            links = listOf(
                PageLink(id = "alive", strokeIds = listOf("s1"), target = LinkTarget(noteId = "a")),
                PageLink(id = "gone", strokeIds = listOf("s2"), target = LinkTarget(noteId = "b")),
                PageLink(id = "empty", target = LinkTarget(noteId = "c")),
                PageLink(id = "text", textIds = listOf("t1"), target = LinkTarget(noteId = "d")),
            ),
        )
        assertEquals(listOf("alive", "text"), page.withoutDeadLinks().links.map { it.id })
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

    @Test
    fun aCroppedNegativePictureSurvivesTheRoundTrip() {
        val image = PageImage(file = "a.jpg", x = 10f, y = 20f, width = 30f, height = 40f, negative = true,
            cropLeft = 0.1f, cropTop = 0.2f, cropRight = 0.9f, cropBottom = 0.7f)
        val doc = InkDocument(listOf(InkPage(images = listOf(image))))
        val back = InkSerializer.decode(InkSerializer.encode(doc)).pages.single().images.single()
        assertEquals(image, back)
        // An uncropped picture writes no crop at all.
        assertFalse(InkSerializer.encode(InkDocument(listOf(InkPage(images = listOf(image.copy(cropLeft = 0f, cropTop = 0f, cropRight = 1f, cropBottom = 1f)))))).contains("crop"))
    }
}
