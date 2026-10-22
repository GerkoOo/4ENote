package com.gerko.fourenote.data.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusCycleTest {
    private val plan = FocusPlan(workMinutes = 25, breakMinutes = 5, longBreakMinutes = 15, sessionsBeforeLongBreak = 2)
    private val min = 60_000L

    @Test
    fun theBarStartsFullAndRunsDown() {
        val cycle = FocusCycle.start(plan, 0)
        assertEquals(1f, cycle.fractionLeft(plan, 0), 0.001f)
        assertEquals(0.6f, cycle.fractionLeft(plan, 10 * min), 0.001f)
    }

    @Test
    fun focusThenBreakThenFocusThenLongBreak() {
        var cycle = FocusCycle.start(plan, 0)
        cycle = cycle.advance(plan, 25 * min)
        assertEquals(FocusPhase.BREAK, cycle.phase)
        assertEquals(30 * min, cycle.endsAt)
        cycle = cycle.advance(plan, 30 * min)
        assertEquals(FocusPhase.WORK, cycle.phase)
        cycle = cycle.advance(plan, 55 * min)
        assertEquals(FocusPhase.LONG_BREAK, cycle.phase)
        assertEquals(70 * min, cycle.endsAt)
    }

    @Test
    fun catchesUpAfterTheAppWasClosed() {
        // 25 + 5 + 25 minutes later, plus 3: three minutes into the long break.
        val cycle = FocusCycle.start(plan, 0).advance(plan, 58 * min)
        assertEquals(FocusPhase.LONG_BREAK, cycle.phase)
        assertEquals(12 * min, cycle.remaining(plan, 58 * min))
    }

    @Test
    fun pausingStopsTheClock() {
        var cycle = FocusCycle.start(plan, 0).pause(plan, 10 * min)
        assertFalse(cycle.running)
        assertEquals(cycle, cycle.advance(plan, 100 * min))
        assertEquals(15 * min, cycle.remaining(plan, 100 * min))
        cycle = cycle.resume(100 * min)
        assertTrue(cycle.running)
        assertEquals(115 * min, cycle.endsAt)
    }

    @Test
    fun skipGoesStraightToTheNextPhase() {
        val cycle = FocusCycle.start(plan, 0).skip(plan, 3 * min)
        assertEquals(FocusPhase.BREAK, cycle.phase)
        assertEquals(8 * min, cycle.endsAt)
    }
}
