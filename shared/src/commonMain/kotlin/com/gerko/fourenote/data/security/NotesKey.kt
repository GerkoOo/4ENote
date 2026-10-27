package com.gerko.fourenote.data.security

import com.gerko.fourenote.data.sync.NoteGzip
import com.gerko.fourenote.platform.BadTagException
import com.gerko.fourenote.platform.aesGcmOpen
import com.gerko.fourenote.platform.aesGcmSeal
import com.gerko.fourenote.platform.pbkdf2Sha256
import com.gerko.fourenote.platform.secureRandomBytes
import com.gerko.fourenote.platform.sha256
import com.gerko.fourenote.platform.toHex
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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
    val id: String = sha256(bytes).copyOf(8).toHex()

    fun copyBytes(): ByteArray = bytes.copyOf()

    /**
     * [MAGIC], a fresh nonce, then the ciphertext with its tag. [context] (the note id or the
     * attachment's cloud name) is bound in, so a file moved to another name no longer opens.
     */
    fun encrypt(plain: ByteArray, context: String): ByteArray {
        val nonce = secureRandomBytes(NONCE_SIZE)
        return MAGIC + nonce + aesGcmSeal(bytes, nonce, context.encodeToByteArray(), plain)
    }

    /** The plain bytes, or [BadTagException] when the key, the context or a single byte is wrong. */
    fun decrypt(data: ByteArray, context: String): ByteArray {
        if (!isEncrypted(data) || data.size < MAGIC.size + NONCE_SIZE + TAG_SIZE) throw BadTagException("Not encrypted by 4ENote")
        val nonce = data.copyOfRange(MAGIC.size, MAGIC.size + NONCE_SIZE)
        return aesGcmOpen(bytes, nonce, context.encodeToByteArray(), data.copyOfRange(MAGIC.size + NONCE_SIZE, data.size))
    }

    companion object {
        const val SIZE = 32
        private const val NONCE_SIZE = 12
        private const val TAG_SIZE = 16
        private val MAGIC = "4EL1".encodeToByteArray()

        fun generate() = NotesKey(secureRandomBytes(SIZE))

        fun isEncrypted(data: ByteArray) =
            data.size >= MAGIC.size && MAGIC.indices.all { data[it] == MAGIC[it] }
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun encode(bytes: ByteArray) = Base64.encode(bytes)

@OptIn(ExperimentalEncodingApi::class)
private fun decode(text: String) = Base64.decode(text)

private fun parse(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

private fun JsonObject.string(name: String): String? = (this[name] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.requireString(name: String): String = requireNotNull(string(name)) { "No $name" }

/**
 * `lock.json` in the cloud folder: the notes key, encrypted with a key made from the 4ENote
 * password (PBKDF2, slow on purpose). Its salt is its own, never the one of the password check
 * kept on the device, so that check can't open it.
 */
object LockFile {
    const val NAME = "lock.json"
    const val MIME_TYPE = "application/json"
    /** PBKDF2 rounds, the same as the device's own password check. */
    const val ITERATIONS = 600_000
    private const val FORMAT = "4enote-lock"
    private const val VERSION = 1

    /** Slow (a fraction of a second): call it off the main thread. */
    fun wrap(key: NotesKey, password: CharArray, iterations: Int = ITERATIONS): String {
        val salt = secureRandomBytes(16)
        val derived = pbkdf2Sha256(password, salt, iterations, NotesKey.SIZE)
        val wrapping = NotesKey(derived).also { derived.fill(0) }
        val raw = key.copyBytes()
        try {
            return buildJsonObject {
                put("format", FORMAT)
                put("v", VERSION)
                put("key", key.id)
                put("salt", encode(salt))
                put("iterations", iterations)
                put("wrapped", encode(wrapping.encrypt(raw, FORMAT)))
            }.toString()
        } finally {
            raw.fill(0)
        }
    }

    /** The notes key, or null when [password] is wrong. Slow: off the main thread. */
    fun unwrap(text: String, password: CharArray): NotesKey? {
        val json = parse(text)
        require(json.string("format") == FORMAT) { "Not a 4ENote lock file" }
        val iterations = requireNotNull(json["iterations"]) { "No iterations" }.jsonPrimitive.int
        // A tampered file asking for almost no work would make guessing cheap for nobody but itself;
        // one asking for absurd work would freeze the app.
        require(iterations in 100_000..10_000_000) { "Unusual lock file" }
        val derived = pbkdf2Sha256(password, decode(json.requireString("salt")), iterations, NotesKey.SIZE)
        val wrapping = NotesKey(derived).also { derived.fill(0) }
        val raw = try {
            wrapping.decrypt(decode(json.requireString("wrapped")), FORMAT)
        } catch (e: BadTagException) {
            return null
        }
        return try {
            NotesKey(raw).takeIf { it.id == json.string("key") }
        } finally {
            raw.fill(0)
        }
    }

    /** Which notes key the file holds, readable without the password. */
    fun keyId(text: String): String? = runCatching { parse(text).string("key")?.ifEmpty { null } }.getOrNull()
}

/**
 * A locked note as stored in the cloud: only its id stays readable; title, ink and everything
 * else is the plain note file, encrypted with the notes key.
 */
object LockedNoteFile {
    private const val FORMAT = "4enote-locked"
    private const val VERSION = 1

    fun isLocked(text: String) = text.contains("\"$FORMAT\"") && runCatching { parse(text).string("format") == FORMAT }.getOrDefault(false)

    /** [compressed] is the note file as NoteFile.toCloud gives it. */
    fun encode(noteId: String, compressed: ByteArray, key: NotesKey): String =
        buildJsonObject {
            put("format", FORMAT)
            put("v", VERSION)
            put("id", noteId)
            put("key", key.id)
            put("data", encode(key.encrypt(compressed, noteId)))
        }.toString()

    /** Which notes key encrypted [text]. */
    fun keyId(text: String): String = parse(text).string("key").orEmpty()

    /** The plain note file; throws when the key is wrong or the file was changed. */
    fun decode(text: String, key: NotesKey): String = NoteGzip.fromCloud(decrypt(text, key))

    /** The note file still compressed, as [encode] was given it. */
    fun decrypt(text: String, key: NotesKey): ByteArray {
        val json = parse(text)
        return key.decrypt(decode(json.requireString("data")), json.requireString("id"))
    }
}
