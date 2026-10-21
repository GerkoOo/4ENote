package com.gerko.fourenote.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * A "cloud" that is just a folder. Used to try out and test sync before a real
 * service is connected; several devices can be simulated by pointing several
 * sync engines at the same folder. Files are stored under their names, so the
 * remote id is the file name.
 */
class LocalFolderCloudProvider(private val folder: File) : CloudProvider {
    override suspend fun listFiles(): List<RemoteFile> = withContext(Dispatchers.IO) {
        folder.mkdirs()
        folder.listFiles().orEmpty().filter { it.isFile && !it.name.startsWith(".") }.map { it.toRemoteFile() }
    }

    override suspend fun download(remoteId: String): ByteArray = withContext(Dispatchers.IO) {
        File(folder, remoteId).readBytes()
    }

    override suspend fun upload(
        name: String,
        mimeType: String,
        content: UploadContent,
        remoteId: String?,
    ): RemoteFile = withContext(Dispatchers.IO) {
        folder.mkdirs()
        if (remoteId != null && remoteId != name) File(folder, remoteId).delete()
        val file = File(folder, name)
        // Write then rename, so a reader never sees a half-written file.
        val temp = File(folder, ".$name.tmp")
        content.open().use { input -> temp.outputStream().use { input.copyTo(it) } }
        if (!temp.renameTo(file)) {
            file.delete()
            check(temp.renameTo(file)) { "Could not write $name" }
        }
        file.toRemoteFile()
    }

    override suspend fun delete(remoteId: String) {
        withContext(Dispatchers.IO) { File(folder, remoteId).delete() }
    }

    private fun File.toRemoteFile(): RemoteFile {
        // Hash as a stream: reading whole files into memory would not scale to big PDFs.
        val sha = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                sha.update(buffer, 0, read)
            }
        }
        val digest = sha.digest()
        return RemoteFile(id = name, name = name, version = digest.joinToString("") { "%02x".format(it) })
    }
}
