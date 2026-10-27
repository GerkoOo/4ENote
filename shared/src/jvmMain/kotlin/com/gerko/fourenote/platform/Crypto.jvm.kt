package com.gerko.fourenote.platform

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private val random = SecureRandom()

actual fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

actual fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

private fun gcm(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray): Cipher =
    Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        updateAAD(aad)
    }

actual fun aesGcmSeal(key: ByteArray, nonce: ByteArray, aad: ByteArray, plain: ByteArray): ByteArray =
    gcm(Cipher.ENCRYPT_MODE, key, nonce, aad).doFinal(plain)

actual fun aesGcmOpen(key: ByteArray, nonce: ByteArray, aad: ByteArray, sealed: ByteArray): ByteArray =
    try {
        gcm(Cipher.DECRYPT_MODE, key, nonce, aad).doFinal(sealed)
    } catch (e: AEADBadTagException) {
        throw BadTagException()
    }

actual fun pbkdf2Sha256(password: CharArray, salt: ByteArray, iterations: Int, keyBytes: Int): ByteArray {
    val spec = PBEKeySpec(password, salt, iterations, keyBytes * 8)
    try {
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    } finally {
        spec.clearPassword()
    }
}

actual fun gzip(data: ByteArray): ByteArray {
    val out = ByteArrayOutputStream(data.size / 4 + 64)
    GZIPOutputStream(out, 64 * 1024).use { it.write(data) }
    return out.toByteArray()
}

actual fun gzipText(write: (Appendable) -> Unit): ByteArray {
    val bytes = ByteArrayOutputStream(64 * 1024)
    java.io.BufferedWriter(java.io.OutputStreamWriter(GZIPOutputStream(bytes, 64 * 1024), Charsets.UTF_8), 64 * 1024).use(write)
    return bytes.toByteArray()
}

actual fun gunzip(data: ByteArray, limit: Long): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    GZIPInputStream(data.inputStream(), 64 * 1024).use { input ->
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw IOException("File too large (over ${limit / (1024 * 1024)} MB)")
            out.write(buffer, 0, read)
        }
    }
    return out.toByteArray()
}
