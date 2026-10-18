package com.gerko.fourenote.sync

import androidx.activity.ComponentActivity
import com.gerko.fourenote.R
import com.gerko.fourenote.data.Note
import com.gerko.fourenote.data.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException

/** UI-facing state for the sync entry point in [com.gerko.fourenote.ui.notelist.NoteListScreen]. */
sealed class SyncUiState {
    data object Idle : SyncUiState()
    data object InProgress : SyncUiState()
    data class Success(val syncedCount: Int) : SyncUiState()
    data class Error(val message: String) : SyncUiState()
}

/**
 * Orchestrates the full "sign in, authorize Drive access, upload notes" flow and exposes simple
 * state for the UI to render (loading / success / error). Realistic failure modes — not signed
 * in yet, sign-in cancelled, no network, a failed upload — are all caught and turned into
 * [SyncUiState.Error] rather than being allowed to crash the app.
 *
 * Must be constructed in an Activity's onCreate() (see [DriveAuthorizationManager]).
 */
class DriveSyncManager(
    activity: ComponentActivity,
    private val repository: NoteRepository,
) {
    private val webClientId = activity.getString(R.string.google_oauth_client_id)
    private val authManager = GoogleAuthManager(activity, webClientId)
    private val authorizationManager = DriveAuthorizationManager(activity)
    private val driveApiClient = DriveApiClient()

    private val _state = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    private val _signedInAccountEmail = MutableStateFlow<String?>(null)
    val signedInAccountEmail: StateFlow<String?> = _signedInAccountEmail.asStateFlow()

    /** Signs the user in if needed, requests Drive access if needed, then uploads every note. */
    suspend fun signInAndSyncAll(notes: List<Note>) {
        _state.value = SyncUiState.InProgress
        try {
            val account = ensureSignedIn() ?: run {
                _state.value = SyncUiState.Idle
                return
            }
            _signedInAccountEmail.value = account.email

            val accessToken = authorizationManager.requestDriveAccessToken()
            val folderId = withContext(Dispatchers.IO) {
                driveApiClient.findOrCreateAppFolder(accessToken)
            }

            var syncedCount = 0
            for (note in notes) {
                val driveFileId = withContext(Dispatchers.IO) {
                    driveApiClient.uploadNote(note, accessToken, folderId)
                }
                repository.markSynced(note.id, driveFileId, System.currentTimeMillis())
                syncedCount++
            }
            _state.value = SyncUiState.Success(syncedCount)
        } catch (e: IOException) {
            _state.value = SyncUiState.Error(e.message ?: "Network error while syncing to Google Drive")
        } catch (e: Exception) {
            _state.value = SyncUiState.Error(e.message ?: "Could not sync notes to Google Drive")
        }
    }

    private suspend fun ensureSignedIn(): GoogleAccountInfo? =
        when (val outcome = authManager.signIn()) {
            is SignInOutcome.Success -> outcome.account
            is SignInOutcome.Cancelled -> null
            is SignInOutcome.Failure -> {
                _state.value = SyncUiState.Error(outcome.message)
                null
            }
        }

    /** Signs out and clears local sign-in state (does not un-sync already-uploaded notes). */
    suspend fun signOut() {
        authManager.signOut()
        _signedInAccountEmail.value = null
        _state.value = SyncUiState.Idle
    }

    /** Lets the UI dismiss a one-shot Success/Error message after showing it. */
    fun consumeState() {
        _state.value = SyncUiState.Idle
    }
}
