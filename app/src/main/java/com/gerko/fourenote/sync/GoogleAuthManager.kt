package com.gerko.fourenote.sync

import android.content.Context
import android.util.Base64
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.security.SecureRandom

/** The signed-in Google account, as returned by Credential Manager. */
data class GoogleAccountInfo(val email: String, val idToken: String)

/** Outcome of a [GoogleAuthManager.signIn] attempt. */
sealed class SignInOutcome {
    data class Success(val account: GoogleAccountInfo) : SignInOutcome()
    data object Cancelled : SignInOutcome()
    data class Failure(val message: String) : SignInOutcome()
}

/**
 * Handles "Sign in with Google" using Credential Manager (androidx.credentials), the API Google
 * now recommends over the deprecated GoogleSignIn/GoogleSignInClient classes.
 *
 * Credential Manager only handles *authentication* (proving who the user is). It intentionally
 * does not grant access to Google APIs like Drive — that is a separate step handled by
 * [DriveAuthorizationManager] using the Android Authorization API.
 *
 * [webClientId] must be the OAuth "Web application" client ID from the Google Cloud project that
 * also has an Android OAuth client registered for this app's package name + SHA-1 fingerprint.
 * See docs/google-drive-setup.md.
 */
class GoogleAuthManager(
    private val context: Context,
    private val webClientId: String,
) {
    private val credentialManager = CredentialManager.create(context)

    /**
     * Prompts the user to sign in. First tries accounts already authorized for this app; if none
     * exist, falls back to showing every Google account on the device.
     */
    suspend fun signIn(): SignInOutcome {
        val firstAttempt = trySignIn(filterByAuthorizedAccounts = true)
        if (firstAttempt !is SignInOutcome.Failure) return firstAttempt
        return trySignIn(filterByAuthorizedAccounts = false)
    }

    @Suppress("DEPRECATION") // `id` fallback below is intentional when `email` isn't populated.
    private suspend fun trySignIn(filterByAuthorizedAccounts: Boolean): SignInOutcome {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(filterByAuthorizedAccounts)
            .setNonce(generateNonce())
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        return try {
            val response = credentialManager.getCredential(context, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val tokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val displayIdentifier = tokenCredential.email ?: tokenCredential.id
                SignInOutcome.Success(GoogleAccountInfo(displayIdentifier, tokenCredential.idToken))
            } else {
                SignInOutcome.Failure("Credential Manager returned an unexpected credential type")
            }
        } catch (e: GetCredentialCancellationException) {
            SignInOutcome.Cancelled
        } catch (e: GetCredentialException) {
            SignInOutcome.Failure(e.message ?: "Sign-in failed")
        } catch (e: GoogleIdTokenParsingException) {
            SignInOutcome.Failure("Could not parse the Google ID token")
        }
    }

    suspend fun signOut() {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }

    private fun generateNonce(byteLength: Int = 32): String {
        val randomBytes = ByteArray(byteLength)
        SecureRandom().nextBytes(randomBytes)
        return Base64.encodeToString(randomBytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }
}
