package com.gerko.fourenote.data.sync

import java.io.File
import java.io.InputStream

/** Data to upload, read as a stream so big files (like a long note's PDF) never have to fit in memory. */
class UploadContent(val size: Long, val open: () -> InputStream) {
    companion object {
        fun of(bytes: ByteArray) = UploadContent(bytes.size.toLong()) { bytes.inputStream() }
        fun of(file: File) = UploadContent(file.length()) { file.inputStream() }
    }
}

/** A file in the cloud folder that 4ENote syncs with. */
data class RemoteFile(
    val id: String,
    val name: String,
    /** Changes whenever the file's content changes (an ETag, revision or hash). */
    val version: String,
)

/**
 * One cloud storage service. Implementations work inside a single app folder
 * (for example "4ENote" in the user's Google Drive) and never touch other files.
 * Google Drive and OneDrive plug in here; [LocalFolderCloudProvider] stands in
 * for them while the real connections are being set up.
 */
interface CloudProvider {
    /** Lists every file in the app folder. */
    suspend fun listFiles(): List<RemoteFile>

    suspend fun download(remoteId: String): ByteArray

    /**
     * Creates a file, or replaces the content and name of [remoteId] when given.
     * Returns the file as it now exists in the cloud.
     */
    suspend fun upload(name: String, mimeType: String, content: UploadContent, remoteId: String?): RemoteFile

    /** Deletes a file; deleting one that is already gone is not an error. */
    suspend fun delete(remoteId: String)
}

/** The largest file a sync reads into memory; a bigger one (hostile or broken server) is refused. */
const val MAX_DOWNLOAD_BYTES = 256L * 1024 * 1024

/** Reads the whole stream, but stops with an error past [limit] bytes instead of running out of memory. */
fun InputStream.readLimited(limit: Long = MAX_DOWNLOAD_BYTES): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > limit) throw java.io.IOException("File too large (over ${limit / (1024 * 1024)} MB)")
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
