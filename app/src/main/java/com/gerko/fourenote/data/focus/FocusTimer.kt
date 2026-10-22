package com.gerko.fourenote.data.focus

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The focus (Pomodoro) timer the editor shows as a bar. It keeps time by the clock, so it runs on
 * while notes are switched or the app is closed, and remembers its plan and progress.
 */
class FocusTimer(context: Context) {
    private val prefs = context.getSharedPreferences("focus", Context.MODE_PRIVATE)

    private val _plan = MutableStateFlow(
        FocusPlan(
            workMinutes = prefs.getInt("work", 25),
            breakMinutes = prefs.getInt("break", 5),
            longBreakMinutes = prefs.getInt("long", 15),
            sessionsBeforeLongBreak = prefs.getInt("sessions", 4),
        ),
    )
    val plan: StateFlow<FocusPlan> = _plan

    /** The bar's colours (ARGB): while focusing, and on a break. */
    private val _colors = MutableStateFlow(prefs.getInt("focusColor", DEFAULT_FOCUS_COLOR) to prefs.getInt("breakColor", DEFAULT_BREAK_COLOR))
    val colors: StateFlow<Pair<Int, Int>> = _colors

    fun setColors(focus: Int, rest: Int) {
        _colors.value = focus to rest
        prefs.edit().putInt("focusColor", focus).putInt("breakColor", rest).apply()
    }

    /** The running cycle, or null while the timer is off. */
    private val _cycle = MutableStateFlow(
        if (!prefs.getBoolean("on", false)) null else FocusCycle(
            phase = runCatching { FocusPhase.valueOf(prefs.getString("phase", null) ?: "") }.getOrDefault(FocusPhase.WORK),
            endsAt = prefs.getLong("endsAt", 0L),
            pausedLeft = prefs.getLong("pausedLeft", -1L).takeIf { it >= 0 },
            doneWork = prefs.getInt("doneWork", 0),
        ),
    )
    val cycle: StateFlow<FocusCycle?> = _cycle

    fun start(now: Long = System.currentTimeMillis()) = set(FocusCycle.start(_plan.value, now))

    fun stop() = set(null)

    fun pause(now: Long = System.currentTimeMillis()) = update { it.pause(_plan.value, now) }

    fun resume(now: Long = System.currentTimeMillis()) = update { it.resume(now) }

    fun skip(now: Long = System.currentTimeMillis()) = update { it.skip(_plan.value, now) }

    /** Catches up with the clock; returns the new phase if one began. */
    fun tick(now: Long = System.currentTimeMillis()): FocusPhase? {
        val before = _cycle.value ?: return null
        val after = before.advance(_plan.value, now)
        if (after == before) return null
        set(after)
        return after.phase
    }

    fun setPlan(plan: FocusPlan) {
        _plan.value = plan
        prefs.edit()
            .putInt("work", plan.workMinutes)
            .putInt("break", plan.breakMinutes)
            .putInt("long", plan.longBreakMinutes)
            .putInt("sessions", plan.sessionsBeforeLongBreak)
            .apply()
    }

    companion object {
        const val DEFAULT_FOCUS_COLOR = 0xFFE5533D.toInt()
        const val DEFAULT_BREAK_COLOR = 0xFF3FA66B.toInt()
    }

    private fun update(change: (FocusCycle) -> FocusCycle) {
        _cycle.value?.let { set(change(it)) }
    }

    private fun set(cycle: FocusCycle?) {
        _cycle.value = cycle
        prefs.edit().apply {
            putBoolean("on", cycle != null)
            if (cycle != null) {
                putString("phase", cycle.phase.name)
                putLong("endsAt", cycle.endsAt)
                putLong("pausedLeft", cycle.pausedLeft ?: -1L)
                putInt("doneWork", cycle.doneWork)
            }
        }.apply()
    }
}
