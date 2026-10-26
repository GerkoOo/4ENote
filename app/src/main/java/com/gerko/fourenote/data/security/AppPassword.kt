package com.gerko.fourenote.data.security

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The 4ENote password: letters, numbers and symbols, used instead of the tablet's screen lock
 * (often a short PIN) when the fingerprint or face isn't used. Only a slow, salted hash is kept,
 * never the password; wrong tries make the next one wait longer and longer.
 */
class AppPassword(private val store: Store, private val clock: () -> Long = System::currentTimeMillis) {
    /** Where the hash and the wrong-try count live; an interface so the logic is testable without Android. */
    interface Store {
        var salt: String?
        var hash: String?
        var iterations: Int
        var failures: Int
        var blockedUntil: Long
    }

    constructor(context: Context) : this(
        object : Store {
            // Its own file, kept out of Android backups (see res/xml/backup_rules.xml).
            private val prefs = context.getSharedPreferences("app-password", Context.MODE_PRIVATE)
            override var salt: String?
                get() = prefs.getString("salt", null)
                set(value) = prefs.edit().putString("salt", value).apply()
            override var hash: String?
                get() = prefs.getString("hash", null)
                set(value) = prefs.edit().putString("hash", value).apply()
            override var iterations: Int
                get() = prefs.getInt("iterations", ITERATIONS)
                set(value) = prefs.edit().putInt("iterations", value).apply()
            override var failures: Int
                get() = prefs.getInt("failures", 0)
                set(value) = prefs.edit().putInt("failures", value).apply()
            override var blockedUntil: Long
                get() = prefs.getLong("blockedUntil", 0L)
                set(value) = prefs.edit().putLong("blockedUntil", value).apply()
        },
    )

    val isSet: Boolean get() = store.hash != null

    /** Milliseconds until the next try is allowed after too many wrong ones; 0 when it is. */
    fun waitMs(): Long = (store.blockedUntil - clock()).coerceAtLeast(0L)

    sealed interface Result {
        data object Right : Result
        data object Wrong : Result
        data class Blocked(val waitMs: Long) : Result
    }

    /** Checks [password]. Slow on purpose (a fraction of a second): call it off the main thread. */
    fun check(password: CharArray): Result {
        val wait = waitMs()
        if (wait > 0) return Result.Blocked(wait)
        val salt = store.salt ?: return Result.Wrong
        val expected = store.hash ?: return Result.Wrong
        val actual = hash(password, decode(salt), store.iterations)
        return if (MessageDigest.isEqual(actual, decode(expected))) {
            store.failures = 0
            store.blockedUntil = 0L
            Result.Right
        } else {
            val failures = store.failures + 1
            store.failures = failures
            val block = blockFor(failures)
            if (block > 0) store.blockedUntil = clock() + block
            if (block > 0) Result.Blocked(block) else Result.Wrong
        }
    }

    /** Sets (or replaces) the password. The caller has checked [problem] first. */
    fun set(password: CharArray) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        store.salt = encode(salt)
        store.iterations = ITERATIONS
        store.hash = encode(hash(password, salt, ITERATIONS))
        store.failures = 0
        store.blockedUntil = 0L
    }

    fun remove() {
        store.hash = null
        store.salt = null
        store.failures = 0
        store.blockedUntil = 0L
    }

    companion object {
        /** PBKDF2-SHA256 rounds, as OWASP advises: each guess costs an attacker this much work. */
        const val ITERATIONS = 600_000
        const val MIN_LENGTH = 12
        /** Wrong tries allowed before the first wait. */
        const val FREE_TRIES = 5

        /** No wait for the first [FREE_TRIES] wrong tries, then 30 s, doubling each time, at most an hour. */
        fun blockFor(failures: Int): Long {
            if (failures < FREE_TRIES) return 0L
            val doublings = (failures - FREE_TRIES).coerceAtMost(7)
            return (30_000L shl doublings).coerceAtMost(60 * 60_000L)
        }

        /** Why [password] isn't good enough, or null if it is. */
        fun problem(password: String): String? {
            if (password.length < MIN_LENGTH) return "Use at least $MIN_LENGTH characters"
            val kinds = listOf(
                password.any { it.isLetter() && it.isLowerCase() },
                password.any { it.isLetter() && it.isUpperCase() },
                password.any { it.isDigit() },
                password.any { !it.isLetterOrDigit() },
            ).count { it }
            if (kinds < 3) return "Mix at least three of: small letters, capital letters, numbers, symbols"
            if (password.toSet().size < 5) return "Use more different characters"
            val plain = password.lowercase().filter { it.isLetterOrDigit() }
            if (COMMON.any { plain.contains(it) && it.length >= plain.length - 4 }) return "Too common: anyone guessing would try it early"
            if (isRun(plain)) return "Avoid runs like 1234 or abcd"
            return null
        }

        // A password that is mostly a run of neighbouring characters (abcdef..., 123456..., 987...).
        private fun isRun(s: String): Boolean {
            if (s.length < 6) return false
            var longest = 1
            var current = 1
            for (i in 1 until s.length) {
                val step = s[i] - s[i - 1]
                current = if (step == 1 || step == -1) current + 1 else 1
                longest = maxOf(longest, current)
            }
            return longest >= s.length - 4
        }

        private val COMMON = listOf(
            "password", "passwort", "motdepasse", "qwerty", "qwertz", "azerty", "letmein", "welcome", "admin",
            "iloveyou", "monkey", "dragon", "football", "baseball", "sunshine", "princess", "master", "shadow",
            "superman", "trustno1", "starwars", "whatever", "secret", "abc123", "123456789", "1q2w3e4r",
            "4enote", "fourenote", "notes", "android", "xiaomi", "samsung",
        )

        private fun hash(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
            val spec = PBEKeySpec(password, salt, iterations, 256)
            try {
                return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
        }

        private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
        private fun decode(text: String) = Base64.getDecoder().decode(text)
    }
}
