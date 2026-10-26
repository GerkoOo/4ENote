package com.gerko.fourenote.data.security

import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The notes key: 32 random bytes that encrypt locked notes and their pictures (AES-256-GCM) before
 * they go to the cloud. Every device that has it can read them; it reaches a new device through
 * the cloud's [LockFile], wrapped with the 4ENote password.
 */
class NotesKey(bytes: ByteArray) {
    init {
        require(bytes.size == SIZE) { "A notes key is $SIZE bytes" }
    }

    // A copy of its own: callers wipe the array they passed once they are done with it.
    private val bytes = bytes.copyOf()

    /** A short name for this key, kept next to everything it encrypted, so a different key is noticed. */
    val id: String = MessageDigest.getInstance("SHA-256").digest(bytes).take(8).joinToString("") { "%02x".format(it) }

    fun copyBytes(): ByteArray = bytes.copyOf()

    /**
     * [MAGIC], a fresh nonce, then the ciphertext with its tag. [context] (the note id or the
     * attachment's cloud name) is bound in, so a file moved to another name no longer opens.
     */
    fun encrypt(plain: ByteArray, context: String): ByteArray {
        val nonce = ByteArray(NONCE_SIZE).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(bytes, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(context.encodeToByteArray())
        return MAGIC + nonce + cipher.doFinal(plain)
    }

    /** The plain bytes, or an exception when the key, the context or a single byte is wrong. */
    fun decrypt(data: ByteArray, context: String): ByteArray {
        if (!isEncrypted(data) || data.size < MAGIC.size + NONCE_SIZE + TAG_BITS / 8) throw AEADBadTagException("Not encrypted by 4ENote")
        val nonce = data.copyOfRange(MAGIC.size, MAGIC.size + NONCE_SIZE)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(bytes, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(context.encodeToByteArray())
        return cipher.doFinal(data, MAGIC.size + NONCE_SIZE, data.size - MAGIC.size - NONCE_SIZE)
    }

    companion object {
        const val SIZE = 32
        private const val NONCE_SIZE = 12
        private const val TAG_BITS = 128
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private val MAGIC = "4EL1".encodeToByteArray()

        fun generate() = NotesKey(ByteArray(SIZE).also { SecureRandom().nextBytes(it) })

        fun isEncrypted(data: ByteArray) =
            data.size >= MAGIC.size && MAGIC.indices.all { data[it] == MAGIC[it] }
    }
}

/**
 * `lock.json` in the cloud folder: the notes key, encrypted with a key made from the 4ENote
 * password (PBKDF2, slow on purpose). Its salt is its own, never the one of the password check
 * kept on the device, so that check can't open it.
 */
object LockFile {
    const val NAME = "lock.json"
    const val MIME_TYPE = "application/json"
    private const val FORMAT = "4enote-lock"
    private const val VERSION = 1

    /** Slow (a fraction of a second): call it off the main thread. */
    fun wrap(key: NotesKey, password: CharArray, iterations: Int = AppPassword.ITERATIONS): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val wrapping = NotesKey(derive(password, salt, iterations))
        val raw = key.copyBytes()
        try {
            return JSONObject()
                .put("format", FORMAT)
                .put("v", VERSION)
                .put("key", key.id)
                .put("salt", encode(salt))
                .put("iterations", iterations)
                .put("wrapped", encode(wrapping.encrypt(raw, FORMAT)))
                .toString()
        } finally {
            raw.fill(0)
        }
    }

    /** The notes key, or null when [password] is wrong. Slow: off the main thread. */
    fun unwrap(text: String, password: CharArray): NotesKey? {
        val json = JSONObject(text)
        require(json.optString("format") == FORMAT) { "Not a 4ENote lock file" }
        val iterations = json.getInt("iterations")
        // A tampered file asking for almost no work would make guessing cheap for nobody but itself;
        // one asking for absurd work would freeze the app.
        require(iterations in 100_000..10_000_000) { "Unusual lock file" }
        val wrapping = NotesKey(derive(password, decode(json.getString("salt")), iterations))
        val raw = try {
            wrapping.decrypt(decode(json.getString("wrapped")), FORMAT)
        } catch (e: AEADBadTagException) {
            return null
        }
        return try {
            NotesKey(raw).takeIf { it.id == json.optString("key") }
        } finally {
            raw.fill(0)
        }
    }

    /** Which notes key the file holds, readable without the password. */
    fun keyId(text: String): String? = runCatching { JSONObject(text).optString("key").ifEmpty { null } }.getOrNull()

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
    private fun decode(text: String) = Base64.getDecoder().decode(text)
}

/**
 * A locked note as stored in the cloud: only its id stays readable; title, ink and everything
 * else is the plain note file, encrypted with the notes key.
 */
object LockedNoteFile {
    private const val FORMAT = "4enote-locked"
    private const val VERSION = 1

    fun isLocked(text: String) = text.contains("\"$FORMAT\"") && runCatching { JSONObject(text).optString("format") == FORMAT }.getOrDefault(false)

    /** [compressed] is the note file as [com.gerko.fourenote.data.sync.NoteFile.toCloud] gives it. */
    fun encode(noteId: String, compressed: ByteArray, key: NotesKey): String =
        JSONObject()
            .put("format", FORMAT)
            .put("v", VERSION)
            .put("id", noteId)
            .put("key", key.id)
            .put("data", Base64.getEncoder().encodeToString(key.encrypt(compressed, noteId)))
            .toString()

    /** Which notes key encrypted [text]. */
    fun keyId(text: String): String = JSONObject(text).optString("key")

    /** The plain note file; throws when the key is wrong or the file was changed. */
    fun decode(text: String, key: NotesKey): String = com.gerko.fourenote.data.sync.NoteFile.fromCloud(decrypt(text, key))

    /** The note file still compressed, as [encode] was given it. */
    fun decrypt(text: String, key: NotesKey): ByteArray {
        val json = JSONObject(text)
        return key.decrypt(Base64.getDecoder().decode(json.getString("data")), json.getString("id"))
    }
}
