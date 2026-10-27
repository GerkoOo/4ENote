package com.gerko.fourenote.data.sync

import com.gerko.fourenote.platform.gunzip
import com.gerko.fourenote.platform.gzip

/** The gzip wrapping of note files in the cloud (see NoteFile.toCloud on Android). */
object NoteGzip {
    /** The largest file a sync reads into memory; a bigger one (hostile or broken server) is refused. */
    const val MAX_BYTES = 256L * 1024 * 1024

    fun isGzip(bytes: ByteArray) = bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()

    fun toCloud(text: String): ByteArray = gzip(text.encodeToByteArray())

    /** The text of a cloud note file, compressed (this version) or not (older versions). */
    fun fromCloud(bytes: ByteArray): String =
        if (isGzip(bytes)) gunzip(bytes, MAX_BYTES).decodeToString() else bytes.decodeToString()
}
