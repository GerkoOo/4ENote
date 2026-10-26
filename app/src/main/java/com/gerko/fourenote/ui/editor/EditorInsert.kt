package com.gerko.fourenote.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.gerko.fourenote.data.ink.PageImage
import com.gerko.fourenote.data.ink.Tool
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.InputStream

// Part of NoteEditorViewModel: Putting pictures on pages: picked images, photo frames filled from the phone, scans.
// Extension functions, so callers keep writing viewModel.x(); the state stays in the class.

/**
 * Places pictures on a page, centered and sized to at most 40% of its width and height,
 * each a little offset from the last. Switches to Select with the last one
 * selected, so it can be moved straight away.
 */
fun NoteEditorViewModel.insertImages(pageId: String, opens: List<() -> InputStream?>) {
    val page = page(pageId) ?: return
    viewModelScope.launch {
        importing = true
        try {
            val stored = opens.mapNotNull { it() }.map { attachments.storeImage(noteId, it, keepTransparency = true) }
            if (stored.isEmpty()) return@launch
            recordUndo()
            stored.forEachIndexed { i, image ->
                val width = page.width * 0.4f
                var height = width * image.heightPx / image.widthPx.coerceAtLeast(1)
                val fit = minOf(1f, page.height * 0.4f / height)
                height *= fit
                page.images.add(
                    PageImage(
                        file = image.file,
                        x = page.width / 2 + i * 24f,
                        y = page.height / 2 + i * 24f,
                        width = width * fit,
                        height = height,
                    ),
                )
            }
            persist()
            tool = Tool.LASSO
            clearSelection()
            selectedImage = page.id to page.images.last().id
        } catch (e: Exception) {
            message = e.message ?: "Import failed"
        } finally {
            importing = false
        }
    }
}

// Fills waiting frames with photos that arrived by sync; other changes to the frame stay as they are here.
internal fun NoteEditorViewModel.fillFrames(frames: List<PageImage>) {
    val byId = frames.associateBy { it.id }
    var changed = false
    for (page in pages) {
        for (i in page.images.indices) {
            val frame = page.images[i]
            val filled = byId[frame.id] ?: continue
            if (!frame.waitingForPhoto) continue
            // The photo's shape comes from the other device; where the frame is and its size stay ours.
            val aspect = filled.width / filled.height.coerceAtLeast(1f)
            val width = minOf(frame.width, frame.height * aspect)
            page.images[i] = frame.copy(file = filled.file, width = width, height = width / aspect)
            changed = true
        }
    }
    if (changed) {
        persist()
        message = "A photo arrived from your other device"
    }
}

/**
 * Puts an empty frame on the page, waiting for a photo taken on another device (or later on
 * this one). It is selected so it can be moved and resized to where the photo should go.
 */
fun NoteEditorViewModel.requestPhoto(pageId: String) {
    val page = page(pageId) ?: return
    recordUndo()
    val frame = PageImage(
        file = "",
        x = page.width / 2,
        y = page.height / 2,
        width = page.width * 0.5f,
        height = page.width * 0.375f,
        requested = true,
        label = "Photo ${nextPhotoNumber()}",
    )
    page.images.add(frame)
    persist()
    requestSync()
    tool = Tool.LASSO
    clearSelection()
    selectedImage = page.id to frame.id
    message = "Move and size the frame; fill it from the camera button on your phone's notes list"
}

// Frames are numbered through the whole note: Photo 1, Photo 2, ...
internal fun NoteEditorViewModel.nextPhotoNumber(): Int = 1 + pages.flatMap { it.images }
    .filter { it.requested }
    .maxOfOrNull { it.label.removePrefix("Photo ").toIntOrNull() ?: 0 }.let { it ?: 0 }

/**
 * Scans or pictures taken in the editor: they fill the page's waiting photo frames first (the
 * selected one, then the others in order), and only what is left over goes onto the page.
 */
fun NoteEditorViewModel.insertScans(pageId: String, photos: List<ByteArray>) {
    val page = page(pageId) ?: return
    val selectedId = selectedImage?.takeIf { it.first == pageId }?.second
    val frames = page.images.filter { it.waitingForPhoto }.sortedByDescending { it.id == selectedId }
    if (frames.isEmpty()) {
        insertImages(pageId, photos.map { bytes -> { bytes.inputStream() } })
        return
    }
    viewModelScope.launch {
        importing = true
        try {
            recordUndo()
            frames.zip(photos).forEach { (frame, bytes) ->
                val stored = attachments.storeImage(noteId, bytes.inputStream())
                val aspect = stored.widthPx.toFloat() / stored.heightPx.coerceAtLeast(1)
                val width = minOf(frame.width, frame.height * aspect)
                val index = page.images.indexOfFirst { it.id == frame.id }
                if (index >= 0) page.images[index] = frame.copy(file = stored.file, width = width, height = width / aspect)
            }
            persist()
            requestSync()
            message = if (frames.size == 1) "Photo placed in ${frames[0].label.ifEmpty { "the frame" }}" else "Photos placed in the frames"
        } catch (e: Exception) {
            message = e.message ?: "Import failed"
        } finally {
            importing = false
        }
        val rest = photos.drop(frames.size)
        if (rest.isNotEmpty()) insertImages(pageId, rest.map { bytes -> { bytes.inputStream() } })
    }
}
