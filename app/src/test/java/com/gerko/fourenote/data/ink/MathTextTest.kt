package com.gerko.fourenote.data.ink

import com.gerko.fourenote.data.ink.MathText.Part.Math
import com.gerko.fourenote.data.ink.MathText.Part.Plain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MathTextTest {
    @Test
    fun splitsInlineMath() {
        assertEquals(
            listOf(Plain("Area is "), Math("\\pi r^2", display = false), Plain(" here")),
            MathText.split("Area is \$\\pi r^2\$ here"),
        )
    }

    @Test
    fun splitsDisplayMath() {
        assertEquals(
            listOf(Plain("So\n"), Math("\\int_0^1 x\\,dx", display = true), Plain("\ndone")),
            MathText.split("So\n\$\$ \\int_0^1 x\\,dx \$\$\ndone"),
        )
    }

    @Test
    fun keepsUnclosedDollarsWhileTyping() {
        assertEquals(listOf(Plain("costs \$x^2 so far")), MathText.split("costs \$x^2 so far"))
        assertFalse(MathText.hasMath("\$\$ x"))
        // An unclosed inline formula stops at the end of its line.
        assertEquals(
            listOf(Plain("a \$b\nc "), Math("d", display = false)),
            MathText.split("a \$b\nc \$d\$"),
        )
    }

    @Test
    fun escapedDollarIsADollarSign() {
        assertEquals(listOf(Plain("price \$5 and "), Math("y", false)), MathText.split("price \\\$5 and \$y\$"))
        assertEquals(listOf(Math("a\\\$b", false)), MathText.split("\$a\\\$b\$"))
    }

    @Test
    fun emptyFormulaStaysText() {
        assertEquals(listOf(Plain("\$\$\$\$")), MathText.split("\$\$\$\$"))
        assertEquals(listOf(Plain("a \$ \$ b")), MathText.split("a \$ \$ b"))
        assertTrue(MathText.hasMath("x \$a\$"))
    }
}
