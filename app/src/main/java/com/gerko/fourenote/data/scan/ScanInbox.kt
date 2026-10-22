package com.gerko.fourenote.data.scan

import java.io.File
import java.util.UUID

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
class ScanInbox(private val root: File) {
    private val synced = File(root, ".synced")
    private val used = File(root, ".used")

    data class Scan(val name: String, val file: File) {
        /** The frame number it is meant for ("Photo 2" is 2), or 0 for any frame. */
        val frame: Int get() = name.substringBefore('_').toIntOrNull() ?: 0
        val takenAt: Long get() = name.split('_').getOrNull(1)?.toLongOrNull() ?: 0L
    }

    /** Waiting scans, oldest first. */
    @Synchronized
    fun pending(): List<Scan> = root.listFiles().orEmpty()
        .filter { it.isFile && it.name.endsWith(EXTENSION) && !it.name.startsWith(".") }
        .map { Scan(it.name, it) }
        .sortedWith(compareBy({ it.takenAt }, { it.name }))

    /** Keeps a scan (JPEG bytes) for frame [frame] (0: any). */
    @Synchronized
    fun add(jpeg: ByteArray, frame: Int, now: Long = System.currentTimeMillis()): Scan {
        val name = "${frame.coerceAtLeast(0)}_${now}_${UUID.randomUUID().toString().take(8)}$EXTENSION"
        write(name, jpeg)
        return Scan(name, File(root, name))
    }

    /** A scan downloaded from the cloud. */
    @Synchronized
    fun saveFromCloud(name: String, jpeg: ByteArray) {
        if (!isValidName(name) || File(used, name).exists()) return
        write(name, jpeg)
        markSynced(name)
    }

    /** Put into a frame here: gone from the inbox, and from the cloud with the next sync. */
    @Synchronized
    fun markUsed(name: String) {
        File(root, name).delete()
        File(synced, name).delete()
        used.mkdirs()
        File(used, name).createNewFile()
    }

    /** Deleted by the user before it was used. Removed from the cloud like a used one. */
    fun discard(name: String) = markUsed(name)

    @Synchronized
    fun used(): List<String> = used.list().orEmpty().toList()

    @Synchronized
    fun forgetUsed(name: String) {
        File(used, name).delete()
    }

    @Synchronized
    fun isSynced(name: String) = File(synced, name).exists()

    @Synchronized
    fun markSynced(name: String) {
        synced.mkdirs()
        File(synced, name).createNewFile()
    }

    /** Used on another device (it has left the cloud): no longer waiting here either. */
    @Synchronized
    fun removedElsewhere(name: String) {
        File(root, name).delete()
        File(synced, name).delete()
    }

    private fun write(name: String, bytes: ByteArray) {
        root.mkdirs()
        val temp = File(root, ".$name.part")
        temp.writeBytes(bytes)
        temp.renameTo(File(root, name))
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
