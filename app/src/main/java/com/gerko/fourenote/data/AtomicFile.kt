package com.gerko.fourenote.data

import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Writes a file so a crash never leaves it half-written: the content goes to a temporary
 * file next to it, is flushed to the disk, and only then moved into place in one step.
 * A failed write leaves the old file as it was and removes the temporary file.
 */
fun writeAtomically(target: File, write: (OutputStream) -> Unit) {
    // Starts with a dot: sync and backups skip such files, so a half-written one never leaves.
    val temp = File(target.parentFile, ".${target.name}.tmp")
    try {
        temp.outputStream().use { out ->
            out.buffered().let { write(it); it.flush() }
            out.fd.sync()
        }
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (e: Throwable) {
        temp.delete()
        throw e
    }
}
