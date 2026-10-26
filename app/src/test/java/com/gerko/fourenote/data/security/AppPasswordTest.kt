package com.gerko.fourenote.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPasswordTest {
    private class MemoryStore : AppPassword.Store {
        override var salt: String? = null
        override var hash: String? = null
        override var iterations: Int = AppPassword.ITERATIONS
        override var failures: Int = 0
        override var blockedUntil: Long = 0L
    }

    private var now = 1_000_000L
    private val store = MemoryStore()
    private val password = AppPassword(store) { now }

    @Test
    fun weakPasswordsAreRefused() {
        assertNotNull(AppPassword.problem("Short1!"))
        assertNotNull(AppPassword.problem("alllowercaseletters"))
        assertNotNull(AppPassword.problem("Password123!")) // common word
        assertNotNull(AppPassword.problem("Abcdefghij1!")) // a run
        assertNotNull(AppPassword.problem("Aaaaaaaa111!")) // too few different characters
        assertNull(AppPassword.problem("Blue-Kettle-77-sings"))
        assertNull(AppPassword.problem("g7#Tr0mb0ne&Sky"))
    }

    @Test
    fun theRightPasswordOpensAndNothingReadableIsKept() {
        password.set("Blue-Kettle-77-sings".toCharArray())
        assertTrue(password.isSet)
        assertFalse(store.hash!!.contains("Kettle"))
        assertEquals(AppPassword.Result.Right, password.check("Blue-Kettle-77-sings".toCharArray()))
        assertEquals(AppPassword.Result.Wrong, password.check("blue-kettle-77-sings".toCharArray()))
    }

    @Test
    fun wrongTriesMakeYouWaitLongerAndLonger() {
        password.set("Blue-Kettle-77-sings".toCharArray())
        repeat(AppPassword.FREE_TRIES - 1) { assertEquals(AppPassword.Result.Wrong, password.check("nope".toCharArray())) }
        val blocked = password.check("nope".toCharArray())
        assertEquals(AppPassword.Result.Blocked(30_000L), blocked)
        // Even the right password waits.
        assertTrue(password.check("Blue-Kettle-77-sings".toCharArray()) is AppPassword.Result.Blocked)
        now += 30_000L
        assertEquals(AppPassword.Result.Blocked(60_000L), password.check("nope".toCharArray()))
        now += 60_000L
        assertEquals(AppPassword.Result.Right, password.check("Blue-Kettle-77-sings".toCharArray()))
        assertEquals(0, store.failures)
    }

    @Test
    fun waitsTopOutAtAnHour() {
        assertEquals(0L, AppPassword.blockFor(4))
        assertEquals(30_000L, AppPassword.blockFor(5))
        assertEquals(60 * 60_000L, AppPassword.blockFor(50))
    }
}
