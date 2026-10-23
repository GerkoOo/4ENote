package com.gerko.fourenote.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Asks GitHub for the newest 4ENote release, only when the user taps "Check for updates"
 * (never by itself). Nothing about the user is sent; it is an ordinary web request.
 */
object UpdateChecker {
    private const val LATEST = "https://api.github.com/repos/GerkoOo/4ENote/releases/latest"
    const val DOWNLOAD = "https://github.com/GerkoOo/4ENote/releases/latest/download/4ENote.apk"

    data class Release(val version: String, val notes: String, val page: String)

    suspend fun latest(): Release = withContext(Dispatchers.IO) {
        val connection = URL(LATEST).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            check(connection.responseCode == 200) { "GitHub answered ${connection.responseCode}" }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            Release(
                version = json.getString("tag_name").removePrefix("v"),
                notes = json.optString("body"),
                page = json.optString("html_url"),
            )
        } finally {
            connection.disconnect()
        }
    }

    /** True when the background check may ask GitHub again: only if [automatic], and not more often than [every]. */
    fun isDue(automatic: Boolean, now: Long, lastChecked: Long, every: Long): Boolean =
        automatic && now - lastChecked > every

    /** True when [latest] is newer than [current] and the user hasn't said "Later" to it. */
    fun worthShowing(latest: String?, current: String, dismissed: String?): Boolean =
        latest != null && isNewer(latest, current) && latest != dismissed

    /** True if [candidate] (like "0.3.0") is a later version than [current]. */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}

/**
 * Checks for a new version in the background when the app opens: at most once every
 * [CHECK_EVERY_MS] (GitHub limits how often one connection may ask), and only while
 * [automatic] is on. Remembers the newest version found, and the one the user said "Later" to.
 */
class UpdateWatch(context: android.content.Context, private val currentVersion: String) {
    private val prefs = context.getSharedPreferences("updates", android.content.Context.MODE_PRIVATE)

    var automatic: Boolean
        get() = prefs.getBoolean("auto", true)
        set(value) = prefs.edit().putBoolean("auto", value).apply()

    /** A newer version worth telling the user about, checking GitHub first if it's time; null if none. */
    suspend fun newerVersion(now: Long = System.currentTimeMillis()): UpdateChecker.Release? {
        if (UpdateChecker.isDue(automatic, now, prefs.getLong("checked", 0L), CHECK_EVERY_MS)) {
            runCatching { UpdateChecker.latest() }.onSuccess { latest ->
                prefs.edit().putLong("checked", now).putString("latest", latest.version).putString("notes", latest.notes)
                    .putString("page", latest.page).apply()
            }
        }
        val latest = prefs.getString("latest", null)
        if (!UpdateChecker.worthShowing(latest, currentVersion, prefs.getString("dismissed", null))) return null
        return UpdateChecker.Release(latest!!, prefs.getString("notes", "").orEmpty(), prefs.getString("page", "").orEmpty())
    }

    /** "Later": don't mention [version] again (a newer one still will be). */
    fun dismiss(version: String) = prefs.edit().putString("dismissed", version).apply()

    private companion object {
        const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L
    }
}
