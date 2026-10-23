package com.gerko.fourenote.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun comparesVersionsByNumberNotText() {
        assertTrue(UpdateChecker.isNewer("0.3.0", "0.2.0"))
        assertTrue(UpdateChecker.isNewer("v0.10.0", "0.9.5"))
        assertTrue(UpdateChecker.isNewer("1.0", "0.9.9"))
        assertFalse(UpdateChecker.isNewer("0.2.0", "0.2.0"))
        assertFalse(UpdateChecker.isNewer("0.2", "0.2.0"))
        assertFalse(UpdateChecker.isNewer("0.1.9", "0.2.0"))
    }

    @Test
    fun checksOnlyWhenAutomaticAndTheWaitIsOver() {
        val sixHours = 6 * 60 * 60 * 1000L
        val now = 1_000_000_000_000L
        assertFalse(UpdateChecker.isDue(automatic = false, now = now, lastChecked = 0L, every = sixHours))
        assertFalse(UpdateChecker.isDue(automatic = true, now = now, lastChecked = now - sixHours, every = sixHours))
        assertTrue(UpdateChecker.isDue(automatic = true, now = now, lastChecked = now - sixHours - 1, every = sixHours))
    }

    @Test
    fun showsOnlyNewerVersionsNotDismissed() {
        assertTrue(UpdateChecker.worthShowing("0.3.0", "0.2.0", dismissed = null))
        assertFalse(UpdateChecker.worthShowing("0.2.0", "0.2.0", dismissed = null))
        assertFalse(UpdateChecker.worthShowing("0.1.9", "0.2.0", dismissed = null))
        assertFalse(UpdateChecker.worthShowing("0.3.0", "0.2.0", dismissed = "0.3.0"))
        assertTrue(UpdateChecker.worthShowing("0.4.0", "0.2.0", dismissed = "0.3.0"))
        assertFalse(UpdateChecker.worthShowing(null, "0.2.0", dismissed = null))
    }
}
