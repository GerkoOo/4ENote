package com.gerko.fourenote.ui.editor

import androidx.lifecycle.viewModelScope
import com.gerko.fourenote.data.recognition.HandwritingRecognizer
import com.gerko.fourenote.data.recognition.MathRecognizer
import com.gerko.fourenote.data.recognition.HandwritingLines
import com.gerko.fourenote.data.ink.PageImage
import com.gerko.fourenote.data.ink.PageText
import com.gerko.fourenote.data.ink.Tool
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Part of NoteEditorViewModel: Handwriting to text and to maths, and placing the result on the page.
// Extension functions, so callers keep writing viewModel.x(); the state stays in the class.

fun NoteEditorViewModel.recognizeSelection() {
    val page = selectionPageId?.let(::page) ?: return
    val strokes = page.strokes.filter { it in selection }
    if (strokes.isEmpty()) return
    recognizingMath = false
    mathSource = page.id to strokes
    val reader = textRecognizer ?: HandwritingRecognizer(attachments.context).also { textRecognizer = it }
    viewModelScope.launch {
        recognition = try {
            if (!reader.isDownloaded()) {
                if (!online()) {
                    recognition = RecognitionState.Failed(
                        "Handwriting recognition needs a one-time download (about ${reader.downloadMegabytes} MB). " +
                            "Connect to the internet and try again; after that it works offline.",
                    )
                    return@launch
                }
                recognition = RecognitionState.Working(downloading = true, progress = 0f)
                reader.download { recognition = RecognitionState.Working(downloading = true, progress = it) }
            }
            recognition = RecognitionState.Working(downloading = false)
            val text = reader.recognize(strokes)
            if (text.isEmpty()) RecognitionState.Failed("No text found") else RecognitionState.Done(listOf(text))
        } catch (e: Exception) {
            RecognitionState.Failed(e.message ?: "Recognition failed")
        }
    }
}

fun NoteEditorViewModel.recognizeSelectionAsMath() {
    val page = selectionPageId?.let(::page) ?: return
    val strokes = page.strokes.filter { it in selection }
    if (strokes.isEmpty()) return
    recognizingMath = true
    mathSource = page.id to strokes
    val math = mathRecognizer ?: MathRecognizer(attachments.context).also { mathRecognizer = it }
    viewModelScope.launch {
        recognition = try {
            if (!math.isDownloaded()) {
                if (!online()) {
                    recognition = RecognitionState.Failed(
                        "Maths recognition needs a one-time download (about ${math.downloadMegabytes} MB). " +
                            "Connect to the internet and try again; after that it works offline.",
                    )
                    return@launch
                }
                recognition = RecognitionState.Working(downloading = true, progress = 0f)
                math.download { recognition = RecognitionState.Working(downloading = true, progress = it) }
            }
            recognition = RecognitionState.Working(downloading = false)
            val latex = math.recognize(strokes)
            if (latex.isEmpty()) RecognitionState.Failed("No formula found") else RecognitionState.Done(listOf(latex))
        } catch (e: Exception) {
            RecognitionState.Failed(e.message ?: "Recognition failed")
        }
    }
}

/**
 * Puts [latex], typeset in the handwriting's color, on the page: in place of the handwriting
 * read as maths if [replace], otherwise just below it. One undo step.
 */
fun NoteEditorViewModel.placeFormula(latex: String, replace: Boolean) {
    val (pageId, strokes) = mathSource ?: return
    val page = page(pageId) ?: return
    val bounds = HandwritingLines.bounds(strokes)
    val color = strokes.firstOrNull()?.color ?: penColor.toArgb()
    viewModelScope.launch {
        try {
            val bitmap = withContext(Dispatchers.Default) { renderLatex(latex, FORMULA_RENDER_PX, color) }
                ?: throw IllegalArgumentException("This LaTeX can't be shown as a formula")
            val png = java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            val stored = attachments.storeImage(noteId, png.toByteArray().inputStream(), keepTransparency = true)
            // As tall as the handwriting, but never wider than the page.
            val aspect = stored.widthPx.toFloat() / stored.heightPx.coerceAtLeast(1)
            var height = bounds.height.coerceAtLeast(24f)
            var width = height * aspect
            val maxWidth = page.width - 48f
            if (width > maxWidth) {
                width = maxWidth
                height = width / aspect
            }
            recordUndo()
            if (replace) {
                page.strokes.removeAll { it in strokes }
                clearSelection()
            }
            val centerY = if (replace) bounds.top + bounds.height / 2 else bounds.bottom + 12f + height / 2
            val centerX = (bounds.left + width / 2).coerceIn(24f + width / 2, page.width - 24f - width / 2)
            page.images.add(PageImage(file = stored.file, x = centerX, y = centerY, width = width, height = height))
            persist()
            recognition = null
        } catch (e: Exception) {
            message = e.message ?: "Couldn't place the formula"
        }
    }
}

/**
 * Puts [text] on the page as a typed text box in the handwriting's colour and about its size:
 * in place of the handwriting if [replace], otherwise just below it. One undo step.
 */
fun NoteEditorViewModel.placeText(text: String, replace: Boolean) {
    val (pageId, strokes) = mathSource ?: return
    val page = page(pageId) ?: return
    if (text.isBlank()) return
    val bounds = HandwritingLines.bounds(strokes)
    val color = strokes.firstOrNull()?.color ?: penColor.toArgb()
    val lines = text.lines().size.coerceAtLeast(1)
    // Handwriting's letters are roughly half its line height; typed text the same size reads naturally.
    val size = (bounds.height / lines * 0.55f).coerceIn(12f, 40f)
    val width = maxOf(bounds.width, 160f).coerceAtMost(page.width - 24f)
    val x = bounds.left.coerceIn(0f, page.width - width)
    val y = if (replace) bounds.top else bounds.bottom + 12f
    recordUndo()
    if (replace) {
        page.strokes.removeAll { it in strokes }
        clearSelection()
    }
    val box = PageText(x = x, y = y, width = width, text = text.trim(), style = textStyle.copy(size = size, color = color), autoMath = false)
    page.texts.add(box)
    persist()
    recognition = null
    // Open it for typing straight away, like any text box.
    tool = Tool.TEXT
    editingText = page.id to box.id
}

fun NoteEditorViewModel.closeRecognition() {
    recognition = null
}
