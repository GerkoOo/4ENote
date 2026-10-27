package com.gerko.fourenote.data.sync

import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.Source
import okio.SYSTEM

/** Data to upload, read as a stream so big files (like a long note's PDF) never have to fit in memory. */
class UploadContent(val size: Long, val open: () -> Source) {
    companion object {
        fun of(bytes: ByteArray) = UploadContent(bytes.size.toLong()) { Buffer().write(bytes) }
        fun of(file: Path) = UploadContent(FileSystem.SYSTEM.metadata(file).size ?: 0L) { FileSystem.SYSTEM.source(file) }
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
const val MAX_DOWNLOAD_BYTES = NoteGzip.MAX_BYTES
