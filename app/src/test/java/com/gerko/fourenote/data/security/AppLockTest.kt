package com.gerko.fourenote.data.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {
    private val minute = 60_000L

    @Test
    fun disabledNeverLocks() {
        assertFalse(AppLock.shouldLock(false, false, null, 0, minute))
        assertFalse(AppLock.shouldLock(false, true, 0, 10 * minute, minute))
    }

    @Test
    fun firstStartLocks() {
        assertTrue(AppLock.shouldLock(true, false, null, 0, minute))
    }

    @Test
    fun withinDelayDoesNotLock() {
        val now = 10 * minute
        assertFalse(AppLock.shouldLock(true, true, now - 30_000, now, minute))
    }

    @Test
    fun atOrAfterDelayLocks() {
        val now = 10 * minute
        assertTrue(AppLock.shouldLock(true, true, now - minute, now, minute))
        assertTrue(AppLock.shouldLock(true, true, now - 5 * minute, now, minute))
    }

    @Test
    fun zeroDelayAlwaysLocksAfterBackground() {
        val now = 10 * minute
        assertTrue(AppLock.shouldLock(true, true, now, now, 0))
    }
}
