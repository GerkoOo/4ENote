package com.gerko.fourenote.data.sync

/**
 * The shape of a device id: the name of the cloud files a device writes (see [NoteFileFormat]).
 * Shared, so the iPad app reads the Android app's files and the other way round.
 */
object DeviceIds {
    const val LENGTH = 8
    const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

    fun isValid(id: String) = id.length == LENGTH && id.all { it in ALPHABET }

    /** A new random id (the iPad's; Android has DeviceIdentity.newId). */
    fun random(): String {
        val out = StringBuilder(LENGTH)
        while (out.length < LENGTH) {
            for (b in com.gerko.fourenote.platform.secureRandomBytes(LENGTH * 2)) {
                val v = b.toInt() and 0xff
                // 252 = 7 x 36: higher bytes are skipped so every character is equally likely.
                if (v < 252 && out.length < LENGTH) out.append(ALPHABET[v % ALPHABET.length])
            }
        }
        return out.toString()
    }
}
