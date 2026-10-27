@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.gerko.fourenote.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CCKeyDerivationPBKDF
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.kCCPBKDF2
import platform.CoreCrypto.kCCPRFHmacAlgSHA256
import platform.CoreCrypto.kCCSuccess
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault
import platform.posix.memcpy
import platform.zlib.Z_DEFAULT_COMPRESSION
import platform.zlib.Z_DEFAULT_STRATEGY
import platform.zlib.Z_DEFLATED
import platform.zlib.Z_FINISH
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.ZLIB_VERSION
import platform.zlib.deflate
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2_
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream

/**
 * AES-GCM is only public in Swift (CryptoKit), so the iPad app hands an implementation in at
 * start (FourENoteApp.swift sets [IosCrypto.aesGcm]).
 */
interface AesGcmBridge {
    /** The ciphertext followed by the 16-byte tag, or null when the key or nonce size is wrong. */
    fun seal(key: NSData, nonce: NSData, aad: NSData, plain: NSData): NSData?

    /** The plain bytes, or null when the tag doesn't match. */
    fun open(key: NSData, nonce: NSData, aad: NSData, sealed: NSData): NSData?
}

object IosCrypto {
    var aesGcm: AesGcmBridge? = null
}

private fun bridge() = checkNotNull(IosCrypto.aesGcm) { "AES-GCM not installed (see FourENoteApp.swift)" }

private inline fun <R> ByteArray.withPointer(block: (CPointer<ByteVar>?) -> R): R =
    if (isEmpty()) block(null) else usePinned { block(it.addressOf(0)) }

private fun ByteArray.toNSData(): NSData = withPointer { NSData.create(bytes = it, length = size.convert()) }

private fun NSData.toByteArray(): ByteArray {
    val out = ByteArray(length.toInt())
    if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

actual fun sha256(data: ByteArray): ByteArray {
    val out = ByteArray(32)
    data.withPointer { input ->
        out.usePinned { CC_SHA256(input, data.size.convert(), it.addressOf(0).reinterpret<UByteVar>()) }
    }
    return out
}

actual fun secureRandomBytes(size: Int): ByteArray {
    val out = ByteArray(size)
    if (size > 0) {
        val status = out.usePinned { SecRandomCopyBytes(kSecRandomDefault, size.convert(), it.addressOf(0)) }
        check(status == errSecSuccess) { "No random bytes ($status)" }
    }
    return out
}

actual fun aesGcmSeal(key: ByteArray, nonce: ByteArray, aad: ByteArray, plain: ByteArray): ByteArray =
    checkNotNull(bridge().seal(key.toNSData(), nonce.toNSData(), aad.toNSData(), plain.toNSData())) { "AES-GCM failed" }.toByteArray()

actual fun aesGcmOpen(key: ByteArray, nonce: ByteArray, aad: ByteArray, sealed: ByteArray): ByteArray =
    (bridge().open(key.toNSData(), nonce.toNSData(), aad.toNSData(), sealed.toNSData()) ?: throw BadTagException()).toByteArray()

actual fun pbkdf2Sha256(password: CharArray, salt: ByteArray, iterations: Int, keyBytes: Int): ByteArray {
    val out = ByteArray(keyBytes)
    val utf8 = password.concatToString().encodeToByteArray()
    try {
        val status = utf8.withPointer { pw ->
            salt.withPointer { s ->
                out.usePinned { o ->
                    CCKeyDerivationPBKDF(
                        kCCPBKDF2.convert(), pw, utf8.size.convert(),
                        s?.reinterpret<UByteVar>(), salt.size.convert(),
                        kCCPRFHmacAlgSHA256.convert(), iterations.convert(),
                        o.addressOf(0).reinterpret<UByteVar>(), keyBytes.convert(),
                    )
                }
            }
        }
        check(status == kCCSuccess.convert<Int>()) { "PBKDF2 failed ($status)" }
    } finally {
        utf8.fill(0)
    }
    return out
}

/** Collects output chunks without copying them more than once. */
private class Output(private val limit: Long) {
    private val chunks = ArrayList<ByteArray>()
    var size = 0L
        private set

    fun add(chunk: ByteArray, count: Int) {
        if (count == 0) return
        size += count
        if (size > limit) throw IllegalStateException("File too large (over ${limit / (1024 * 1024)} MB)")
        chunks += chunk.copyOf(count)
    }

    fun bytes(): ByteArray {
        val out = ByteArray(size.toInt())
        var at = 0
        for (c in chunks) {
            c.copyInto(out, at)
            at += c.size
        }
        return out
    }
}

private const val CHUNK = 64 * 1024

actual fun gzip(data: ByteArray): ByteArray = memScoped {
    val stream = alloc<z_stream>()
    stream.zalloc = null
    stream.zfree = null
    stream.opaque = null
    // 15 + 16: largest window, gzip header instead of zlib's.
    check(deflateInit2_(stream.ptr, Z_DEFAULT_COMPRESSION, Z_DEFLATED, 15 + 16, 8, Z_DEFAULT_STRATEGY, ZLIB_VERSION, sizeOf<z_stream>().convert()) == Z_OK) {
        "gzip failed to start"
    }
    val out = Output(Long.MAX_VALUE)
    val chunk = ByteArray(CHUNK)
    try {
        data.withPointer { input ->
            stream.next_in = input?.reinterpret()
            stream.avail_in = data.size.convert()
            do {
                val result = chunk.usePinned {
                    stream.next_out = it.addressOf(0).reinterpret()
                    stream.avail_out = CHUNK.convert()
                    deflate(stream.ptr, Z_FINISH)
                }
                check(result == Z_OK || result == Z_STREAM_END) { "gzip failed ($result)" }
                out.add(chunk, CHUNK - stream.avail_out.toInt())
            } while (result != Z_STREAM_END)
        }
    } finally {
        deflateEnd(stream.ptr)
    }
    out.bytes()
}

actual fun gzipText(write: (Appendable) -> Unit): ByteArray =
    gzip(StringBuilder().also(write).toString().encodeToByteArray())

actual fun gunzip(data: ByteArray, limit: Long): ByteArray = memScoped {
    val stream = alloc<z_stream>()
    stream.zalloc = null
    stream.zfree = null
    stream.opaque = null
    stream.next_in = null
    stream.avail_in = 0u
    // 15 + 16: gzip only, like the JDK's GZIPInputStream.
    check(inflateInit2_(stream.ptr, 15 + 16, ZLIB_VERSION, sizeOf<z_stream>().convert()) == Z_OK) { "gunzip failed to start" }
    val out = Output(limit)
    val chunk = ByteArray(CHUNK)
    try {
        data.withPointer { input ->
            stream.next_in = input?.reinterpret()
            stream.avail_in = data.size.convert()
            while (true) {
                val result = chunk.usePinned {
                    stream.next_out = it.addressOf(0).reinterpret()
                    stream.avail_out = CHUNK.convert()
                    inflate(stream.ptr, Z_NO_FLUSH)
                }
                val produced = CHUNK - stream.avail_out.toInt()
                if (result != Z_OK && result != Z_STREAM_END) throw IllegalStateException("Broken gzip file ($result)")
                out.add(chunk, produced)
                if (result == Z_STREAM_END) break
                if (produced == 0 && stream.avail_in.toInt() == 0) throw IllegalStateException("Gzip file cut short")
            }
        }
    } finally {
        inflateEnd(stream.ptr)
    }
    out.bytes()
}
