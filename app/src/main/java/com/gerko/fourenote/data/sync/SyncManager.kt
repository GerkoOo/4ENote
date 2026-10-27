package com.gerko.fourenote.data.sync

import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.NetworkType
import androidx.work.ExistingWorkPolicy
import androidx.work.Constraints
import android.net.NetworkCapabilities
import android.net.Network
import android.net.ConnectivityManager
import android.content.Context
import com.gerko.fourenote.data.NoteRepository
import com.gerko.fourenote.data.attachments.AttachmentStore
import com.gerko.fourenote.data.sync.google.GoogleAuth
import com.gerko.fourenote.data.sync.google.GoogleDriveProvider
import com.gerko.fourenote.data.sync.webdav.WebDavProvider
import com.gerko.fourenote.export.PdfExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.UnknownHostException

/** A short message for a failed WebDAV connection test. */
internal fun webDavErrorMessage(e: Exception): String = when {
    e.message?.startsWith("WebDAV 401") == true -> "Wrong user name or password"
    e is UnknownHostException || e.cause is UnknownHostException -> "Server not found"
    else -> e.message ?: e.javaClass.simpleName
}

/** Where notes sync to. */
enum class CloudChoice(val label: String) {
    NONE("Not connected"),
    TEST("Test cloud (a folder on this tablet)"),
    GOOGLE_DRIVE("Google Drive"),
    WEBDAV("Nextcloud / WebDAV"),
    ONEDRIVE("OneDrive"),
}

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Syncing : SyncStatus
    data class Done(val result: SyncResult, val at: Long) : SyncStatus
    data class Failed(val message: String, val at: Long) : SyncStatus
}

/**
 * Owns the chosen cloud and runs sync: on app start, when a note is closed or
 * deleted, every [AUTO_SYNC_INTERVAL_MS] while the app is in front (to pull
 * other devices' edits), and on demand. Only one sync runs at a time.
 */
