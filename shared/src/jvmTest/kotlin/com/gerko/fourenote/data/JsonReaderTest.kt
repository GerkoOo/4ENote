package com.gerko.fourenote.data

import com.gerko.fourenote.data.ink.InkDocument
import com.gerko.fourenote.data.ink.InkLayer
import com.gerko.fourenote.data.ink.InkPage
import com.gerko.fourenote.data.ink.InkSerializer
import com.gerko.fourenote.data.ink.InkStroke
import com.gerko.fourenote.data.ink.PageBackground
import com.gerko.fourenote.data.ink.PageImage
import com.gerko.fourenote.data.ink.PaperStyle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class JsonReaderTest {
    @Test
    fun readsStringsWithEscapesTheSameAsKotlinx() {
        val samples = listOf("plain", "quote \" and \\ slash", "line\nbreak\ttab", "unicode é ✓ \u0001", "")
        for (s in samples) {
            val text = StringBuilder().also { JsonReader.quote(s, it) }.toString()
            assertEquals(s, JsonReader(text).string())
            assertEquals(s, (Json.parseToJsonElement(text) as JsonPrimitive).content)
        }
        assertEquals("A✓", JsonReader("\"\\u0041\\u2713\"").string())
    }

    @Test
    fun readsNestedValuesAndSkipsUnknownOnes() {
        val r = JsonReader("""{ "a": [1, 2.5, -3e2], "skip": {"x": [true, null, "s"]}, "b": "x" }""")
        var floats: FloatArray? = null
        var b = ""
        r.obj { key ->
            when (key) {
                "a" -> floats = r.floats()
                "b" -> b = r.string()
                else -> r.skip()
            }
        }
        assertArrayEquals(floatArrayOf(1f, 2.5f, -300f), floats, 0f)
        assertEquals("x", b)
        assertTrue(r.atEnd())
    }

    @Test
    fun aDamagedNoteGivesAnEmptyOneInsteadOfACrash() {
        for (bad in listOf("{", "{\"pages\":[{\"strokes\":[{\"p\":[1,", "not json", "[1,2]")) {
            val doc = InkSerializer.decode(bad)
            assertEquals(1, doc.pages.size)
        }
        // A word where a number belongs counts as 0, as it did before.
        val doc = InkSerializer.decode("""{"pages":[{"id":"p","strokes":[{"id":"s","p":[1,"x",3,null]}]}]}""")
        assertArrayEquals(floatArrayOf(1f, 0f, 3f, 0f), doc.pages[0].strokes[0].points, 0f)
    }

    @Test
    fun aFullNoteSurvivesTheRoundTrip() {
        val random = Random(1)
        fun stroke(i: Int) = InkStroke(
            color = random.nextInt(), width = 2.5f, highlighter = i % 3 == 0,
            points = FloatArray(40) { (random.nextFloat() * 8000).toInt() / 10f },
            widths = if (i % 2 == 0) FloatArray(20) { (random.nextFloat() * 300).toInt() / 100f } else null,
            layerId = if (i % 4 == 0) "l2" else InkLayer.BASE_ID, id = "s$i",
            nibAngle = if (i % 5 == 0) 0.75f else null, squareEnds = i % 7 == 0,
        )
        val doc = InkDocument(
            pages = listOf(
                InkPage(id = "p1", strokes = List(50, ::stroke), background = PageBackground.PdfPage("a \"b\".pdf", 3),
                    images = listOf(PageImage(id = "i", file = "x.jpg", x = 1.5f, y = 2f, width = 30f, height = 40f, cropLeft = 0.1f, label = "Photo 1"))),
                InkPage(id = "p2", strokes = List(5, ::stroke)),
            ),
            paper = PaperStyle.GRID,
            layers = listOf(InkLayer.BASE, InkLayer("l2", "Notes ✓", visible = false)),
        )
        val back = InkSerializer.decode(InkSerializer.encode(doc))
        assertEquals(doc.paper, back.paper)
        assertEquals(doc.layers, back.layers)
        assertEquals(doc.pages.map { it.copy(strokes = emptyList()) }, back.pages.map { it.copy(strokes = emptyList()) })
        for ((a, b) in doc.pages.flatMap { it.strokes }.zip(back.pages.flatMap { it.strokes })) {
            assertEquals(a.id, b.id)
            assertEquals(a.color, b.color)
            assertArrayEquals(a.points, b.points, 0f)
            assertEquals(a.widths?.toList(), b.widths?.toList())
            assertEquals(a.layerId, b.layerId)
            assertEquals(a.nibAngle, b.nibAngle)
            assertEquals(a.squareEnds, b.squareEnds)
            assertEquals(a.highlighter, b.highlighter)
        }
    }
}
