package com.gerko.fourenote.ui.editor

import com.gerko.fourenote.data.Note
import com.gerko.fourenote.data.ink.InkPage
import com.gerko.fourenote.data.ink.LinkTarget
import com.gerko.fourenote.data.ink.PageLink
import kotlinx.coroutines.flow.first

// Part of NoteEditorViewModel: Links from the selection to other notes, pages or objects.
// Extension functions, so callers keep writing viewModel.x(); the state stays in the class.

/**
 * What a new link would hang on: the page, the lasso selection's stroke ids, or else the
 * selected picture's id. Null if nothing is selected.
 */
fun NoteEditorViewModel.selectionAnchors(): Triple<String, List<String>, List<String>>? {
    val pageId = selectionPageId ?: selectedImage?.first ?: return null
    val strokeIds = if (selection.isNotEmpty()) selection.map { it.id } else emptyList()
    val imageIds = if (strokeIds.isEmpty()) listOfNotNull(selectedImage?.takeIf { it.first == pageId }?.second) else emptyList()
    if (strokeIds.isEmpty() && imageIds.isEmpty()) return null
    return Triple(pageId, strokeIds, imageIds)
}

fun NoteEditorViewModel.addLink(pageId: String, strokeIds: List<String>, imageIds: List<String>, target: LinkTarget) {
    val page = page(pageId) ?: return
    if (strokeIds.isEmpty() && imageIds.isEmpty()) return
    recordUndo()
    page.links.add(PageLink(strokeIds = strokeIds, imageIds = imageIds, target = target))
    persist()
    message = "Link added"
}

/**
 * Links what is selected to [target]: the lasso selection's strokes, or else the selected
 * picture. Nothing selected does nothing.
 */
fun NoteEditorViewModel.linkSelection(target: LinkTarget) {
    val (pageId, strokeIds, imageIds) = selectionAnchors() ?: return
    addLink(pageId, strokeIds, imageIds, target)
    clearSelection()
}

/**
 * What the current selection would make a link point to: the lasso selection's strokes, or
 * else the selected picture, on the page they are on. Null if nothing is selected.
 */
fun NoteEditorViewModel.pickedTarget(): LinkTarget? {
    val (pageId, strokeIds, imageIds) = selectionAnchors() ?: return null
    return LinkTarget(noteId, pageId, objectIds = strokeIds + imageIds)
}

fun NoteEditorViewModel.removeLink(pageId: String, linkId: String) {
    val page = page(pageId) ?: return
    if (page.links.none { it.id == linkId }) return
    recordUndo()
    page.links.removeAll { it.id == linkId }
    persist()
}

suspend fun NoteEditorViewModel.linkableNotes(): List<Note> = repository.observeAll().first()

/**
 * The pages a link can point to in [noteId]: this note's pages as they are on screen, or
 * another note's as saved. Null if that note is gone or locked.
 */
suspend fun NoteEditorViewModel.linkablePages(noteId: String): List<InkPage>? {
    if (noteId == this.noteId) return pages.map { it.toPage() }
    val note = repository.getById(noteId) ?: return null
    if (note.locked) return null
    return repository.loadDocument(noteId).pages
}

/**
 * Checks where [link] leads now. A link to this note's current page (no page id) resolves to
 * [LinkResolution.SameNote] with index -1: the reader is already there.
 */
suspend fun NoteEditorViewModel.resolve(link: PageLink): LinkResolution {
    val target = link.target
    if (target.noteId == noteId) {
        val pageId = target.pageId ?: return LinkResolution.SameNote(-1)
        val index = pages.indexOfFirst { it.id == pageId }
        if (index < 0) return LinkResolution.PageNotFound(noteId)
        if (target.objectIds.isEmpty()) return LinkResolution.SameNote(index)
        val page = pages[index]
        val alive = target.objectIds.filter { id ->
            page.strokes.any { it.id == id } || page.texts.any { it.id == id } || page.images.any { it.id == id }
        }
        return if (alive.isEmpty()) LinkResolution.ObjectsNotFound(noteId, pageId) else LinkResolution.SameNote(index, alive)
    }
    val note = repository.getById(target.noteId)
    if (note == null || note.deleted || note.trashedAt != null) return LinkResolution.NoteNotFound
    val pageId = target.pageId ?: return LinkResolution.OtherNote(target.noteId, null)
    // Loaded once: the page and the objects the link points to are both checked against it.
    val page = repository.loadDocument(target.noteId).pages.firstOrNull { it.id == pageId }
        ?: return LinkResolution.PageNotFound(target.noteId)
    if (target.objectIds.isEmpty()) return LinkResolution.OtherNote(target.noteId, pageId)
    val alive = target.objectIds.filter { id ->
        page.strokes.any { it.id == id } || page.texts.any { it.id == id } || page.images.any { it.id == id }
    }
    return if (alive.isEmpty()) LinkResolution.ObjectsNotFound(target.noteId, pageId) else LinkResolution.OtherNote(target.noteId, pageId, alive)
}
