package com.gerko.fourenote.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class CryptoSelfTestTest {
    @Test
    fun knownAnswers() {
        assertEquals(emptyList(), CryptoSelfTest.failures())
    }
}
