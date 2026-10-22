package com.gerko.fourenote.data.recognition

import org.junit.Assert.assertEquals
import org.junit.Test

class LatexTokenizerTest {
    // "Ġ" stands for a space byte in byte-level BPE.
    private val tokenizer = LatexTokenizer(
        """{"model":{"vocab":{"<pad>":0,"<s>":1,"</s>":2,"<unk>":3,"<mask>":4,"x":5,"Ġ^":6,"Ġ{":7,"Ġ2":8,"Ġ}":9,"\\frac":10,"Ġ\\":11}}}""",
    )

    @Test
    fun decodesTokensWithSpaces() {
        assertEquals("x ^ { 2 }", tokenizer.decode(listOf(5L, 6, 7, 8, 9)))
    }

    @Test
    fun skipsSpecialTokens() {
        assertEquals("x", tokenizer.decode(listOf(1L, 5, 2, 0)))
    }

    @Test
    fun keepsBackslashes() {
        assertEquals("\\frac \\", tokenizer.decode(listOf(10L, 11)))
    }
}
