package com.gerko.fourenote.ios.editor

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Density
import com.gerko.fourenote.data.ink.InkDocument
import com.gerko.fourenote.data.ink.InkPage
import com.gerko.fourenote.data.ink.InkStroke
import com.gerko.fourenote.data.ink.PaperStyle
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EditorTest {
    private fun blankDoc() = InkDocument(pages = listOf(InkPage()), paper = PaperStyle.LINED)

    @Test
    fun strokeBuilderMakesPenStrokeWithWidths() {
        val builder = StrokeBuilder()
        builder.start(10f, 10f, 0L, 0.5f)
        for (i in 1 until 10) {
            builder.add(10f + i * 4f, 10f + i * 2f, i * 16L, 0.5f)
        }
        val stroke = builder.finish(EditorTool.PEN, 0xFF000000.toInt(), 3f)
        assertNotNull(stroke)
        stroke!!
        assertTrue("Pen stroke should have points", stroke.points.size >= 4)
        assertNotNull("Pen stroke should have widths", stroke.widths)
        assertEquals("One width per point", stroke.points.size / 2, stroke.widths!!.size)
        assertFalse(stroke.highlighter)
    }

    @Test
    fun eraseAtRemovesTouchedStrokeButNotFarOne() {
        val near = InkStroke(0xFF000000.toInt(), 3f, false, floatArrayOf(0f, 0f, 100f, 0f))
        val far = InkStroke(0xFF000000.toInt(), 3f, false, floatArrayOf(0f, 500f, 100f, 500f))
        val doc = InkDocument(pages = listOf(InkPage(strokes = listOf(near, far))))
        val state = EditorState(doc)
        state.eraseAt(0, 50f, 1f, 12f)
        val remaining = state.document.pages[0].strokes
        assertEquals(1, remaining.size)
        assertEquals(far.id, remaining[0].id)
    }

    @Test
    fun undoAndRedoRestoreDocuments() {
        val state = EditorState(blankDoc())
        val stroke = InkStroke(0xFF000000.toInt(), 3f, false, floatArrayOf(0f, 0f, 10f, 10f))
        state.addStroke(0, stroke)
        assertEquals(1, state.document.pages[0].strokes.size)
        assertTrue(state.canUndo)

        state.undo()
        assertEquals(0, state.document.pages[0].strokes.size)
        assertTrue(state.canRedo)

        state.redo()
        assertEquals(1, state.document.pages[0].strokes.size)
    }

    @Test
    fun onChangeIsCalledForEveryEdit() {
        var calls = 0
        val state = EditorState(blankDoc()) { calls++ }
        state.addStroke(0, InkStroke(0xFF000000.toInt(), 3f, false, floatArrayOf(0f, 0f, 10f, 10f)))
        state.undo()
        state.redo()
        assertEquals(3, calls)
    }

    @Test
    fun aFingerScrollsWhileTheStylusDraws() {
        val state = EditorState(blankDoc())
        val scene = ImageComposeScene(width = 1024, height = 1366, density = Density(1f)) { InkEditor(state) }
        try {
            scene.render()
            fun drag(type: PointerType) {
                val start = Offset(200f, 400f)
                // The test scene only counts a pen or finger as down with its "button" pressed.
                val down = PointerButtons(isPrimaryPressed = true)
                scene.sendPointerEvent(PointerEventType.Press, start, type = type, buttons = down)
                for (i in 1..10) scene.sendPointerEvent(PointerEventType.Move, start + Offset(i * 8f, i * 4f), type = type, buttons = down)
                scene.sendPointerEvent(PointerEventType.Release, start + Offset(80f, 40f), type = type, buttons = PointerButtons())
                scene.render()
            }
            // Palm or finger: no ink while finger drawing is off.
            drag(PointerType.Touch)
            assertEquals(0, state.document.pages[0].strokes.size)
            // Right away, while the pages may still glide from the finger: the pen writes anyway.
            drag(PointerType.Stylus)
            assertEquals(1, state.document.pages[0].strokes.size)
        } finally {
            scene.close()
        }
    }

    @Test
    fun drawingWithThePointerAddsAStroke() {
        val state = EditorState(blankDoc())
        // A mouse is the only pointer the desktop test scene can send; treat it like a finger.
        state.fingerDraws = true
        val scene = ImageComposeScene(width = 1024, height = 1366, density = Density(1f)) {
            InkEditor(state)
        }
        try {
            scene.render()
            val start = Offset(200f, 400f)
            scene.sendPointerEvent(PointerEventType.Press, start)
            for (i in 1..20) {
                scene.sendPointerEvent(PointerEventType.Move, start + Offset(i * 8f, i * 4f))
            }
            scene.sendPointerEvent(PointerEventType.Release, start + Offset(160f, 80f))
            scene.render()

            val strokes = state.document.pages[0].strokes
            assertEquals("One stroke should have been drawn", 1, strokes.size)

            val image = scene.render()
            val data = image.encodeToData(EncodedImageFormat.PNG)
                ?: error("Skia could not encode the screenshot")
            val out = File("build/screenshots/editor.png")
            out.parentFile?.mkdirs()
            out.writeBytes(data.bytes)
            assertTrue("Screenshot is empty", out.length() > 0L)
        } finally {
            scene.close()
        }
    }
}
