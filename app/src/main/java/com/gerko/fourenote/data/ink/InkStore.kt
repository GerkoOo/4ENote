package com.gerko.fourenote.data.ink

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Keeps each note's ink in files, one per page, instead of one big database row (Android
 * can't read rows over about 2 MB, which a long note passes quickly):
 *
 * ```
 * <root>/<noteId>/manifest.json      pages in order, page sizes, backgrounds, pictures, paper, layers
 * <root>/<noteId>/pages/<pageId>.ink that page's strokes (see InkPageCodec)
 * <root>/<noteId>/synced.4enote.gz   the note as of the last sync, the base for merging
 * ```
 *
 * Only pages whose strokes changed since the last save are rewritten. Every file is written
 * to a temporary name first and then moved into place, so a crash leaves the old version.
 */
class InkStore(private val root: File) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    // The strokes last written for each page, per note: a page whose stroke list holds the same
    // (immutable) stroke objects hasn't changed and isn't written again.
    private val written = ConcurrentHashMap<String, MutableMap<String, List<InkStroke>>>()

    private fun dir(noteId: String) = File(root, noteId)
    private fun manifest(noteId: String) = File(dir(noteId), "manifest.json")
    private fun pagesDir(noteId: String) = File(dir(noteId), "pages")
    private fun pageFile(noteId: String, pageId: String) = File(pagesDir(noteId), "$pageId.ink")
    private fun snapshotFile(noteId: String) = File(dir(noteId), "synced.4enote.gz")

    private suspend fun <T> locked(noteId: String, block: suspend () -> T): T =
        locks.getOrPut(noteId) { Mutex() }.withLock { withContext(Dispatchers.IO) { block() } }

    fun exists(noteId: String) = manifest(noteId).isFile

    /** The whole note, or null if it has never been saved here. */
    suspend fun load(noteId: String): InkDocument? = locked(noteId) {
        val doc = readManifest(noteId) ?: return@locked null
        val seen = HashMap<String, List<InkStroke>>()
        val pages = doc.pages.map { page ->
            val strokes = readPage(noteId, page.id)
            seen[page.id] = strokes
            page.copy(strokes = strokes)
        }
        written[noteId] = seen
        doc.copy(pages = pages)
    }

    /** Pages, pictures and layers without any ink: cheap to read for every note. */
    suspend fun loadOutline(noteId: String): InkDocument? = locked(noteId) { readManifest(noteId) }

    /** Paper, layers and only the first page with its ink: enough for a thumbnail. */
    suspend fun loadFirstPage(noteId: String): InkDocument? = locked(noteId) {
        val doc = readManifest(noteId) ?: return@locked null
        val first = doc.pages.first()
        doc.copy(pages = listOf(first.copy(strokes = readPage(noteId, first.id))))
    }

    suspend fun save(noteId: String, doc: InkDocument) = locked(noteId) {
        val pagesDir = pagesDir(noteId).apply { mkdirs() }
        val previous = written.getOrPut(noteId) { HashMap() }
        for (page in doc.pages) {
            val last = previous[page.id]
            val unchanged = last != null && last.size == page.strokes.size &&
                last.indices.all { last[it] === page.strokes[it] } && pageFile(noteId, page.id).isFile
            if (!unchanged) {
                writeAtomically(pageFile(noteId, page.id)) { out -> InkPageCodec.write(page.strokes, out) }
                previous[page.id] = page.strokes
            }
        }
        val manifest = InkSerializer.encode(doc.copy(pages = doc.pages.map { it.copy(strokes = emptyList()) }))
        writeAtomically(manifest(noteId)) { it.write(manifest.encodeToByteArray()) }
        // Pages deleted from the note.
        val ids = doc.pages.mapTo(HashSet()) { it.id }
        pagesDir.listFiles()?.forEach { file ->
            val id = file.name.removeSuffix(".ink")
            if (file.name.endsWith(".ink") && id !in ids) {
                file.delete()
                previous.remove(id)
            }
        }
    }

    suspend fun readSnapshot(noteId: String): String? = locked(noteId) {
        snapshotFile(noteId).takeIf { it.isFile }?.let { file ->
            GZIPInputStream(file.inputStream()).bufferedReader().use { it.readText() }
        }
    }

    suspend fun writeSnapshot(noteId: String, text: String) = locked(noteId) {
        dir(noteId).mkdirs()
        writeAtomically(snapshotFile(noteId)) { out ->
            GZIPOutputStream(out).let { it.write(text.encodeToByteArray()); it.finish() }
        }
    }

    suspend fun copy(fromNoteId: String, toNoteId: String) = locked(fromNoteId) {
        val from = dir(fromNoteId)
        if (from.isDirectory) {
            from.copyRecursively(dir(toNoteId), overwrite = true)
            // A copy starts unsynced.
            snapshotFile(toNoteId).delete()
        }
    }

    suspend fun delete(noteId: String) = locked(noteId) {
        dir(noteId).deleteRecursively()
        written.remove(noteId)
    }

    private fun readManifest(noteId: String): InkDocument? {
        val file = manifest(noteId)
        if (!file.isFile) return null
        return InkSerializer.decode(file.readText())
    }

    private fun readPage(noteId: String, pageId: String): List<InkStroke> {
        val file = pageFile(noteId, pageId)
        if (!file.isFile) return emptyList()
        return file.inputStream().buffered().use(InkPageCodec::read)
    }

    private fun writeAtomically(target: File, write: (java.io.OutputStream) -> Unit) {
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.outputStream().use { out ->
            out.buffered().let { write(it); it.flush() }
            out.fd.sync()
        }
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
