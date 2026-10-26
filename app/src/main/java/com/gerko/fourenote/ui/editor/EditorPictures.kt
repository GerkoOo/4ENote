package com.gerko.fourenote.ui.editor

import com.gerko.fourenote.data.ink.PageImage

// Part of NoteEditorViewModel: The selected picture: moving, cropping, turning, inverting, locking, fading it.
// Extension functions, so callers keep writing viewModel.x(); the state stays in the class.

fun NoteEditorViewModel.selectImage(pageId: String, imageId: String?) {
    clearSelection()
    selectedImage = imageId?.let { pageId to it }
}

internal fun NoteEditorViewModel.selected(): Pair<PageState, Int>? {
    val (pageId, imageId) = selectedImage ?: return null
    val page = page(pageId) ?: return null
    val index = page.images.indexOfFirst { it.id == imageId }
    return if (index < 0) null else page to index
}

fun NoteEditorViewModel.updateImage(pageId: String, image: PageImage) {
    val page = page(pageId) ?: return
    val index = page.images.indexOfFirst { it.id == image.id }
    if (index < 0 || page.images[index] == image) return
    recordUndo()
    page.images[index] = image
    persist()
}

fun NoteEditorViewModel.deleteSelectedImage() {
    val (page, index) = selected() ?: return
    recordUndo()
    page.images.removeAt(index)
    selectedImage = null
    persist()
}

fun NoteEditorViewModel.duplicateSelectedImage() {
    val (page, index) = selected() ?: return
    recordUndo()
    val copy = page.images[index].let { it.copy(id = java.util.UUID.randomUUID().toString(), x = it.x + 24f, y = it.y + 24f) }
    page.images.add(copy)
    selectedImage = page.id to copy.id
    persist()
}

fun NoteEditorViewModel.rotateSelectedImage(degrees: Float) {
    val (page, index) = selected() ?: return
    recordUndo()
    page.images[index] = page.images[index].let { it.copy(rotation = (it.rotation + degrees) % 360f) }
    persist()
}

fun NoteEditorViewModel.toggleSelectedImageLocked() {
    val (page, index) = selected() ?: return
    recordUndo()
    page.images[index] = page.images[index].let { it.copy(locked = !it.locked) }
    persist()
    if (page.images[index].locked) {
        message = "Picture locked. To unlock it, tap it with the lasso tool."
        selectedImage = null
    }
}

fun NoteEditorViewModel.toggleSelectedImageInverted() {
    val (page, index) = selected() ?: return
    recordUndo()
    page.images[index] = page.images[index].let { it.copy(inverted = !it.inverted, negative = false) }
    persist()
}

fun NoteEditorViewModel.toggleSelectedImageNegative() {
    val (page, index) = selected() ?: return
    recordUndo()
    page.images[index] = page.images[index].let { it.copy(negative = !it.negative, inverted = false) }
    persist()
}

fun NoteEditorViewModel.imageFile(name: String) = attachments.file(noteId, name)

/**
 * Shows only part of the selected picture: [l], [t], [r], [b] are its edges as fractions of the
 * whole picture. The frame keeps the same scale and centre, so the picture shrinks to the crop.
 */
fun NoteEditorViewModel.cropSelectedImage(l: Float, t: Float, r: Float, b: Float) {
    val (page, index) = selected() ?: return
    val image = page.images[index]
    val oldW = (image.cropRight - image.cropLeft).coerceAtLeast(0.01f)
    val oldH = (image.cropBottom - image.cropTop).coerceAtLeast(0.01f)
    val newW = (r - l).coerceAtLeast(0.01f)
    val newH = (b - t).coerceAtLeast(0.01f)
    if (image.cropLeft == l && image.cropTop == t && image.cropRight == r && image.cropBottom == b) return
    recordUndo()
    page.images[index] = image.copy(
        cropLeft = l, cropTop = t, cropRight = r, cropBottom = b,
        width = image.width * newW / oldW,
        height = image.height * newH / oldH,
    )
    persist()
}

fun NoteEditorViewModel.bringSelectedImageToFront() {
    val (page, index) = selected() ?: return
    if (index == page.images.lastIndex) return
    recordUndo()
    page.images.add(page.images.removeAt(index))
    persist()
}

fun NoteEditorViewModel.setSelectedImageOpacity(opacity: Float) {
    val (page, index) = selected() ?: return
    if (!opacityUndoRecorded) {
        recordUndo()
        opacityUndoRecorded = true
    }
    page.images[index] = page.images[index].copy(opacity = opacity.coerceIn(0.05f, 1f))
    persist()
}

fun NoteEditorViewModel.finishOpacityChange() {
    opacityUndoRecorded = false
}
