package com.gerko.fourenote.data.security

import android.content.Context
import com.gerko.fourenote.data.ink.FileSealing
import java.util.Base64

/**
 * The device key: a random [NotesKey] that encrypts locked notes' files on this tablet (see
 * [com.gerko.fourenote.data.ink.InkStore.setSealed]). It is kept sealed by its own Android Keystore
 * key, in a file left out of backups, and never leaves the device, so note files copied off the
 * tablet (an Android backup, adb) can't be read. It opens without asking, because sync reads
 * locked notes in the background; the gate in front of them is [Unlock].
 */
class DeviceKey(private val store: Store, private val sealer: NotesKeyring.Sealer) : FileSealing {
    /** Where the sealed key lives; an interface so this is testable without Android. */
    interface Store {
        val sealedKey: String?
        /** Stores the key before returning: nothing may be sealed with a key that could still be lost. */
        fun saveSealedKey(value: String)
    }

    constructor(context: Context) : this(
        object : Store {
            // Its own file, kept out of Android backups (see res/xml/backup_rules.xml).
            private val prefs = context.getSharedPreferences("device-key", Context.MODE_PRIVATE)
            override val sealedKey: String? get() = prefs.getString("sealedKey", null)
            override fun saveSealedKey(value: String) {
                check(prefs.edit().putString("sealedKey", value).commit()) { "Couldn't store the device key" }
            }
        },
        KeystoreSealer("4enote-device-key"),
    )

    private var cached: NotesKey? = null

    /**
     * The key, made on first use. A stored key that no longer opens is an error, never a reason to
     * make a new one: that would leave every file sealed with the old key unreadable for good.
     */
    @Synchronized
    fun key(): NotesKey {
        cached?.let { return it }
        val stored = store.sealedKey
        val key = if (stored != null) {
            val raw = sealer.open(Base64.getDecoder().decode(stored))
            try {
                NotesKey(raw)
            } finally {
                raw.fill(0)
            }
        } else {
            NotesKey.generate().also { fresh ->
                val raw = fresh.copyBytes()
                try {
                    store.saveSealedKey(Base64.getEncoder().encodeToString(sealer.seal(raw)))
                } finally {
                    raw.fill(0)
                }
            }
        }
        cached = key
        return key
    }

    override fun isSealed(head: ByteArray) = NotesKey.isEncrypted(head)

    override fun seal(plain: ByteArray, name: String) = key().encrypt(plain, CONTEXT + name)

    override fun open(data: ByteArray, name: String) = key().decrypt(data, CONTEXT + name)

    private companion object {
        // Kept apart from what the notes key binds in the cloud (note ids, cloud names).
        const val CONTEXT = "device-ink/"
    }
}
