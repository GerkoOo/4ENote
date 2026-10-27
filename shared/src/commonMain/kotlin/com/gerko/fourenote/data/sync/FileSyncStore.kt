package com.gerko.fourenote.data.sync

import com.gerko.fourenote.data.Note
import com.gerko.fourenote.data.isInCloud
import com.gerko.fourenote.platform.newLock
import com.gerko.fourenote.platform.nowMillis
import com.gerko.fourenote.platform.sha256
import com.gerko.fourenote.platform.toHex
import com.gerko.fourenote.platform.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okio.FileSystem
import okio.Path

/**
 * Notes kept as plain files, with everything sync needs (the iPad app's store; Android keeps them
 * in Room and InkStore). Under [root]:
 * ```
 * notes/<key>.json       the note's details (Note without its ink)
 * notes/<key>.ink        its ink (InkSerializer text)
 * sync/<key>/synced.gz   this device's last upload
 * sync/<key>/sent/       earlier uploads (see [recordSent]) and their "order" file
 * sync/<key>/peers/      other devices' files as last merged here
 * ```
 * The key is the note id, or a hash of it if the id isn't a safe file name.
 */
class FileSyncStore(
    private val root: Path,
    private val fs: FileSystem,
    private val clock: () -> Long = ::nowMillis,
    private val onProblem: (noteId: String, message: String) -> Unit = { _, _ -> },
) : SyncStore {
    private val lock = newLock()
    private val open = HashSet<String>()

    // ---- For the app ----

    /** Every note that isn't deleted, without its ink, newest first. */
    fun notes(): List<Note> = lock.withLock { metas().filter { !it.deleted } }.sortedByDescending { it.updatedAt }

    /** The note with its ink, or null. */
    fun get(id: String): Note? = lock.withLock { meta(id)?.takeIf { !it.deleted }?.let { it.copy(inkData = readInk(id)) } }

    /** Saves a note edited here: it uploads with the next sync. */
    fun save(note: Note) = lock.withLock {
        val old = meta(note.id)
        val now = clock()
        val updated = note.copy(
            // Never the same updatedAt twice: sync uses it to notice edits made while it ran.
            updatedAt = if (old != null && now <= old.updatedAt) old.updatedAt + 1 else now,
            dirty = true,
            deleted = false,
            // Sync bookkeeping is the store's, whatever the caller's copy says.
            remoteId = old?.remoteId,
            remoteVersion = old?.remoteVersion,
            remotePdfId = old?.remotePdfId,
            lastSyncedAt = old?.lastSyncedAt,
            rev = old?.rev ?: 0,
            syncedSnapshot = "",
        )
        writeInk(note.id, note.inkData)
        writeMeta(updated)
    }

    /** Deletes a note: at once if it never reached the cloud, else with the next sync. */
    fun delete(id: String) = lock.withLock {
        val note = meta(id) ?: return@withLock
        if (note.isInCloud) writeMeta(note.copy(deleted = true, dirty = true, updatedAt = clock())) else purgeNow(id)
    }

    /** The editor has the note open (sync leaves it alone) or has closed it. */
    fun setOpen(id: String, isOpen: Boolean) = lock.withLock { if (isOpen) open += id else open -= id }

    /** Another cloud was chosen: every note uploads there as new. */
    fun forgetCloud() = lock.withLock {
        for (note in metas()) {
            writeMeta(note.copy(remoteId = null, remoteVersion = null, remotePdfId = null, lastSyncedAt = null, dirty = true))
            fs.deleteRecursively(syncDir(note.id))
        }
    }

    // ---- SyncStore ----

    override suspend fun notesForSync(): List<Note> = lock.withLock { metas() }

    override suspend fun withContent(note: Note): Note = note.copy(inkData = lock.withLock { readInk(note.id) })

    override suspend fun syncedSnapshot(note: Note): String =
        lock.withLock { readBytes(syncDir(note.id) / SYNCED) }?.let { NoteGzip.fromCloud(it) }.orEmpty()

    override suspend fun sentSnapshot(note: Note, version: String): String? {
        if (version == note.remoteVersion) return syncedSnapshot(note).takeIf { it.isNotEmpty() }
        return lock.withLock { readBytes(sentDir(note.id) / sentName(version)) }?.let { NoteGzip.fromCloud(it) }
    }

    override suspend fun peerVersions(noteId: String): Map<String, String> = lock.withLock {
        val dir = syncDir(noteId) / PEERS
        (fs.listOrNull(dir) ?: emptyList()).filter { it.name.endsWith(".version") }.mapNotNull { file ->
            val lines = readText(file)?.split('\n') ?: return@mapNotNull null
            if (lines.size < 2) null else lines[0] to lines[1]
        }.toMap()
    }

    override suspend fun peerSnapshot(noteId: String, deviceId: String): String? =
        lock.withLock { readBytes(syncDir(noteId) / PEERS / (fileKey(deviceId) + ".gz")) }?.let { NoteGzip.fromCloud(it) }

    override suspend fun savePeer(noteId: String, deviceId: String, version: String, compressed: ByteArray) = lock.withLock {
        val dir = syncDir(noteId) / PEERS
        writeBytes(dir / (fileKey(deviceId) + ".gz"), compressed)
        // The version last: a crash in between leaves the old version, so the file is merged again.
        writeBytes(dir / (fileKey(deviceId) + ".version"), "$deviceId\n$version".encodeToByteArray())
    }

    override suspend fun forgetPeer(noteId: String, deviceId: String) = lock.withLock {
        val dir = syncDir(noteId) / PEERS
        fs.delete(dir / (fileKey(deviceId) + ".version"))
        fs.delete(dir / (fileKey(deviceId) + ".gz"))
    }

    override fun isOpen(noteId: String): Boolean = lock.withLock { noteId in open }

    override suspend fun insertFromCloud(note: Note) = lock.withLock {
        writeInk(note.id, note.inkData)
        writeMeta(note.copy(syncedSnapshot = ""))
    }

    override suspend fun replaceIfUnchanged(note: Note, expectedUpdatedAt: Long, dirty: Boolean): Boolean = lock.withLock {
        val current = meta(note.id)
        if (current == null || current.updatedAt != expectedUpdatedAt) return@withLock false
        writeInk(note.id, note.inkData)
        writeMeta(
            current.copy(
                title = note.title,
                createdAt = note.createdAt,
                updatedAt = note.updatedAt,
                dirty = dirty,
                folderId = note.folderId,
                locked = note.locked,
                rev = note.rev,
                // A PDF copy another device made, kept if this device has none yet.
                remotePdfId = current.remotePdfId ?: note.remotePdfId,
            ),
        )
        true
    }

    override suspend fun markSynced(noteId: String, file: RemoteFile, pdfId: String?, snapshot: ByteArray, expectedUpdatedAt: Long, rev: Long) =
        lock.withLock {
            writeBytes(syncDir(noteId) / SYNCED, snapshot)
            recordSent(noteId, file.version, snapshot)
            val current = meta(noteId) ?: return@withLock
            writeMeta(
                current.copy(
                    remoteId = file.id,
                    remotePdfId = pdfId,
                    remoteVersion = file.version,
                    lastSyncedAt = clock(),
                    rev = rev,
                    // Edited while the upload was in flight: that edit goes up with the next sync.
                    dirty = current.updatedAt != expectedUpdatedAt,
                ),
            )
        }

    override suspend fun purge(noteId: String) = lock.withLock { purgeNow(noteId) }

    override suspend fun purgeIfUnchanged(noteId: String, expectedUpdatedAt: Long): Boolean = lock.withLock {
        val current = meta(noteId)
        if (current == null || current.dirty || current.updatedAt != expectedUpdatedAt) return@withLock false
        purgeNow(noteId)
        true
    }

    override fun reportProblem(noteId: String, message: String) = onProblem(noteId, message)

    // ---- Files ----

    private fun purgeNow(id: String) {
        fs.delete(notesDir / (fileKey(id) + ".json"))
        fs.delete(notesDir / (fileKey(id) + ".ink"))
        fs.deleteRecursively(syncDir(id))
    }

    /**
     * Keeps this device's upload at [version]: another device that merged it may name it as the
     * base of its next file. Newest [SENT_MIN] always, then any under a day old, up to [SENT_MAX]
     * and [SENT_MAX_BYTES] (as Android's InkStore).
     */
    private fun recordSent(noteId: String, version: String, compressed: ByteArray) {
        val dir = sentDir(noteId)
        val now = clock()
        val name = sentName(version)
        writeBytes(dir / name, compressed)
        val entries = listOf(name to now) + readOrder(dir).filter { it.first != name }
        val keep = ArrayList<Pair<String, Long>>()
        var bytes = 0L
        for ((i, entry) in entries.withIndex()) {
            val size = fs.metadataOrNull(dir / entry.first)?.size ?: continue
            bytes += size
            if (i < SENT_MIN || (i < SENT_MAX && now - entry.second < SENT_KEEP_MS && bytes <= SENT_MAX_BYTES)) keep += entry
            else fs.delete(dir / entry.first)
        }
        writeBytes(dir / ORDER, keep.joinToString("\n") { "${it.first} ${it.second}" }.encodeToByteArray())
    }

    private fun readOrder(dir: Path): List<Pair<String, Long>> =
        readText(dir / ORDER)?.lines()?.mapNotNull { line ->
            val parts = line.split(' ')
            if (parts.size == 2) parts[0] to (parts[1].toLongOrNull() ?: 0L) else null
        }.orEmpty()

    private val notesDir = root / "notes"

    private fun syncDir(id: String) = root / "sync" / fileKey(id)

    private fun sentDir(id: String) = syncDir(id) / "sent"

    private fun sentName(version: String) = sha256(version.encodeToByteArray()).copyOf(12).toHex() + ".gz"

    private fun metas(): List<Note> =
        (fs.listOrNull(notesDir) ?: emptyList()).filter { it.name.endsWith(".json") }.mapNotNull { readMeta(it) }

    private fun meta(id: String): Note? = readMeta(notesDir / (fileKey(id) + ".json"))

    private fun readInk(id: String): String = readText(notesDir / (fileKey(id) + ".ink")).orEmpty()

    private fun writeInk(id: String, ink: String) = writeBytes(notesDir / (fileKey(id) + ".ink"), ink.encodeToByteArray())

    private fun writeMeta(note: Note) {
        val json = buildJsonObject {
            put("id", note.id)
            put("title", note.title)
            put("createdAt", note.createdAt)
            put("updatedAt", note.updatedAt)
            put("remoteId", note.remoteId)
            put("lastSyncedAt", note.lastSyncedAt)
            put("deleted", note.deleted)
            put("dirty", note.dirty)
            put("remoteVersion", note.remoteVersion)
            put("remotePdfId", note.remotePdfId)
            put("folderId", note.folderId)
            put("trashedAt", note.trashedAt)
            put("locked", note.locked)
            put("rev", note.rev)
        }
        writeBytes(notesDir / (fileKey(note.id) + ".json"), json.toString().encodeToByteArray())
    }

    private fun readMeta(file: Path): Note? {
        val text = readText(file) ?: return null
        return try {
            val o = Json.parseToJsonElement(text).jsonObject
            Note(
                id = o.str("id") ?: return null,
                title = o.str("title").orEmpty(),
                createdAt = o.long("createdAt") ?: 0L,
                updatedAt = o.long("updatedAt") ?: 0L,
                remoteId = o.str("remoteId"),
                lastSyncedAt = o.long("lastSyncedAt"),
                deleted = o.bool("deleted") ?: false,
                dirty = o.bool("dirty") ?: true,
                remoteVersion = o.str("remoteVersion"),
                remotePdfId = o.str("remotePdfId"),
                folderId = o.str("folderId"),
                trashedAt = o.long("trashedAt"),
                locked = o.bool("locked") ?: false,
                rev = o.long("rev") ?: 0L,
            )
        } catch (e: Exception) {
            onProblem(file.name, "Unreadable note file: ${e.message}")
            null
        }
    }

    private fun JsonObject.str(name: String) = (this[name] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.long(name: String) = (this[name] as? JsonPrimitive)?.longOrNull
    private fun JsonObject.bool(name: String) = (this[name] as? JsonPrimitive)?.booleanOrNull

    private fun readBytes(file: Path): ByteArray? = if (fs.exists(file)) fs.read(file) { readByteArray() } else null

    private fun readText(file: Path): String? = readBytes(file)?.decodeToString()

    /** Written whole or not at all: a temporary file, then an atomic rename. */
    private fun writeBytes(file: Path, bytes: ByteArray) {
        file.parent?.let { fs.createDirectories(it) }
        val temp = file.parent!! / ".${file.name}.part"
        fs.write(temp) { write(bytes) }
        fs.atomicMove(temp, file)
    }

    private companion object {
        const val SYNCED = "synced.gz"
        const val PEERS = "peers"
        const val ORDER = "order"
        const val SENT_MIN = 3
        const val SENT_MAX = 30
        const val SENT_KEEP_MS = 24 * 60 * 60 * 1000L
        const val SENT_MAX_BYTES = 64L * 1024 * 1024

        private val SAFE = Regex("[A-Za-z0-9_-]{1,64}")

        fun fileKey(id: String) = if (SAFE.matches(id)) id else "h" + sha256(id.encodeToByteArray()).copyOf(16).toHex()
    }
}