class SyncManager(
    private val context: Context,
    private val repository: NoteRepository,
    private val attachments: AttachmentStore,
    val googleAuth: GoogleAuth,
    private val scope: CoroutineScope,
    private val inbox: com.gerko.fourenote.data.scan.ScanInbox,
    private val keys: com.gerko.fourenote.data.security.NotesKeyring,
) {
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    // Kept apart from "sync" so the backup rules can exclude just the password.
    private val webDavPrefs = context.getSharedPreferences("webdav", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var autoSync: Job? = null

    private val _choice = MutableStateFlow(
        runCatching { CloudChoice.valueOf(prefs.getString(KEY_CHOICE, null) ?: "") }.getOrDefault(CloudChoice.NONE),
    )
    val choice: StateFlow<CloudChoice> = _choice

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status

    private val _webDavAccount = MutableStateFlow(webDavAccountLabel())
    /** "user @ host" of the saved WebDAV server, or null when none is set up. */
    val webDavAccount: StateFlow<String?> = _webDavAccount

    /** Whether [choice] can be picked now: Google Drive once signed in, WebDAV once set up, OneDrive not yet. */
    /** What sync keeps on this device to merge other devices' edits (see InkStore.syncHistoryBytes). */
    suspend fun syncHistoryBytes(): Long = repository.syncHistoryBytes()

    fun isAvailable(choice: CloudChoice) = when (choice) {
        CloudChoice.GOOGLE_DRIVE -> googleAuth.configured && googleAuth.account.value != null
        CloudChoice.WEBDAV -> webDavCredentials() != null
        CloudChoice.ONEDRIVE -> false
        else -> true
    }

    /** Called once Google sign-in finishes: switches sync to Google Drive. */
    fun onGoogleSignedIn() {
        prefs.edit().remove(KEY_DRIVE_FOLDER).apply()
        select(CloudChoice.GOOGLE_DRIVE)
    }

    fun signOutOfGoogle() {
        googleAuth.signOut()
        prefs.edit().remove(KEY_DRIVE_FOLDER).apply()
        if (_choice.value == CloudChoice.GOOGLE_DRIVE) select(CloudChoice.NONE)
    }

    /**
     * Checks the server answers with these credentials, then saves them and switches to WebDAV.
     * Returns null on success, or a short message to show the user; nothing is saved on failure.
     */
    suspend fun connectWebDav(url: String, user: String, password: String): String? {
        val trimmedUrl = url.trim()
        val trimmedUser = user.trim()
        // The password travels with every request, so it may only go over an encrypted connection.
        if (!trimmedUrl.startsWith("https://", ignoreCase = true)) return "The server address must start with https://"
        val error = try {
            withContext(Dispatchers.IO) {
                WebDavProvider(trimmedUrl, trimmedUser, password).listFiles()
            }
            null
        } catch (e: Exception) {
            webDavErrorMessage(e)
        }
        if (error != null) return error
        webDavPrefs.edit()
            .putString(KEY_WEBDAV_URL, trimmedUrl)
            .putString(KEY_WEBDAV_USER, trimmedUser)
            .putString(KEY_WEBDAV_PASSWORD, password)
            .apply()
        _webDavAccount.value = webDavAccountLabel()
        select(CloudChoice.WEBDAV)
        return null
    }

    fun disconnectWebDav() {
        webDavPrefs.edit().clear().apply()
        _webDavAccount.value = null
        if (_choice.value == CloudChoice.WEBDAV) select(CloudChoice.NONE)
    }

    fun select(choice: CloudChoice) {
        if (!isAvailable(choice) || choice == _choice.value) return
        _choice.value = choice
        prefs.edit().putString(KEY_CHOICE, choice.name).apply()
        _status.value = SyncStatus.Idle
        // Not while a sync with the old cloud is still running; it waits for this in turn.
        scope.launch {
            mutex.withLock { repository.forgetCloud() }
            requestSync()
        }
    }

    /**
     * Syncs soon: right away when online, otherwise as soon as a connection comes back, even if
     * the app has been closed by then (a background job, see SyncWorker).
     */
    fun requestSync() {
        if (providerFor(_choice.value) == null) return
        if (!online()) {
            syncWhenOnline()
            return
        }
        scope.launch { syncNow() }
    }

    /** One sync, now. Returns false if it failed (or there is nowhere to sync to). */
    suspend fun syncNow(): Boolean {
        val provider = providerFor(_choice.value) ?: return false
        return mutex.withLock {
            _status.value = SyncStatus.Syncing
            try {
                val engine = SyncEngine(repository, provider, ::exportPdf, deviceId = DeviceIdentity.id(context), inbox = inbox, keys = keys)
                var result = engine.sync()
                // Scans that found their frame make notes to upload (and scans to remove from the cloud).
                if (repository.placeInboxScans(inbox) > 0) result = engine.sync()
                _status.value = SyncStatus.Done(result, System.currentTimeMillis())
                true
            } catch (e: Exception) {
                _status.value = SyncStatus.Failed(e.message ?: e.javaClass.simpleName, System.currentTimeMillis())
                false
            }
        }
    }

    /** Leaves a job for Android to sync once there is a connection, app open or not. */
    fun syncWhenOnline() {
        val work = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SyncWorker.NAME, ExistingWorkPolicy.KEEP, work)
    }

    private fun online(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // Syncs the moment a connection comes back while the app is in front.
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            requestSync()
        }
    }

    /** A failed PDF copy is logged and skipped, so it never blocks syncing the note itself. */
    private fun exportPdf(note: com.gerko.fourenote.data.Note): File? {
        val target = File.createTempFile("export-", ".pdf", context.cacheDir)
        return try {
            PdfExporter.export(note.id, com.gerko.fourenote.data.ink.InkSerializer.decode(note.inkData), attachments, target)
            target
        } catch (e: Exception) {
            android.util.Log.w("SyncManager", "PDF copy of ${note.id.take(8)} failed", e)
            target.delete()
            null
        }
    }

    /** Call when the app comes to the foreground. */
    fun startAutoSync() {
        if (autoSync?.isActive == true) return
        runCatching { context.getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(networkCallback) }
        autoSync = scope.launch {
            while (isActive) {
                requestSync()
                delay(AUTO_SYNC_INTERVAL_MS)
            }
        }
    }

    fun stopAutoSync() {
        runCatching { context.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(networkCallback) }
        autoSync?.cancel()
        autoSync = null
    }

    private fun providerFor(choice: CloudChoice): CloudProvider? = when (choice) {
        CloudChoice.TEST -> LocalFolderCloudProvider(File(context.filesDir, "test-cloud"))
        CloudChoice.GOOGLE_DRIVE -> if (googleAuth.account.value == null) null else GoogleDriveProvider(googleAuth) { found ->
            // Remembers the "4ENote" folder's id between syncs.
            if (found != null) prefs.edit().putString(KEY_DRIVE_FOLDER, found).apply()
            prefs.getString(KEY_DRIVE_FOLDER, null)
        }
        CloudChoice.WEBDAV -> webDavCredentials()?.let { (url, user, password) ->
            WebDavProvider(url, user, password)
        }
        CloudChoice.ONEDRIVE, CloudChoice.NONE -> null
    }

    private fun webDavCredentials(): Triple<String, String, String>? {
        val url = webDavPrefs.getString(KEY_WEBDAV_URL, null) ?: return null
        val user = webDavPrefs.getString(KEY_WEBDAV_USER, null) ?: return null
        val password = webDavPrefs.getString(KEY_WEBDAV_PASSWORD, null) ?: return null
        return Triple(url, user, password)
    }

    private fun webDavAccountLabel(): String? {
        val (url, user, _) = webDavCredentials() ?: return null
        val host = runCatching { java.net.URI(url).host }.getOrNull() ?: url
        return "$user @ $host"
    }

    private companion object {
        const val KEY_CHOICE = "cloud"
        const val KEY_DRIVE_FOLDER = "driveFolder"
        const val KEY_WEBDAV_URL = "url"
        const val KEY_WEBDAV_USER = "user"
        const val KEY_WEBDAV_PASSWORD = "password"
        const val AUTO_SYNC_INTERVAL_MS = 60_000L
    }
}
