package com.gerko.fourenote.data.security

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether 4ENote asks for the fingerprint, face or screen lock before showing anything.
 * Off by default; the setting and the "ask again" delay live in SharedPreferences so they
 * survive a restart.
 */
class AppLock(context: Context) {
    private val prefs = context.getSharedPreferences("app-lock", Context.MODE_PRIVATE)

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    /** True once the user has unlocked in this process; a fresh process starts locked. */
    private var unlockedThisProcess = false

    private val _enabledFlow = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    /** [enabled] as a flow, for what follows the setting (the recent-apps thumbnail). */
    val enabledFlow: StateFlow<Boolean> = _enabledFlow.asStateFlow()

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
            _enabledFlow.value = value
            if (!value) {
                // Turning the lock off shouldn't leave the app locked behind the settings screen.
                unlockedThisProcess = true
                _locked.value = false
            }
        }

    var delayMs: Long
        get() = prefs.getLong(KEY_DELAY, DEFAULT_DELAY_MS)
        set(value) = prefs.edit().putLong(KEY_DELAY, value).apply()

    /** Called when the app comes to the foreground. */
    fun onAppStarted(now: Long) {
        val last = prefs.getLong(KEY_LAST_BACKGROUND, -1L).takeIf { it >= 0 }
        if (shouldLock(enabled, unlockedThisProcess, last, now, delayMs)) _locked.value = true
    }

    /** Called when the app goes to the background; remembers when, for the delay. */
    fun onAppStopped(now: Long) {
        // The PIN screen is a separate activity: leaving for it isn't leaving the app.
        if (Biometric.prompting) return
        prefs.edit().putLong(KEY_LAST_BACKGROUND, now).apply()
    }

    /** The user got past the prompt. */
    fun unlocked() {
        // Forget the last time in the background, so coming back from the prompt doesn't lock again.
        prefs.edit().remove(KEY_LAST_BACKGROUND).apply()
        unlockedThisProcess = true
        _locked.value = false
    }

    companion object {
        const val DEFAULT_DELAY_MS = 60_000L

        /** The "ask again" choices, with their labels. */
        val DELAYS = listOf(0L, 60_000L, 300_000L, 900_000L)
        val DELAY_LABELS = listOf("Immediately", "After 1 minute", "After 5 minutes", "After 15 minutes")

        private const val KEY_ENABLED = "enabled"
        private const val KEY_DELAY = "delayMs"
        private const val KEY_LAST_BACKGROUND = "lastBackgroundAt"

        /**
         * Whether the app should show the lock screen now. Pure so it can be tested without
         * Android: [unlockedThisProcess] is false until the first unlock in this process,
         * [lastBackgroundAt] is null if the app has never been backgrounded.
         */
        fun shouldLock(
            enabled: Boolean,
            unlockedThisProcess: Boolean,
            lastBackgroundAt: Long?,
            now: Long,
            delayMs: Long,
        ): Boolean {
            if (!enabled) return false
            if (!unlockedThisProcess) return true
            if (lastBackgroundAt == null) return false
            return now - lastBackgroundAt >= delayMs
        }
    }
}
