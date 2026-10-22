package com.gerko.fourenote.data.sync

import android.content.Context
import com.gerko.fourenote.data.NoteRepository
import com.gerko.fourenote.data.attachments.AttachmentStore
import com.gerko.fourenote.export.PdfExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** Where notes sync to. Real services become available once their sign-in is set up. */
enum class CloudChoice(val label: String, val available: Boolean) {
    NONE("Not connected", true),
    TEST("Test cloud (a folder on this tablet)", true),
    GOOGLE_DRIVE("Google Drive", false),
    ONEDRIVE("OneDrive", false),
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
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var autoSync: Job? = null

    private val _choice = MutableStateFlow(
        runCatching { CloudChoice.valueOf(prefs.getString(KEY_CHOICE, null) ?: "") }.getOrDefault(CloudChoice.NONE),
    )
    val choice: StateFlow<CloudChoice> = _choice

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status

    fun select(choice: CloudChoice) {
        if (!choice.available || choice == _choice.value) return
        _choice.value = choice
        prefs.edit().putString(KEY_CHOICE, choice.name).apply()
        _status.value = SyncStatus.Idle
        requestSync()
    }

    fun requestSync() {
        val provider = providerFor(_choice.value) ?: return
        scope.launch {
            mutex.withLock {
                _status.value = SyncStatus.Syncing
                _status.value = try {
                    val result = SyncEngine(repository, provider, ::exportPdf).sync()
                    SyncStatus.Done(result, System.currentTimeMillis())
                } catch (e: Exception) {
                    SyncStatus.Failed(e.message ?: e.javaClass.simpleName, System.currentTimeMillis())
                }
            }
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
        autoSync = scope.launch {
            while (isActive) {
                requestSync()
                delay(AUTO_SYNC_INTERVAL_MS)
            }
        }
    }

    fun stopAutoSync() {
        autoSync?.cancel()
        autoSync = null
    }

    private fun providerFor(choice: CloudChoice): CloudProvider? = when (choice) {
        CloudChoice.TEST -> LocalFolderCloudProvider(File(context.filesDir, "test-cloud"))
        // Plugged in once their OAuth clients exist.
        CloudChoice.GOOGLE_DRIVE, CloudChoice.ONEDRIVE, CloudChoice.NONE -> null
    }

    private companion object {
        const val KEY_CHOICE = "cloud"
        const val AUTO_SYNC_INTERVAL_MS = 60_000L
    }
}
