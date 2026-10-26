package com.gerko.fourenote.data

import kotlin.random.Random

/**
 * A random id in the usual UUID text form (8-4-4-4-12 hex digits, version 4). Common code has no
 * java.util.UUID, and ids only need to be unique, not standard-compliant.
 */
fun newId(): String {
    val bytes = Random.nextBytes(16)
    bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
    bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
    val hex = "0123456789abcdef"
    val out = StringBuilder(36)
    for (i in bytes.indices) {
        if (i == 4 || i == 6 || i == 8 || i == 10) out.append('-')
        val v = bytes[i].toInt() and 0xFF
        out.append(hex[v ushr 4]).append(hex[v and 0x0F])
    }
    return out.toString()
}
