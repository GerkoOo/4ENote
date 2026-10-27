package com.gerko.fourenote.platform

/**
 * Known answers (NIST, Python's hashlib/gzip/cryptography) for every primitive in Crypto.kt. The
 * JVM tests run it, and the iPad app runs it when launched with `-selftest` (the iOS CI does),
 * so both platforms provably read each other's files.
 */
object CryptoSelfTest {
    fun failures(): List<String> {
        val failures = ArrayList<String>()
        fun check(name: String, block: () -> Boolean) {
            val ok = runCatching(block).getOrElse { failures += "$name: ${it::class.simpleName} ${it.message}"; return }
            if (!ok) failures += name
        }
        check("sha256") { sha256("abc".encodeToByteArray()).toHex() == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad" }
        check("sha256 empty") { sha256(ByteArray(0)).toHex() == "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" }
        check("random") { secureRandomBytes(32).let { a -> a.size == 32 && !a.contentEquals(secureRandomBytes(32)) } }
        val key = hexToBytes("feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308")
        val nonce = hexToBytes("cafebabefacedbaddecaf888")
        val aad = hexToBytes("feedfacedeadbeeffeedfacedeadbeefabaddad2")
        val plain = hexToBytes("d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a721c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b39")
        val sealed = "522dc1f099567d07f47f37a32a84427d643a8cdcbfe5c0c97598a2bd2555d1aa8cb08e48590dbb3da7b08b1056828838c5f61e6393ba7a0abcc9f662" +
            "76fc6ece0f4e1768cddf8853bb2d551b"
        check("aes-gcm seal") { aesGcmSeal(key, nonce, aad, plain).toHex() == sealed }
        check("aes-gcm open") { aesGcmOpen(key, nonce, aad, hexToBytes(sealed)).contentEquals(plain) }
        check("aes-gcm empty") { aesGcmOpen(key, nonce, ByteArray(0), aesGcmSeal(key, nonce, ByteArray(0), ByteArray(0))).isEmpty() }
        check("aes-gcm wrong aad") {
            try {
                aesGcmOpen(key, nonce, "other".encodeToByteArray(), hexToBytes(sealed)); false
            } catch (e: BadTagException) {
                true
            }
        }
        check("pbkdf2") {
            pbkdf2Sha256("pässwort".toCharArray(), "4ENote salt 16b!".encodeToByteArray(), 1000, 32).toHex() ==
                "da1bd23c79a8fff0d3387be351f00a73b1dc2c04e50fc45f96be189ef540ebec"
        }
        val text = "Hallo 4ENote, hallo gzip. ".repeat(3)
        check("gunzip") {
            gunzip(hexToBytes("1f8b08000000000002fff348ccc9c9573071f5cb2f49d551c800f3d2ab320bf4143cc89001009f193f304e000000"), 1000)
                .decodeToString() == text
        }
        check("gzip round trip") {
            val big = ByteArray(300_000) { (it * 7 % 251).toByte() }
            gunzip(gzip(big), big.size.toLong()).contentEquals(big) && gunzip(gzip(ByteArray(0)), 0).isEmpty()
        }
        check("gunzip limit") {
            try {
                gunzip(gzip(ByteArray(5000)), 4999); false
            } catch (e: Exception) {
                true
            }
        }
        return failures
    }
}
