package com.gerko.fourenote.data.security

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.gerko.fourenote.FourENoteApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Every "prove it's you" in the app goes through here. With a 4ENote password set, the fingerprint
 * or face is asked first and the password is the way around it; the tablet's screen lock (often a
 * short PIN) no longer opens anything. Without one, the fingerprint, face or screen lock is asked.
 */
object Unlock {
    /** A password dialog to show (see PasswordPrompt), with what to do after. */
    class Request(val title: String, val onSuccess: () -> Unit, val onFail: (String?) -> Unit)

    private val _passwordRequest = MutableStateFlow<Request?>(null)
    val passwordRequest: StateFlow<Request?> = _passwordRequest.asStateFlow()

    private fun password(context: Context) = (context.applicationContext as FourENoteApp).appPassword

    /** Whether anything can be asked at all: a 4ENote password, or a fingerprint, face or screen lock. */
    fun canUnlock(context: Context): Boolean = password(context).isSet || Biometric.canAuthenticate(context)

    fun authenticate(activity: FragmentActivity, title: String, onSuccess: () -> Unit, onFail: (String?) -> Unit) {
        if (!password(activity).isSet) {
            Biometric.authenticate(activity, title, onSuccess, onFail)
            return
        }
        val askPassword = { _passwordRequest.value = Request(title, onSuccess, onFail) }
        if (Biometric.canUseBiometrics(activity)) {
            Biometric.authenticateBiometric(activity, title, onSuccess, onFail, onUsePassword = askPassword)
        } else {
            askPassword()
        }
    }

    /** The password dialog closed: right password ([ok]) or cancelled. */
    fun finishPassword(ok: Boolean) {
        val request = _passwordRequest.value ?: return
        _passwordRequest.value = null
        if (ok) request.onSuccess() else request.onFail(null)
    }
}
