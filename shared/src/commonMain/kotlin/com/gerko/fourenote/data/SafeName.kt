package com.gerko.fourenote.data

/**
 * Note ids, page ids and attachment names become file and folder names on this device. Those that
 * come from the cloud or a backup are untrusted: a name like "../../shared_prefs/sync.xml" would
 * write outside the note's folder. Only plain names (letters, digits, '.', '_', '-') are allowed.
 */
object SafeName {
    private const val MAX_LENGTH = 200

    /** A single path segment that stays inside its folder. Leading dots are allowed (temp files). */
    fun isSafe(name: String): Boolean =
        name.length in 1..MAX_LENGTH && name != "." && name != ".." &&
            name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '.' || it == '_' || it == '-' }

    /** A name from the cloud: safe, and not a hidden name (those are this device's temp files). */
    fun isSafeFromCloud(name: String): Boolean = isSafe(name) && !name.startsWith(".")

    fun require(name: String, what: String): String {
        kotlin.require(isSafe(name)) { "Unsafe $what" }
        return name
    }
}
