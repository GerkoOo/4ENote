package com.gerko.fourenote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashTest {
    private val day = 24L * 60 * 60 * 1000
    private val trashed = 1_000_000_000_000L

    @Test
    fun expiresAfterTenDays() {
        assertEquals(trashed + 10 * day, Trash.expiresAt(trashed))
    }

    @Test
    fun notExpiredBeforeTenDays() {
        assertFalse(Trash.isExpired(trashed, trashed + 10 * day - 1))
        assertTrue(Trash.isExpired(trashed, trashed + 10 * day))
        assertTrue(Trash.isExpired(trashed, trashed + 11 * day))
    }

    @Test
    fun daysLeftCountsToday() {
        assertEquals(10, Trash.daysLeft(trashed, trashed))
        assertEquals(10, Trash.daysLeft(trashed, trashed + 1))
        assertEquals(9, Trash.daysLeft(trashed, trashed + day))
        assertEquals(1, Trash.daysLeft(trashed, trashed + 9 * day))
        assertEquals(0, Trash.daysLeft(trashed, trashed + 10 * day))
        assertEquals(0, Trash.daysLeft(trashed, trashed + 20 * day))
    }

    @Test
    fun daysLeftText() {
        assertEquals("Deleted for good in 10 days", Trash.daysLeftText(trashed, trashed))
        assertEquals("Deleted for good in 1 day", Trash.daysLeftText(trashed, trashed + 9 * day))
        assertEquals("Deleted for good today", Trash.daysLeftText(trashed, trashed + 10 * day))
    }
}
