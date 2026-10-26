package com.gerko.fourenote.data.sync

import android.content.Context
import android.os.Build
import java.security.SecureRandom

/**
 * This installation's id: its own note files in the cloud (see [NoteFile]) and its own layer in
 * each note are named after it. Random, so two devices set up at the same moment never pick the
 * same one; kept out of backups and device transfers, so a restored copy gets an id of its own.
 */
object DeviceIdentity {
    private const val PREFS = "device-id"
    private const val KEY = "id"
    private const val LENGTH = 8
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

    fun id(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.takeIf(::isValid)?.let { return it }
        synchronized(this) {
            prefs.getString(KEY, null)?.takeIf(::isValid)?.let { return it }
            val id = newId()
            // commit, not apply: the id must be on disk before anything is uploaded under it.
            check(prefs.edit().putString(KEY, id).commit()) { "Could not store the device id" }
            return id
        }
    }

    /** What the device's layer is called, like "Xiaomi 23043RP34G". */
    fun name(): String =
        listOf(Build.MANUFACTURER, Build.MODEL).filterNot { it.isNullOrBlank() }.joinToString(" ")
            .replaceFirstChar { it.uppercase() }.ifBlank { "This device" }

    fun newId(random: SecureRandom = SecureRandom()): String =
        String(CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] })

    fun isValid(id: String) = id.length == LENGTH && id.all { it in ALPHABET }

    /** The layer a device draws on in every note. */
    fun layerId(deviceId: String) = "device-$deviceId"
}
