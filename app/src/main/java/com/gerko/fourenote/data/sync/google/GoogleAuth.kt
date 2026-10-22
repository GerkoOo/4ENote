package com.gerko.fourenote.data.sync.google

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.gerko.fourenote.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Signing in to Google with plain OAuth 2.0 for apps (authorization code with PKCE), no Google
 * libraries: the sign-in page opens in the browser, Google sends the browser back to
 * `com.gerko.fourenote:/oauth2redirect` (see OAuthRedirectActivity), and the one-time code is
 * swapped for tokens. Only access to files 4ENote creates itself is asked for (drive.file).
 *
 * The OAuth client id comes from `google.clientId` in local.properties (an Android OAuth client
 * with "custom URI scheme" enabled); without it Google Drive can't be chosen.
 */
class GoogleAuth(context: Context) {
    private val prefs = context.getSharedPreferences("google-auth", Context.MODE_PRIVATE)
    private val refreshing = Mutex()

    val clientId: String = BuildConfig.GOOGLE_CLIENT_ID

    /** False in builds made without a client id. */
    val configured: Boolean get() = clientId.isNotBlank()

    private val _account = MutableStateFlow(prefs.getString(KEY_EMAIL, null)?.takeIf { prefs.contains(KEY_REFRESH) })

    /** The signed-in account's email, or null. */
    val account: StateFlow<String?> = _account

    /** Opens Google's sign-in page in the browser; the answer comes back through [handleRedirect]. */
    fun signInIntent(): Intent {
        val verifier = randomToken()
        val state = randomToken()
        prefs.edit().putString(KEY_VERIFIER, verifier).putString(KEY_STATE, state).apply()
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        val url = Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            // A refresh token, so sync keeps working without signing in again.
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
            .build()
        return Intent(Intent.ACTION_VIEW, url)
    }

    /** Finishes signing in with the page's answer. Returns an error message, or null on success. */
    suspend fun handleRedirect(answer: Uri): String? = withContext(Dispatchers.IO) {
        answer.getQueryParameter("error")?.let { return@withContext "Google said: $it" }
        val code = answer.getQueryParameter("code") ?: return@withContext "Google sent no sign-in code"
        if (answer.getQueryParameter("state") != prefs.getString(KEY_STATE, null)) {
            return@withContext "That sign-in didn't start here; please try again"
        }
        val verifier = prefs.getString(KEY_VERIFIER, null) ?: return@withContext "Please start signing in again"
        runCatching {
            val tokens = postForm(
                TOKEN_URL,
                mapOf(
                    "client_id" to clientId,
                    "code" to code,
                    "code_verifier" to verifier,
                    "grant_type" to "authorization_code",
                    "redirect_uri" to REDIRECT_URI,
                ),
            )
            val email = tokens.optString("id_token").takeIf { it.isNotEmpty() }?.let(::emailFromIdToken)
            prefs.edit()
                .putString(KEY_REFRESH, tokens.getString("refresh_token"))
                .putString(KEY_ACCESS, tokens.getString("access_token"))
                .putLong(KEY_EXPIRES, System.currentTimeMillis() + tokens.optLong("expires_in", 3600) * 1000)
                .putString(KEY_EMAIL, email)
                .remove(KEY_VERIFIER)
                .remove(KEY_STATE)
                .apply()
            _account.value = email ?: "Google account"
        }.exceptionOrNull()?.let { "Couldn't finish signing in: ${it.message}" }
    }

    /** A valid access token, refreshed when it is about to run out. */
    suspend fun accessToken(forceRefresh: Boolean = false): String = refreshing.withLock {
        val refresh = prefs.getString(KEY_REFRESH, null) ?: throw IllegalStateException("Not signed in to Google")
        val access = prefs.getString(KEY_ACCESS, null)
        if (!forceRefresh && access != null && prefs.getLong(KEY_EXPIRES, 0) > System.currentTimeMillis() + 60_000) {
            return@withLock access
        }
        val tokens = withContext(Dispatchers.IO) {
            postForm(TOKEN_URL, mapOf("client_id" to clientId, "refresh_token" to refresh, "grant_type" to "refresh_token"))
        }
        val fresh = tokens.getString("access_token")
        prefs.edit()
            .putString(KEY_ACCESS, fresh)
            .putLong(KEY_EXPIRES, System.currentTimeMillis() + tokens.optLong("expires_in", 3600) * 1000)
            .apply()
        fresh
    }

    fun signOut() {
        prefs.edit().clear().apply()
        _account.value = null
    }

    private fun postForm(url: String, fields: Map<String, String>): JSONObject {
        val body = fields.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }.toByteArray()
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.outputStream.use { it.write(body) }
            val ok = connection.responseCode in 200..299
            val text = (if (ok) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (!ok) throw IllegalStateException(runCatching { JSONObject(text).optString("error_description").ifEmpty { JSONObject(text).optString("error") } }.getOrDefault(text))
            return JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun emailFromIdToken(idToken: String): String? = runCatching {
        val payload = idToken.split('.')[1]
        JSONObject(String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))).optString("email").ifEmpty { null }
    }.getOrNull()

    private fun randomToken(): String = ByteArray(32).also { SecureRandom().nextBytes(it) }
        .let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP) }

    companion object {
        const val REDIRECT_URI = "com.gerko.fourenote:/oauth2redirect"
        private const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        private const val SCOPES = "openid email https://www.googleapis.com/auth/drive.file"
        private const val KEY_VERIFIER = "verifier"
        private const val KEY_STATE = "state"
        private const val KEY_REFRESH = "refresh"
        private const val KEY_ACCESS = "access"
        private const val KEY_EXPIRES = "expires"
        private const val KEY_EMAIL = "email"
    }
}
