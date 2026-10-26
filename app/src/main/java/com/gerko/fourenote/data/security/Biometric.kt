package com.gerko.fourenote.data.security

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** The fingerprint, face or screen-lock prompt the app lock uses. */
object Biometric {
    /** True while a prompt is up; its PIN screen stops the app's activity for a moment. */
    @Volatile
    var prompting = false
        private set

    private const val ALLOWED =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /** True when the device can ask for a fingerprint, face or screen lock. */
    fun canAuthenticate(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.canAuthenticate(ALLOWED)
        } else {
            // DEVICE_CREDENTIAL can't be combined with biometrics before API 30.
            manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        }
        if (result == BiometricManager.BIOMETRIC_SUCCESS) return true
        // Older devices without biometrics can still use the screen lock.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            return keyguard.isDeviceSecure
        }
        return false
    }

    /** True when a fingerprint or face is set up (not just a screen lock). */
    fun canUseBiometrics(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Fingerprint or face only, with a button for the 4ENote password instead of the screen lock:
     * [onUsePassword] runs when it is tapped, or when too many tries locked the sensor.
     */
    fun authenticateBiometric(
        activity: FragmentActivity,
        title: String,
        onSuccess: () -> Unit,
        onFail: (String?) -> Unit,
        onUsePassword: () -> Unit,
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompting = false
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    prompting = false
                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON, BiometricPrompt.ERROR_LOCKOUT,
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT, BiometricPrompt.ERROR_NO_BIOMETRICS -> onUsePassword()
                        BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_CANCELED -> onFail(null)
                        else -> onFail(errString.toString())
                    }
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setNegativeButtonText("Use 4ENote password")
            .build()
        prompting = true
        prompt.authenticate(info)
    }

    /**
     * Shows the prompt. [onSuccess] runs when the user gets through; [onFail] gets a message,
     * or null when the user simply cancelled (that isn't an error).
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        onSuccess: () -> Unit,
        onFail: (String?) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompting = false
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    prompting = false
                    val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    onFail(if (cancelled) null else errString.toString())
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setAllowedAuthenticators(ALLOWED)
                } else {
                    // The only way to allow the PIN before Android 11; its replacement needs Android 11.
                    @Suppress("DEPRECATION")
                    setDeviceCredentialAllowed(true)
                }
            }
            .build()
        prompting = true
        prompt.authenticate(info)
    }
}
