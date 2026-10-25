package com.gerko.fourenote.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteLockTest {
    private class MemorySettings(override var unlockAllAtOnce: Boolean = false) : NoteLock.Settings

    private val settings = MemorySettings()
    private val lock = NoteLock(settings)

    @Test
    fun nothingIsUnlockedAtFirst() {
        assertFalse(lock.isUnlocked("n1"))
        assertFalse(lock.allUnlocked.value)
    }

    @Test
    fun anUnlockedNoteStaysOpenUntilItIsClosed() {
        lock.unlocked("n1")
        assertTrue(lock.isUnlocked("n1"))
        assertFalse(lock.isUnlocked("n2"))
        lock.relock("n1")
        assertFalse(lock.isUnlocked("n1"))
    }

    @Test
    fun relockingOneNoteLeavesTheOthersOpen() {
        lock.unlocked("n1")
        lock.unlocked("n2")
        lock.relock("n1")
        assertFalse(lock.isUnlocked("n1"))
        assertTrue(lock.isUnlocked("n2"))
    }

    @Test
    fun unlockAllAtOnceOpensEveryNoteAfterOneUnlock() {
        settings.unlockAllAtOnce = true
        lock.unlocked("n1")
        assertTrue(lock.allUnlocked.value)
        assertTrue(lock.isUnlocked("n2"))
        // Closing one note doesn't close the others.
        lock.relock("n1")
        assertTrue(lock.isUnlocked("n2"))
    }

    @Test
    fun withoutUnlockAllAtOnceOnlyTheNoteItselfOpens() {
        lock.unlocked("n1")
        assertFalse(lock.allUnlocked.value)
        assertFalse(lock.isUnlocked("n2"))
    }

    @Test
    fun relockAllClosesEverything() {
        settings.unlockAllAtOnce = true
        lock.unlocked("n1")
        lock.relockAll()
        assertFalse(lock.allUnlocked.value)
        assertFalse(lock.isUnlocked("n1"))
        assertFalse(lock.isUnlocked("n2"))
        assertEquals(emptySet<String>(), lock.unlocked.value)
    }

    @Test
    fun theChoiceIsKeptInTheSettings() {
        lock.unlockAllAtOnce = true
        assertTrue(settings.unlockAllAtOnce)
        assertTrue(NoteLock(settings).unlockAllAtOnce)
    }
}
