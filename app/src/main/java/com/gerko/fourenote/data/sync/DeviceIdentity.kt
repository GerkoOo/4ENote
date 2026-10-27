package com.gerko.fourenote.data.sync

import android.content.Context
import java.security.SecureRandom

/**
 * This installation's id: its own note files in the cloud (see [NoteFile]) are named after it. Random, so two devices set up at the same moment never pick the
 * same one; kept out of backups and device transfers, so a restored copy gets an id of its own.
 */
object DeviceIdentity {
    private const val PREFS = "device-id"
    private const val KEY = "id"

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

    fun newId(random: SecureRandom = SecureRandom()): String =
        String(CharArray(DeviceIds.LENGTH) { DeviceIds.ALPHABET[random.nextInt(DeviceIds.ALPHABET.length)] })

    fun isValid(id: String) = DeviceIds.isValid(id)
}
