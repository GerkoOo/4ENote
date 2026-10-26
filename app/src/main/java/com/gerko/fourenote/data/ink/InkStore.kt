package com.gerko.fourenote.data.ink

import com.gerko.fourenote.data.SafeName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
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
 *
 * A locked note's folder holds a `sealed` marker, and then its files are encrypted with
 * [sealing] (the device key), each bound to its own name. Reading takes both forms, so a lock or
 * unlock that stopped halfway leaves every file readable; [setSealed] finishes it.
 */
class InkStore(val root: File, private val sealing: FileSealing? = null) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    // The strokes last written for each page, per note: a page whose stroke list holds the same
    // (immutable) stroke objects hasn't changed and isn't written again.
    private val written = ConcurrentHashMap<String, MutableMap<String, List<InkStroke>>>()

    private fun dir(noteId: String) = File(root, SafeName.require(noteId, "note id"))
    private fun manifest(noteId: String) = File(dir(noteId), "manifest.json")
    private fun pagesDir(noteId: String) = File(dir(noteId), "pages")
    private fun pageFile(noteId: String, pageId: String) = File(pagesDir(noteId), "${SafeName.require(pageId, "page id")}.ink")
    private fun snapshotFile(noteId: String) = File(dir(noteId), "synced.4enote.gz")
    private fun sealedMarker(noteId: String) = File(dir(noteId), SEALED_MARKER)

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
                writeNoteFile(noteId, pageFile(noteId, page.id)) { out -> InkPageCodec.write(page.strokes, out) }
                previous[page.id] = page.strokes
            }
        }
        val manifest = InkSerializer.encode(doc.copy(pages = doc.pages.map { it.copy(strokes = emptyList()) }))
        writeNoteFile(noteId, manifest(noteId)) { it.write(manifest.encodeToByteArray()) }
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
            GZIPInputStream(openNoteFile(noteId, file)).bufferedReader().use { it.readText() }
        }
    }

    suspend fun writeSnapshot(noteId: String, text: String) = locked(noteId) {
        dir(noteId).mkdirs()
        writeNoteFile(noteId, snapshotFile(noteId)) { out ->
            // Written in pieces: a long note's text is never copied whole into bytes.
            // Not closed: writeAtomically still syncs the file afterwards.
            val gzip = GZIPOutputStream(out, 64 * 1024)
            java.io.OutputStreamWriter(gzip, Charsets.UTF_8).let { it.write(text); it.flush() }
            gzip.finish()
        }
    }

    /** Stores the snapshot as gzip-compressed text already (the cloud's note file); it is kept as it is. */
    suspend fun writeCompressedSnapshot(noteId: String, compressed: ByteArray) = locked(noteId) {
        dir(noteId).mkdirs()
        writeNoteFile(noteId, snapshotFile(noteId)) { it.write(compressed) }
    }

    suspend fun copy(fromNoteId: String, toNoteId: String) = locked(fromNoteId) {
        val from = dir(fromNoteId)
        if (from.isDirectory) {
            from.copyRecursively(dir(toNoteId), overwrite = true)
            // A copy starts unsynced.
            snapshotFile(toNoteId).delete()
        }
    }

    /** Forgets what was last written for a note, before its files are replaced from outside (a backup). */
    suspend fun forget(noteId: String) = locked(noteId) {
        written.remove(noteId)
    }

    suspend fun delete(noteId: String) = locked(noteId) {
        dir(noteId).deleteRecursively()
        written.remove(noteId)
    }

    /**
     * Makes a note's files match its lock: encrypted with the device key when [sealed], plain
     * otherwise. It can stop at any point (reads take both forms) and simply runs again; files
     * already in the right form are left alone. A note with no files yet gets the marker, so its
     * first save is sealed too.
     */
    suspend fun setSealed(noteId: String, sealed: Boolean) = locked(noteId) {
        val dir = dir(noteId)
        val marker = sealedMarker(noteId)
        // Unlocking drops the marker only once every file is plain again, so no marker: nothing to do.
        if (!sealed && !marker.isFile) return@locked
        val sealing = sealing ?: error("No device key to seal notes with")
        if (sealed && !marker.isFile) {
            dir.mkdirs()
            // The marker first: from here on every save of this note is sealed.
            writeAtomically(marker) {}
        }
        // A save cut short leaves its temporary file, which may hold plain ink.
        (dir.listFiles().orEmpty().toList() + pagesDir(noteId).listFiles().orEmpty().toList())
            .filter { it.isFile && it.name.endsWith(".tmp") }
            .forEach { it.delete() }
        for (file in noteFiles(noteId)) {
            val isSealed = sealing.isSealed(head(file))
            if (isSealed == sealed) continue
            val name = nameOf(noteId, file)
            val plain = if (isSealed) sealing.open(file.readBytes(), name) else file.readBytes()
            writeAtomically(file) { it.write(if (sealed) sealing.seal(plain, name) else plain) }
        }
        if (!sealed) marker.delete()
    }

    /** Whether a note's folder is marked to be kept encrypted (see [setSealed]). */
    fun isSealed(noteId: String) = sealedMarker(noteId).isFile

    /** A note's ink file (as named in a backup) as plain bytes, whether it is kept sealed or not. */
    suspend fun plainBytes(noteId: String, file: File): ByteArray = locked(noteId) {
        openNoteFile(noteId, file).use { it.readBytes() }
    }

    private fun noteFiles(noteId: String): List<File> =
        listOf(manifest(noteId), snapshotFile(noteId)).filter { it.isFile } +
            pagesDir(noteId).listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".ink") }

    // The name a sealed file is bound to: its path inside the note's folder, so a copied note
    // (same names) still opens, while a file swapped for another one doesn't.
    private fun nameOf(noteId: String, file: File) = file.relativeTo(dir(noteId)).invariantSeparatorsPath

    private fun head(file: File): ByteArray = file.inputStream().use { input ->
        val head = ByteArray(HEAD_SIZE)
        var read = 0
        while (read < head.size) {
            val n = input.read(head, read, head.size - read)
            if (n < 0) break
            read += n
        }
        head.copyOf(read)
    }

    /** A note file's plain content, whichever form it is in. */
    private fun openNoteFile(noteId: String, file: File): InputStream {
        val sealing = sealing
        if (sealing == null || !sealing.isSealed(head(file))) return file.inputStream().buffered()
        return sealing.open(file.readBytes(), nameOf(noteId, file)).inputStream()
    }

    /** Writes a note file, encrypted when the note is marked sealed. */
    private fun writeNoteFile(noteId: String, file: File, write: (OutputStream) -> Unit) {
        if (!isSealed(noteId)) return writeAtomically(file, write)
        val sealing = sealing ?: error("No device key to seal notes with")
        val plain = ByteArrayOutputStream().also(write).toByteArray()
        writeAtomically(file) { it.write(sealing.seal(plain, nameOf(noteId, file))) }
    }

    private fun readManifest(noteId: String): InkDocument? {
        val file = manifest(noteId)
        if (!file.isFile) return null
        return InkSerializer.decode(openNoteFile(noteId, file).bufferedReader().use { it.readText() })
    }

    private fun readPage(noteId: String, pageId: String): List<InkStroke> {
        val file = pageFile(noteId, pageId)
        if (!file.isFile) return emptyList()
        return openNoteFile(noteId, file).use(InkPageCodec::read)
    }

    private fun writeAtomically(target: File, write: (java.io.OutputStream) -> Unit) {
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.outputStream().use { out ->
            out.buffered().let { write(it); it.flush() }
            out.fd.sync()
        }
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private companion object {
        const val SEALED_MARKER = "sealed"
        // Enough to tell a sealed file by its first bytes.
        const val HEAD_SIZE = 4
    }
}

/** Encrypts and decrypts a note's files on this device (the device key); see [InkStore.setSealed]. */
interface FileSealing {
    /** Whether a file starting with [head] is sealed. */
    fun isSealed(head: ByteArray): Boolean
    /** [plain] encrypted and bound to [name]. */
    fun seal(plain: ByteArray, name: String): ByteArray
    /** The plain bytes; throws when the key, the name or a single byte is wrong. */
    fun open(data: ByteArray, name: String): ByteArray
}
