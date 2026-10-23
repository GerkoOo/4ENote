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
}
