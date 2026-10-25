package com.gerko.fourenote.data.backup

import com.gerko.fourenote.data.SafeName
import com.gerko.fourenote.data.Folder
import com.gerko.fourenote.data.FolderFile
import com.gerko.fourenote.data.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * All notes in one file to keep somewhere safe, and back again. A backup is a zip:
 *
 * ```
 * backup.json                      what this is, and when it was made
 * notes.json                       each note's title, dates and folder
 * folders.json                     the folders (see FolderFile)
 * ink/<noteId>/manifest.json       the note's pages (see InkStore)
 * ink/<noteId>/pages/<page>.ink
 * attachments/<noteId>/<file>      its pictures and PDF pages
 * ```
 *
 * Restoring never loses newer work: a note comes back if it is missing here (or was deleted),
 * or if the backup's copy is newer; otherwise the note here is kept. Restored notes count as
 * edited, so they sync.
 */
class Backup(
    private val inkRoot: File,
    private val attachmentsRoot: File,
    private val notes: suspend () -> List<Note>,
    private val folders: suspend () -> List<Folder>,
    private val saveNote: suspend (Note) -> Unit,
    private val saveFolders: suspend (List<Folder>) -> Unit,
    /** Forgets what the ink store keeps in memory for a note, before its files are replaced. */
    private val forgetInk: suspend (String) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Restored(val added: Int, val updated: Int, val kept: Int, val folders: Int)

    /** Writes every note and folder to [out]; returns how many notes. */
    suspend fun export(out: OutputStream, appVersion: String): Int = withContext(Dispatchers.IO) {
        val all = notes().filterNot { it.deleted }
        val zip = ZipOutputStream(out.buffered())
        fun text(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.encodeToByteArray())
            zip.closeEntry()
        }
        text("backup.json", JSONObject().put("format", FORMAT).put("v", VERSION).put("created", clock()).put("app", appVersion).toString())
        text(
            "notes.json",
            JSONArray().apply {
                all.forEach { n ->
                    put(
                        JSONObject().put("id", n.id).put("title", n.title).put("createdAt", n.createdAt)
                            .put("updatedAt", n.updatedAt).put("folder", n.folderId ?: JSONObject.NULL)
                            .put("locked", n.locked),
                    )
                }
            }.toString(),
        )
        text("folders.json", FolderFile.encode(folders().filterNot { it.deleted }))
        for (note in all) {
            val ink = File(inkRoot, note.id)
            File(ink, "manifest.json").takeIf { it.isFile }?.let { addFile(zip, "ink/${note.id}/manifest.json", it) }
            File(ink, "pages").listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".ink") }
                .forEach { addFile(zip, "ink/${note.id}/pages/${it.name}", it) }
            File(attachmentsRoot, note.id).listFiles().orEmpty().filter { it.isFile && !it.name.startsWith(".") }
                .forEach { addFile(zip, "attachments/${note.id}/${it.name}", it) }
        }
        zip.finish()
        zip.flush()
        all.size
    }

    /** Brings notes and folders back from a backup read from [input]. */
    suspend fun restore(input: InputStream): Restored = withContext(Dispatchers.IO) {
        val zip = ZipInputStream(input.buffered())
        val here = notes().associateBy { it.id }
        var backed: Map<String, Note>? = null
        var backedFolders: List<Folder> = emptyList()
        // The notes taken from the backup (their files are replaced as they come).
        val taking = HashSet<String>()
        val started = HashSet<String>()
        var sawHeader = false
        while (true) {
            val entry = zip.nextEntry ?: break
            val name = entry.name
            when {
                entry.isDirectory -> Unit
                name == "backup.json" -> {
                    val header = JSONObject(zip.readBytes().decodeToString())
                    require(header.optString("format") == FORMAT) { "This isn't a 4ENote backup" }
                    sawHeader = true
                }
                name == "notes.json" -> {
                    val array = JSONArray(zip.readBytes().decodeToString())
                    backed = List(array.length()) { i ->
                        val o = array.getJSONObject(i)
                        Note(
                            id = o.getString("id"),
                            title = o.optString("title"),
                            createdAt = o.optLong("createdAt"),
                            updatedAt = o.optLong("updatedAt"),
                            folderId = if (o.isNull("folder")) null else o.optString("folder").ifEmpty { null },
                            locked = o.optBoolean("locked", false),
                        )
                    }.associateBy { it.id }
                    for ((id, note) in backed!!) {
                        val local = here[id]
                        // The id names the note's folders; a backup is a file from anywhere, so check it.
                        if (!SafeName.isSafeFromCloud(id)) continue
                        if (local == null || local.deleted || note.updatedAt > local.updatedAt) taking += id
                    }
                }
                name == "folders.json" -> backedFolders = FolderFile.decode(zip.readBytes().decodeToString())
                else -> {
                    val target = targetFor(name) ?: continue
                    val (noteId, file) = target
                    if (noteId !in taking) continue
                    if (started.add(noteId)) {
                        // A clean start for this note's ink, so no page left over from the version here remains.
                        forgetInk(noteId)
                        File(inkRoot, noteId).deleteRecursively()
                    }
                    file.parentFile?.mkdirs()
                    val temp = File(file.parentFile, ".${file.name}.part")
                    temp.outputStream().use { zip.copyTo(it) }
                    if (!temp.renameTo(file)) {
                        file.delete()
                        temp.renameTo(file)
                    }
                }
            }
        }
        require(sawHeader && backed != null) { "This isn't a 4ENote backup" }
        val now = clock()
        var added = 0
        var updated = 0
        for (id in taking) {
            val note = backed!!.getValue(id)
            val local = here[id]
            // Kept as an edit (dirty, newer than before) so sync takes it, keeping this device's
            // link to the cloud copy so it is updated rather than duplicated.
            val restored = (local ?: note).copy(
                title = note.title,
                createdAt = note.createdAt,
                updatedAt = maxOf(now, note.updatedAt),
                folderId = note.folderId,
                locked = note.locked,
                deleted = false,
                dirty = true,
                // Restoring brings a note back out of Deleted notes too.
                trashedAt = null,
            )
            saveNote(restored)
            if (local == null || local.deleted) added++ else updated++
        }
        // Folders: the newer of each; any a restored note is in comes back if it was deleted here.
        val localFolders = folders()
        var merged = FolderFile.merge(localFolders, backedFolders)
        val needed = taking.mapNotNull { backed!![it]?.folderId }.toSet()
        merged = merged.map { if (it.deleted && it.id in needed) it.copy(deleted = false, updatedAt = now) else it }
        val newFolders = merged.count { f -> localFolders.none { it.id == f.id && !it.deleted } && !f.deleted }
        if (merged != localFolders.sortedBy { it.id }) saveFolders(merged)
        Restored(added, updated, backed!!.size - added - updated, newFolders)
    }

    // Where a file entry goes, or null for anything unexpected (never outside the note folders).
    private fun targetFor(name: String): Pair<String, File>? {
        val parts = name.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." || it.contains('\\') }) return null
        return when {
            parts.size == 3 && parts[0] == "ink" && parts[2] == "manifest.json" -> parts[1] to File(File(inkRoot, parts[1]), "manifest.json")
            parts.size == 4 && parts[0] == "ink" && parts[2] == "pages" && parts[3].endsWith(".ink") ->
                parts[1] to File(File(File(inkRoot, parts[1]), "pages"), parts[3])
            parts.size == 3 && parts[0] == "attachments" -> parts[1] to File(File(attachmentsRoot, parts[1]), parts[2])
            else -> null
        }
    }

    private fun addFile(zip: ZipOutputStream, name: String, file: File) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private companion object {
        const val FORMAT = "4enote-backup"
        const val VERSION = 1
    }
}
