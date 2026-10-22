package com.gerko.fourenote.data.focus

/** The phases of a focus (Pomodoro) cycle. */
enum class FocusPhase(val label: String) {
    WORK("Focus"),
    BREAK("Break"),
    LONG_BREAK("Long break"),
}

/** How long each phase lasts, in minutes, and how many focus phases come before a long break. */
data class FocusPlan(
    val workMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val sessionsBeforeLongBreak: Int = 4,
) {
    fun millis(phase: FocusPhase): Long = 60_000L * when (phase) {
        FocusPhase.WORK -> workMinutes
        FocusPhase.BREAK -> breakMinutes
        FocusPhase.LONG_BREAK -> longBreakMinutes
    }.coerceAtLeast(1)
}

/**
 * Where a focus cycle is: the current [phase], when it ends ([endsAt], wall-clock milliseconds)
 * while running, or how much of it was left when paused ([pausedLeft]). [doneWork] counts the
 * focus phases finished so far, to know when a long break is due. Pure, so it is easy to test.
 */
data class FocusCycle(
    val phase: FocusPhase = FocusPhase.WORK,
    val endsAt: Long = 0L,
    val pausedLeft: Long? = null,
    val doneWork: Int = 0,
) {
    val running: Boolean get() = pausedLeft == null

    fun remaining(plan: FocusPlan, now: Long): Long =
        (pausedLeft ?: (endsAt - now)).coerceIn(0L, plan.millis(phase))

    /** How much of the phase is still to go, 1 (just started) to 0 (over): the bar's length. */
    fun fractionLeft(plan: FocusPlan, now: Long): Float = remaining(plan, now).toFloat() / plan.millis(phase)

    fun pause(plan: FocusPlan, now: Long) = if (running) copy(pausedLeft = remaining(plan, now)) else this

    fun resume(now: Long) = pausedLeft?.let { copy(endsAt = now + it, pausedLeft = null) } ?: this

    /** Straight on to the next phase, starting now (kept paused if paused). */
    fun skip(plan: FocusPlan, now: Long): FocusCycle {
        val next = next(plan)
        return if (running) next.copy(endsAt = now + plan.millis(next.phase)) else next.copy(pausedLeft = plan.millis(next.phase))
    }

    /** Moves past every phase that has ended by [now] (the app may have been closed through several). */
    fun advance(plan: FocusPlan, now: Long): FocusCycle {
        if (!running) return this
        var cycle = this
        var guard = 0
        while (now >= cycle.endsAt && guard++ < 1000) {
            val next = cycle.next(plan)
            cycle = next.copy(endsAt = cycle.endsAt + plan.millis(next.phase))
        }
        return cycle
    }

    private fun next(plan: FocusPlan): FocusCycle = when (phase) {
        FocusPhase.WORK -> {
            val done = doneWork + 1
            val long = plan.sessionsBeforeLongBreak > 0 && done % plan.sessionsBeforeLongBreak == 0
            copy(phase = if (long) FocusPhase.LONG_BREAK else FocusPhase.BREAK, doneWork = done)
        }
        else -> copy(phase = FocusPhase.WORK)
    }

    companion object {
        fun start(plan: FocusPlan, now: Long) = FocusCycle(endsAt = now + plan.millis(FocusPhase.WORK))
    }
}
