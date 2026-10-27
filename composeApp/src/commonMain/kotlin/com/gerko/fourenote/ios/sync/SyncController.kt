package com.gerko.fourenote.ios.sync

import com.gerko.fourenote.data.sync.CloudProvider
import com.gerko.fourenote.data.sync.DeviceIds
import com.gerko.fourenote.data.sync.SyncEngine
import com.gerko.fourenote.data.sync.webdav.WebDavProvider
import com.gerko.fourenote.ios.store.NoteStore
import com.gerko.fourenote.ios.store.Secrets
import com.gerko.fourenote.ios.store.excludeFromBackup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okio.FileSystem
import okio.Path

/** A Nextcloud (or other WebDAV) account. The password lives in the Keychain, the rest in a file. */
data class WebDavAccount(val url: String, val user: String, val password: String)

sealed interface SyncStatus {
    data object NotConnected : SyncStatus
    data object Syncing : SyncStatus
    data class Done(val at: Long, val problems: List<String>) : SyncStatus
    data class Failed(val message: String) : SyncStatus
}

/**
 * Runs the shared [SyncEngine] over this iPad's notes: at start, when a note is closed, and on
 * request. One sync at a time; a request during a sync is dropped (the running one covers it,
 * except edits made after it read the note, which stay dirty for the next).
 */
class SyncController(
    private val store: NoteStore,
    private val dir: Path,
    private val fs: FileSystem,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val provider: (WebDavAccount) -> CloudProvider = { WebDavProvider(it.url, it.user, it.password) },
) {
    private val running = Mutex()
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.NotConnected)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /** Goes up after every sync that may have changed notes, so the home page reloads. */
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    init {
        if (account() != null) _status.value = SyncStatus.Done(0, emptyList())
    }

    /** This iPad's id in the cloud's file names; made once, never backed up (see [excludeFromBackup]). */
    val deviceId: String by lazy {
        val file = dir / "device-id"
        val existing = if (fs.exists(file)) fs.read(file) { readUtf8() }.trim() else ""
        if (DeviceIds.isValid(existing)) return@lazy existing
        val id = DeviceIds.random()
        fs.createDirectories(dir)
        fs.write(file) { writeUtf8(id) }
        excludeFromBackup(file)
        id
    }

    fun account(): WebDavAccount? {
        val file = dir / ACCOUNT_FILE
        if (!fs.exists(file)) return null
        val o = runCatching { Json.parseToJsonElement(fs.read(file) { readUtf8() }).jsonObject }.getOrNull() ?: return null
        val url = (o["url"] as? JsonPrimitive)?.contentOrNull ?: return null
        val user = (o["user"] as? JsonPrimitive)?.contentOrNull ?: return null
        return WebDavAccount(url, user, Secrets.get(PASSWORD_KEY) ?: return null)
    }

    /** Tries the account; on success keeps it and syncs. Returns an error message, or null. */
    suspend fun connect(account: WebDavAccount): String? {
        val error = try {
            provider(account).listFiles()
            null
        } catch (e: Exception) {
            errorMessage(e)
        }
        if (error != null) return error
        val previous = account()
        running.withLock {
            // A different cloud: its files have nothing to do with what was synced before.
            if (previous == null || previous.url != account.url || previous.user != account.user) store.sync.forgetCloud()
            Secrets.set(PASSWORD_KEY, account.password)
            fs.createDirectories(dir)
            fs.write(dir / ACCOUNT_FILE) {
                writeUtf8(buildJsonObject { put("url", account.url); put("user", account.user) }.toString())
            }
        }
        syncNow()
        return null
    }

    fun disconnect() {
        scope.launch {
            running.withLock {
                fs.delete(dir / ACCOUNT_FILE)
                Secrets.set(PASSWORD_KEY, null)
                _status.value = SyncStatus.NotConnected
            }
        }
    }

    fun syncNow() {
        val account = account() ?: return
        scope.launch {
            if (!running.tryLock()) return@launch
            try {
                _status.value = SyncStatus.Syncing
                val result = SyncEngine(store.sync, provider(account), clock = now, deviceId = deviceId).sync()
                _status.value = SyncStatus.Done(now(), result.errors)
            } catch (e: Exception) {
                _status.value = SyncStatus.Failed(errorMessage(e))
            } finally {
                running.unlock()
                _changes.value++
            }
        }
    }

    private fun errorMessage(e: Exception): String {
        val message = e.message.orEmpty()
        return when {
            message.startsWith("WebDAV 401") -> "Wrong user name or password."
            message.startsWith("WebDAV 404") || message.startsWith("WebDAV 405") -> "No WebDAV folder at this address."
            message.startsWith("WebDAV") -> "The server answered: ${message.take(120)}"
            else -> "Can't reach the server: ${message.ifEmpty { e::class.simpleName ?: "error" }.take(120)}"
        }
    }

    private companion object {
        const val ACCOUNT_FILE = "webdav.json"
        const val PASSWORD_KEY = "webdav-password"
    }
}
