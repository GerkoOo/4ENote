package com.gerko.fourenote.ios.store

import com.gerko.fourenote.data.Note
import com.gerko.fourenote.data.ink.InkDocument
import com.gerko.fourenote.data.ink.InkPage
import com.gerko.fourenote.data.ink.InkSerializer
import com.gerko.fourenote.data.sync.FileSyncStore
import okio.FileSystem
import okio.Path
import okio.SYSTEM

/** A note as the iPad screens use it: a title, a modification time and its ink. */
data class StoredNote(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val document: InkDocument,
)

/**
 * The notes on this device, kept by the shared [FileSyncStore] under [root] (the same store the
 * sync engine works on), so every save here goes to the cloud with the next sync.
 */
class NoteStore(
    root: Path,
    fs: FileSystem = FileSystem.SYSTEM,
    now: () -> Long = { com.gerko.fourenote.platform.nowMillis() },
) {
    val sync = FileSyncStore(root, fs, now)

    /** Every readable note, newest first. A damaged note is skipped, not fatal. */
    fun all(): List<StoredNote> = sync.notes().mapNotNull { get(it.id) }

    fun get(id: String): StoredNote? {
        val note = sync.get(id) ?: return null
        val document = runCatching { InkSerializer.decode(note.inkData) }.getOrNull() ?: return null
        return StoredNote(note.id, note.title.ifEmpty { "Untitled" }, note.updatedAt, document)
    }

    fun save(note: StoredNote) {
        val current = sync.get(note.id) ?: Note(id = note.id)
        sync.save(current.copy(title = note.title, inkData = InkSerializer.encode(note.document)))
    }

    fun create(title: String = "Untitled"): StoredNote {
        val note = Note(title = title, inkData = InkSerializer.encode(InkDocument(pages = listOf(InkPage()))))
        sync.save(note)
        return get(note.id)!!
    }

    fun rename(id: String, title: String) {
        val note = sync.get(id) ?: return
        sync.save(note.copy(title = title))
    }

    fun delete(id: String) = sync.delete(id)
}
