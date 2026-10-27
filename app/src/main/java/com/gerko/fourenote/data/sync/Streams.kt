package com.gerko.fourenote.data.sync

import okio.buffer
import java.io.InputStream

/** The upload as a Java stream, for the Android providers' HTTP clients. */
fun UploadContent.openStream(): InputStream = open().buffer().inputStream()

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
