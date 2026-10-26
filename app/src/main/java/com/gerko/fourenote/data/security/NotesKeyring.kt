package com.gerko.fourenote.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Holds this device's copy of the [NotesKey] and keeps the cloud's [LockFile] in step with it.
 *
 * The key is made when the 4ENote password is first set (with no lock file in the cloud yet), or
 * comes from the cloud's lock file when the password set on another device is entered here. On the
 * device it is kept encrypted by an Android Keystore key, which never leaves the tablet's hardware.
 */
class NotesKeyring(private val store: Store, private val sealer: Sealer) {
    /** Where the sealed key and the last known lock file live; an interface so this is testable without Android. */
    interface Store {
        var sealedKey: String?
        /** The cloud's lock file as last seen, or the one made here and not uploaded yet. */
        var lockFile: String?
        /** [lockFile] was made here (new key or new password) and still has to reach the cloud. */
        var lockFileDirty: Boolean
        /** Per note, "keyId:cloud version" of its last encrypted copy (see [encryptedVersion]). */
        fun encryptedVersion(noteId: String): String?
        fun setEncryptedVersion(noteId: String, value: String)
    }

    /** Encrypts the notes key for keeping on this device. */
    interface Sealer {
        fun seal(plain: ByteArray): ByteArray
        fun open(sealed: ByteArray): ByteArray
    }

    constructor(context: Context) : this(
        object : Store {
            // Its own file, kept out of Android backups (see res/xml/backup_rules.xml).
            private val prefs = context.getSharedPreferences("notes-key", Context.MODE_PRIVATE)
            override var sealedKey: String?
                get() = prefs.getString("sealedKey", null)
                set(value) = prefs.edit().putString("sealedKey", value).apply()
            override var lockFile: String?
                get() = prefs.getString("lockFile", null)
                set(value) = prefs.edit().putString("lockFile", value).apply()
            override var lockFileDirty: Boolean
                get() = prefs.getBoolean("lockFileDirty", false)
                set(value) = prefs.edit().putBoolean("lockFileDirty", value).apply()
            override fun encryptedVersion(noteId: String) = prefs.getString("enc:$noteId", null)
            override fun setEncryptedVersion(noteId: String, value: String) = prefs.edit().putString("enc:$noteId", value).apply()
        },
        KeystoreSealer("4enote-notes-key"),
    )

    private var cached: NotesKey? = null

    private val _state = MutableStateFlow(computeState())
    /** What the privacy screen tells the user about encryption. */
    val state: StateFlow<State> = _state.asStateFlow()

    private val _waiting = MutableStateFlow(0)
    /** Locked notes in the cloud the last sync couldn't open, for want of the key. */
    val waiting: StateFlow<Int> = _waiting.asStateFlow()

    enum class State {
        /** No 4ENote password anywhere yet: locked notes go to the cloud readable. */
        OFF,
        /** This device has the key: locked notes are encrypted. */
        ON,
        /** Another device set the password; it has to be entered here before locked notes sync. */
        NEEDS_PASSWORD,
    }

    /** The notes key, or null when this device has none (or its Keystore key is gone, as after a restore). */
    fun key(): NotesKey? {
        cached?.let { return it }
        val sealed = store.sealedKey ?: return null
        val raw = runCatching { sealer.open(Base64.getDecoder().decode(sealed)) }.getOrNull() ?: return null
        return try {
            NotesKey(raw).also { cached = it }
        } finally {
            raw.fill(0)
        }
    }

    /**
     * The 4ENote password was set or changed here. Makes the key if there is none anywhere yet,
     * and wraps it with [password] for the cloud. Returns false, changing nothing, when another
     * device's lock file is known but its key isn't here: [unlockWith] that password instead.
     * Slow: off the main thread.
     */
    fun passwordSet(password: CharArray): Boolean {
        val key = key() ?: run {
            if (store.lockFile != null) return false
            NotesKey.generate().also(::keep)
        }
        store.lockFile = LockFile.wrap(key, password)
        store.lockFileDirty = true
        update()
        return true
    }

    /** Opens the known lock file with the password set on another device. Slow: off the main thread. */
    fun unlockWith(password: CharArray): Boolean {
        val text = store.lockFile ?: return false
        val key = runCatching { LockFile.unwrap(text, password) }.getOrNull() ?: return false
        keep(key)
        update()
        return true
    }

    /**
     * Sync found [cloud] as the cloud's lock file (null: there is none). Returns the lock file to
     * upload, if any; call [uploaded] once it is there.
     */
    fun reconcile(cloud: String?): String? {
        val local = store.lockFile
        val key = key()
        try {
            if (store.lockFileDirty && local != null) {
                // Ours is a new password for the same key, or the first lock file: it goes up.
                if (cloud == null || LockFile.keyId(cloud) == LockFile.keyId(local)) return local
                // Another device set a password first, with its own key. Nothing was encrypted with
                // ours yet (that only starts once our lock file is up), so theirs wins.
                drop()
                store.lockFile = cloud
                return null
            }
            if (cloud == null) return local
            store.lockFile = cloud
            // Its key is no longer the cloud's: notes encrypted from now on would be unreadable elsewhere.
            if (key != null && LockFile.keyId(cloud) != key.id) drop()
            return null
        } finally {
            update()
        }
    }

    fun uploaded(text: String) {
        if (store.lockFile == text) store.lockFileDirty = false
    }

    /** Whether this device may encrypt with its key: only once the cloud has that key's lock file. */
    fun keyForUpload(): NotesKey? = key()?.takeIf { !store.lockFileDirty }

    /** "keyId:version" of the note's cloud copy last seen encrypted with this device's key. */
    fun encryptedVersion(noteId: String): String? = store.encryptedVersion(noteId)

    /** The note's cloud copy at [version] is encrypted with this device's key. */
    fun encryptedVersion(noteId: String, version: String) {
        val key = key() ?: return
        store.setEncryptedVersion(noteId, "${key.id}:$version")
    }

    fun reportWaiting(count: Int) {
        _waiting.value = count
    }

    private fun keep(key: NotesKey) {
        val raw = key.copyBytes()
        try {
            store.sealedKey = Base64.getEncoder().encodeToString(sealer.seal(raw))
        } finally {
            raw.fill(0)
        }
        cached = key
    }

    private fun drop() {
        store.sealedKey = null
        store.lockFileDirty = false
        cached = null
    }

    private fun update() {
        _state.value = computeState()
    }

    private fun computeState(): State = when {
        key() != null -> State.ON
        store.lockFile != null -> State.NEEDS_PASSWORD
        else -> State.OFF
    }

}

/** AES-GCM with a key ([alias]) in the Android Keystore; it opens without asking (the app gate is [Unlock]). */
class KeystoreSealer(private val alias: String) : NotesKeyring.Sealer {
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override fun seal(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun open(sealed: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, sealed, 0, 12))
        return cipher.doFinal(sealed, 12, sealed.size - 12)
    }
}
