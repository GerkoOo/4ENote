package com.gerko.fourenote.platform

/**
 * The few primitives that encryption and sync need from the platform: the JDK on Android,
 * CommonCrypto, zlib and CryptoKit on the iPad. Same bytes in, same bytes out on both
 * (see [CryptoSelfTest]).
 */

/** Thrown when an AES-GCM tag doesn't match: wrong key, wrong context, or a changed byte. */
class BadTagException(message: String = "Wrong key or changed data") : Exception(message)

expect fun sha256(data: ByteArray): ByteArray

expect fun secureRandomBytes(size: Int): ByteArray

/** AES-GCM with a 128-bit tag: the ciphertext followed by the tag. */
expect fun aesGcmSeal(key: ByteArray, nonce: ByteArray, aad: ByteArray, plain: ByteArray): ByteArray

/** The plain bytes of [aesGcmSeal]'s output; throws [BadTagException] when anything differs. */
expect fun aesGcmOpen(key: ByteArray, nonce: ByteArray, aad: ByteArray, sealed: ByteArray): ByteArray

/** PBKDF2-HMAC-SHA256 over the UTF-8 bytes of [password]. */
expect fun pbkdf2Sha256(password: CharArray, salt: ByteArray, iterations: Int, keyBytes: Int): ByteArray

/** A gzip file (one member) holding [data]. */
expect fun gzip(data: ByteArray): ByteArray

/** Text written by [write] as UTF-8, gzip-compressed; on the JVM without holding the text whole. */
expect fun gzipText(write: (Appendable) -> Unit): ByteArray

/** The content of a gzip file; throws when it is broken or would be over [limit] bytes. */
expect fun gunzip(data: ByteArray, limit: Long): ByteArray

fun ByteArray.toHex(): String {
    val digits = "0123456789abcdef"
    val out = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xff
        out.append(digits[v shr 4]).append(digits[v and 0xf])
    }
    return out.toString()
}

fun hexToBytes(hex: String): ByteArray = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
