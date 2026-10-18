package com.gerko.fourenote.sync

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CompletableDeferred

/**
 * Minimal Drive scope needed to create/read/update files *this app* creates. Deliberately not the
 * broad `drive` scope: `drive.file` avoids a scary "see and manage all your files" consent screen
 * and is sufficient since we only ever touch files we uploaded ourselves.
 */
const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

/**
 * Requests scoped access to the user's Google Drive via the Android Authorization API
 * (`com.google.android.gms.auth.api.identity.AuthorizationClient`). This is intentionally separate
 * from [GoogleAuthManager]: Credential Manager proves *who* the user is, this grants *access to
 * their data*. See https://developer.android.com/identity/authorization.
 *
 * Must be constructed in an Activity's onCreate(), before the Activity reaches STARTED, because it
 * registers an [ActivityResultLauncher].
 */
class DriveAuthorizationManager(private val activity: ComponentActivity) {

    private var pendingResolution: CompletableDeferred<AuthorizationResult>? = null

    private val resolutionLauncher: ActivityResultLauncher<IntentSenderRequest> =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { activityResult ->
            val deferred = pendingResolution
            pendingResolution = null
            if (deferred == null) return@registerForActivityResult
            try {
                val result = Identity.getAuthorizationClient(activity)
                    .getAuthorizationResultFromIntent(activityResult.data)
                deferred.complete(result)
            } catch (e: Exception) {
                deferred.completeExceptionally(e)
            }
        }

    /**
     * Returns a valid Drive access token, prompting the user for consent via a system dialog if
     * this is the first time (or if the previously granted access has expired/been revoked).
     */
    suspend fun requestDriveAccessToken(): String {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            .build()

        val initialResult = CompletableDeferred<AuthorizationResult>()
        Identity.getAuthorizationClient(activity)
            .authorize(request)
            .addOnSuccessListener { initialResult.complete(it) }
            .addOnFailureListener { initialResult.completeExceptionally(it) }
        val authorizationResult = initialResult.await()

        val finalResult = if (authorizationResult.hasResolution()) {
            val pendingIntent = authorizationResult.pendingIntent
                ?: throw IllegalStateException(
                    "Drive authorization requires user consent but no resolution intent was provided",
                )
            val resolutionResult = CompletableDeferred<AuthorizationResult>()
            pendingResolution = resolutionResult
            resolutionLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            resolutionResult.await()
        } else {
            authorizationResult
        }

        return finalResult.accessToken
            ?: throw IllegalStateException("Drive authorization did not return an access token")
    }
}
