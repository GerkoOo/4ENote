package com.gerko.fourenote.data.ink

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The fixture was written by the old org.json code. Notes on disk must keep decoding the same,
 * and what this code writes must still be readable by old app versions.
 */
class FormatCompatibilityTest {
    private val fixture = javaClass.getResource("/ink-v1-fixture.json")!!.readText()

    @Test
    fun theOldFixtureDecodesToTheSameValues() {
        val doc = InkSerializer.decode(fixture)
        assertEquals(PaperStyle.DOTS, doc.paper)
        assertEquals(2, doc.pages.size)

        val page = doc.pages[0]
        assertEquals("25828d9e-c231-4da4-873d-7721b8690aeb", page.id)
        assertEquals(794f, page.width)
        assertEquals(1123f, page.height)
        assertEquals(PageBackground.PdfPage("pdf-1.pdf", 2), page.background)
        assertTrue(page.invertBackground)

        assertEquals(listOf("s1", "s2"), page.strokes.map { it.id })
        val s1 = page.strokes[0]
        assertEquals(-15654349, s1.color)
        assertEquals(2.5f, s1.width)
        assertEquals(listOf(1f, 2f, 3.3f, 4.5f, 10f, 20f), s1.points.toList())
        assertEquals(listOf(1f, 1.5f, 2f), s1.widths!!.toList())
        assertEquals(false, s1.highlighter)
        assertEquals(InkLayer.BASE_ID, s1.layerId)
        val s2 = page.strokes[1]
        assertEquals(-2130706688, s2.color)
        assertEquals(12f, s2.width)
        assertEquals(true, s2.highlighter)
        assertEquals(null, s2.widths)

        val text = page.texts.single()
        assertEquals("t1", text.id)
        assertEquals("Hello \$x^2\$", text.text)
        assertEquals(200f, text.width)
        assertEquals(true, text.autoMath)

        val image = page.images.single()
        assertEquals("i1", image.id)
        assertEquals("image-1.jpg", image.file)
        assertEquals(12f, image.rotation)
        assertEquals(0.1f, image.cropLeft, 1e-6f)
        assertEquals(1f, image.cropRight)

        val second = doc.pages[1]
        assertEquals("p2", second.id)
        assertEquals(PageBackground.Image("image-2.png"), second.background)
        assertTrue(second.strokes.isEmpty())
    }

    @Test
    fun encodingTheDecodedFixtureGivesTheSameJson() {
        val encoded = Json.parseToJsonElement(InkSerializer.encode(InkSerializer.decode(fixture)))
        assertSameJson(Json.parseToJsonElement(fixture), encoded)
    }

    @Test
    fun penPointsAreWrittenShort() {
        val stroke = InkStroke(color = 0, width = 2.5f, highlighter = false, points = floatArrayOf(412.34f, 87.66f, 0.1f, 3f), id = "s")
        val text = InkSerializer.encode(InkDocument(listOf(InkPage(id = "p", strokes = listOf(stroke)))))
        assertTrue(text, text.contains("\"p\":[412.3,87.7,0.1,3]"))
        // And they read back as the very same floats.
        assertEquals(listOf(412.3f, 87.7f, 0.1f, 3f), InkSerializer.decode(text).pages[0].strokes[0].points.toList())
    }

    /** Key order doesn't matter; numbers are compared as doubles. */
    private fun assertSameJson(expected: JsonElement, actual: JsonElement, path: String = "") {
        when (expected) {
            is JsonObject -> {
                val a = actual as? JsonObject ?: throw AssertionError("$path: not an object")
                assertEquals("$path keys", expected.keys, a.keys)
                for (key in expected.keys) assertSameJson(expected.getValue(key), a.getValue(key), "$path.$key")
            }
            is JsonArray -> {
                val a = actual as? JsonArray ?: throw AssertionError("$path: not an array")
                assertEquals("$path size", expected.size, a.size)
                for (i in expected.indices) assertSameJson(expected[i], a[i], "$path[$i]")
            }
            is JsonPrimitive -> {
                val a = actual as? JsonPrimitive ?: throw AssertionError("$path: not a primitive")
                val e = expected.doubleOrNull
                val b = a.doubleOrNull
                if (e != null && b != null) {
                    assertTrue("$path: $e != $b", abs(e - b) <= 1e-6)
                } else {
                    assertEquals(path, expected.content, a.content)
                }
            }
            else -> throw AssertionError("$path: unexpected element")
        }
    }
}
