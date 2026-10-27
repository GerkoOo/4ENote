package com.gerko.fourenote.data.scan

import com.gerko.fourenote.data.newId
import com.gerko.fourenote.platform.nowMillis
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import com.gerko.fourenote.platform.newLock
import com.gerko.fourenote.platform.withLock

/**
 * Scans taken from the home page before the note they belong in has reached this device. They
 * wait here and in the cloud (see SyncEngine) until a device that has the note puts each one into
 * its photo frame: "Photo 2" goes into the frame named Photo 2, and a scan for any frame goes
 * into the first frame still empty.
 *
 * ```
 * <root>/<frame>_<takenAt>_<random>.jpg  a waiting scan; frame 0 means any frame
 * <root>/.synced/<name>                  the scan is in the cloud too
 * <root>/.used/<name>                    placed in a frame here; to be removed from the cloud
 * ```
 */
class ScanInbox(private val root: Path, private val fs: FileSystem = FileSystem.SYSTEM) {
    private val synced = root / ".synced"
    private val used = root / ".used"
    private val lock = newLock()

    data class Scan(val name: String, val file: Path) {
        /** The frame number it is meant for ("Photo 2" is 2), or 0 for any frame. */
        val frame: Int get() = name.substringBefore('_').toIntOrNull() ?: 0
        val takenAt: Long get() = name.split('_').getOrNull(1)?.toLongOrNull() ?: 0L
    }

    /** Waiting scans, oldest first. */
    fun pending(): List<Scan> = lock.withLock {
        (fs.listOrNull(root) ?: emptyList())
            .filter { it.name.endsWith(EXTENSION) && !it.name.startsWith(".") && fs.metadataOrNull(it)?.isRegularFile == true }
            .map { Scan(it.name, it) }
            .sortedWith(compareBy({ it.takenAt }, { it.name }))
    }

    /** Keeps a scan (JPEG bytes) for frame [frame] (0: any). */
    fun add(jpeg: ByteArray, frame: Int, now: Long = nowMillis()): Scan = lock.withLock {
        val name = "${frame.coerceAtLeast(0)}_${now}_${newId().take(8)}$EXTENSION"
        write(name, jpeg)
        return Scan(name, root / name)
    }

    /** A scan downloaded from the cloud. */
    fun saveFromCloud(name: String, jpeg: ByteArray): Unit = lock.withLock {
        if (!isValidName(name) || fs.exists(used / name)) return
        write(name, jpeg)
        markSynced(name)
    }

    /** Put into a frame here: gone from the inbox, and from the cloud with the next sync. */
    fun markUsed(name: String): Unit = lock.withLock {
        fs.delete(root / name)
        fs.delete(synced / name)
        fs.createDirectories(used)
        fs.write(used / name) {}
    }

    /** Deleted by the user before it was used. Removed from the cloud like a used one. */
    fun discard(name: String) = markUsed(name)

    fun used(): List<String> = lock.withLock { fs.listOrNull(used)?.map { it.name }.orEmpty() }

    fun forgetUsed(name: String): Unit = lock.withLock {
        fs.delete(used / name)
    }

    fun isSynced(name: String): Boolean = lock.withLock { fs.exists(synced / name) }

    fun markSynced(name: String): Unit = lock.withLock {
        fs.createDirectories(synced)
        fs.write(synced / name) {}
    }

    /** Used on another device (it has left the cloud): no longer waiting here either. */
    fun removedElsewhere(name: String): Unit = lock.withLock {
        fs.delete(root / name)
        fs.delete(synced / name)
    }

    private fun write(name: String, bytes: ByteArray) {
        fs.createDirectories(root)
        val temp = root / ".$name.part"
        fs.write(temp) { write(bytes) }
        fs.atomicMove(temp, root / name)
    }

    companion object {
        const val EXTENSION = ".jpg"

        /** Cloud files of the inbox are named with this in front of the scan's name. */
        const val CLOUD_PREFIX = "scan-inbox_"

        fun isValidName(name: String) = Regex("""\d+_\d+_[0-9a-f-]+\.jpg""").matches(name)

        /** The frame number in a frame's name: "Photo 2" is 2. */
        fun frameNumber(label: String): Int? = label.removePrefix("Photo ").trim().toIntOrNull()
    }
}
