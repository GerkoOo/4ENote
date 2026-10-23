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
