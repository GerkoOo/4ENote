package com.gerko.fourenote

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Where the browser comes back after signing in to Google
 * (com.gerko.fourenote:/oauth2redirect): finishes the sign-in, then returns to the app.
 */
class OAuthRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as FourENoteApp
        val answer = intent?.data
        CoroutineScope(Dispatchers.Main).launch {
            val error = answer?.let { app.googleAuth.handleRedirect(it) } ?: "No answer from Google"
            if (error != null) {
                Toast.makeText(app, error, Toast.LENGTH_LONG).show()
            } else {
                app.syncManager.onGoogleSignedIn()
            }
            startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        }
    }
}
