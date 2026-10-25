package com.gerko.fourenote.data.security

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which locked notes have been unlocked in this session. Nothing is remembered across a restart:
 * leaving the app (or the screen going off) locks every note again.
 */
class NoteLock(private val settings: Settings) {
    /** Where the one persisted choice lives; a small interface so the logic is testable without Android. */
    interface Settings {
        var unlockAllAtOnce: Boolean
    }

    constructor(context: Context) : this(
        object : Settings {
            private val prefs = context.getSharedPreferences("note-lock", Context.MODE_PRIVATE)
            override var unlockAllAtOnce: Boolean
                get() = prefs.getBoolean(KEY_UNLOCK_ALL, false)
                set(value) = prefs.edit().putBoolean(KEY_UNLOCK_ALL, value).apply()
        },
    )

    private val _unlocked = MutableStateFlow<Set<String>>(emptySet())
    val unlocked: StateFlow<Set<String>> = _unlocked.asStateFlow()

    private val _allUnlocked = MutableStateFlow(false)
    val allUnlocked: StateFlow<Boolean> = _allUnlocked.asStateFlow()

    /** After one unlock, the other locked notes open too, until the app is left. */
    var unlockAllAtOnce: Boolean
        get() = settings.unlockAllAtOnce
        set(value) {
            settings.unlockAllAtOnce = value
            // Turning it off doesn't take back what is already open; it only stops the next unlock spreading.
        }

    fun isUnlocked(noteId: String): Boolean = _allUnlocked.value || noteId in _unlocked.value

    /** The user got past the prompt for [noteId]. */
    fun unlocked(noteId: String) {
        _unlocked.value = _unlocked.value + noteId
        if (unlockAllAtOnce) _allUnlocked.value = true
    }

    /** The note was closed: it asks again next time. */
    fun relock(noteId: String) {
        _unlocked.value = _unlocked.value - noteId
    }

    /** Leaving the app: every note asks again. */
    fun relockAll() {
        _unlocked.value = emptySet()
        _allUnlocked.value = false
    }

    private companion object {
        const val KEY_UNLOCK_ALL = "unlockAllAtOnce"
    }
}
