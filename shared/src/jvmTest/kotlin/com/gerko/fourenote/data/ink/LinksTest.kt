package com.gerko.fourenote.data.ink

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinksTest {
    private fun stroke(id: String, vararg points: Float) =
        InkStroke(color = 0xFF000000.toInt(), width = 2f, highlighter = false, points = points, id = id)

    private fun link(id: String, target: LinkTarget, strokeIds: List<String> = emptyList()) =
        PageLink(id = id, strokeIds = strokeIds, target = target)

    @Test
    fun targetObjectsFollowStrokesToTheirNewIds() {
        val links = listOf(
            link("a", LinkTarget("note-1", "page-1", objectIds = listOf("s1", "s2"))),
            link("b", LinkTarget("note-1", "page-2", objectIds = listOf("s1"))),
            link("c", LinkTarget("note-2", "page-1", objectIds = listOf("s1"))),
        )
        val replaced = links.withTargetStrokesReplaced("note-1", "page-1", mapOf("s1" to listOf("s1a", "s1b")))
        assertEquals(listOf("s1a", "s1b", "s2"), replaced[0].target.objectIds)
        // Other pages and notes keep their targets.
        assertEquals(listOf("s1"), replaced[1].target.objectIds)
        assertEquals(listOf("s1"), replaced[2].target.objectIds)
    }

    @Test
    fun targetObjectsAreDroppedWhenTheirStrokeIsGone() {
        val links = listOf(link("a", LinkTarget("note-1", "page-1", objectIds = listOf("s1", "s2"))))
        val replaced = links.withTargetStrokesReplaced("note-1", "page-1", mapOf("s1" to emptyList()))
        assertEquals(listOf("s2"), replaced[0].target.objectIds)
    }

    @Test
    fun targetObjectsAreLeftAloneWithoutReplacements() {
        val links = listOf(link("a", LinkTarget("note-1", "page-1", objectIds = listOf("s1"))))
        assertEquals(links, links.withTargetStrokesReplaced("note-1", "page-1", emptyMap()))
    }

    @Test
    fun objectBoundsCoverStrokesTextsAndPictures() {
        val strokes = listOf(stroke("s1", 10f, 20f, 30f, 40f))
        val texts = listOf(PageText(id = "t1", x = 100f, y = 50f, width = 40f, style = TextStyleSpec(size = 10f)))
        val images = listOf(PageImage(id = "i1", file = "a.png", x = 200f, y = 200f, width = 20f, height = 40f))
        val bounds = objectBounds(listOf("s1", "t1", "i1"), strokes, texts, images)!!
        assertArrayEquals(floatArrayOf(10f, 20f, 210f, 220f), bounds, 0.01f)
    }

    @Test
    fun objectBoundsIgnoreMissingObjects() {
        val strokes = listOf(stroke("s1", 10f, 20f, 30f, 40f))
        assertArrayEquals(floatArrayOf(10f, 20f, 30f, 40f), objectBounds(listOf("s1", "gone"), strokes, emptyList(), emptyList())!!, 0.01f)
        assertNull(objectBounds(listOf("gone"), strokes, emptyList(), emptyList()))
        assertNull(objectBounds(emptyList(), strokes, emptyList(), emptyList()))
    }

    @Test
    fun linkAnchorBoundsUseTheSameBox() {
        val strokes = listOf(stroke("s1", 10f, 20f, 30f, 40f))
        val link = PageLink(strokeIds = listOf("s1"), target = LinkTarget("note-1"))
        assertArrayEquals(objectBounds(listOf("s1"), strokes, emptyList(), emptyList())!!, linkAnchorBounds(link, strokes, emptyList(), emptyList())!!, 0.01f)
    }
}
