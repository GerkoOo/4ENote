package com.gerko.fourenote.platform

import kotlin.test.Test
import kotlin.test.assertEquals

/** The known answers without AES-GCM (on iOS that needs the app's CryptoKit bridge; the app's -selftest covers it). */
class PlatformTest {
    @Test
    fun knownAnswers() {
        val failures = CryptoSelfTest.failures().filter { !it.startsWith("aes-gcm") || isJvm }
        assertEquals(emptyList(), failures)
    }
}

expect val isJvm: Boolean
