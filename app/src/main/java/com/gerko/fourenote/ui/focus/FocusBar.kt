package com.gerko.fourenote.ui.focus

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gerko.fourenote.data.focus.FocusPhase
import com.gerko.fourenote.data.focus.FocusTimer
import kotlinx.coroutines.delay

private val FocusColor = Color(0xFFE5533D)
private val BreakColor = Color(0xFF3FA66B)

fun FocusPhase.color() = if (this == FocusPhase.WORK) FocusColor else BreakColor

/** The clock, ticking twice a second while shown; also moves the timer on to the next phase. */
@Composable
private fun rememberNow(timer: FocusTimer, onPhase: (FocusPhase) -> Unit): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val currentOnPhase by rememberUpdatedState(onPhase)
    LaunchedEffect(timer) {
        while (true) {
            now = System.currentTimeMillis()
            timer.tick(now)?.let(currentOnPhase)
            delay(500)
        }
    }
    return now
}

/**
 * The focus timer as a thin bar that starts full and runs down to the end of the phase: red while
 * focusing, green on a break. Across the top on an upright screen, down the side ([vertical]) on a
 * sideways one, so it takes almost no room. Tap it for the timer's controls. Nothing while off.
 */
@Composable
fun FocusBar(timer: FocusTimer, vertical: Boolean, onOpen: () -> Unit, onPhase: (FocusPhase) -> Unit, modifier: Modifier = Modifier) {
    val cycle by timer.cycle.collectAsState()
    val plan by timer.plan.collectAsState()
    val c = cycle ?: return
    val now = rememberNow(timer, onPhase)
    val left = c.fractionLeft(plan, now)
    val color = c.phase.color().copy(alpha = if (c.running) 1f else 0.45f)
    // A wider strip than the bar itself, so it is easy to tap.
    Box(
        modifier
            .then(if (vertical) Modifier.fillMaxHeight().width(TOUCH) else Modifier.fillMaxWidth().height(TOUCH))
            .clickable(onClick = onOpen),
        contentAlignment = if (vertical) Alignment.CenterStart else Alignment.TopCenter,
    ) {
        Box(
            (if (vertical) Modifier.fillMaxHeight().width(THICKNESS) else Modifier.fillMaxWidth().height(THICKNESS))
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = if (vertical) Alignment.BottomCenter else Alignment.CenterStart,
        ) {
            Box(
                (if (vertical) Modifier.fillMaxWidth().fillMaxHeight(left) else Modifier.fillMaxHeight().fillMaxWidth(left))
                    .background(color),
            )
        }
    }
}

/** Short buzz when a phase ends, so a break (or getting back to work) isn't missed. */
fun buzz(context: Context) {
    val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        ?: @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
    runCatching { vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 180), -1)) }
}

/** Starts, pauses, skips and stops the focus timer, and sets how long each phase lasts. */
@Composable
fun FocusDialog(timer: FocusTimer, onDismiss: () -> Unit) {
    val cycle by timer.cycle.collectAsState()
    val plan by timer.plan.collectAsState()
    val context = LocalContext.current
    val now = rememberNow(timer) { buzz(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Focus timer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val c = cycle
                if (c == null) {
                    Text(
                        "Work in focus phases with short breaks between them (Pomodoro). A thin bar shows how much " +
                            "of the phase is left, even in full screen.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    val left = c.remaining(plan, now) / 1000
                    Text(
                        "${c.phase.label}${if (c.running) "" else " (paused)"} · ${left / 60}:${"%02d".format(left % 60)} left",
                        style = MaterialTheme.typography.titleMedium,
                        color = c.phase.color(),
                    )
                    LinearProgressIndicator(
                        progress = { c.fractionLeft(plan, now) },
                        color = c.phase.color(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Focus phases done: ${c.doneWork}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { if (c.running) timer.pause() else timer.resume() }) {
                            Text(if (c.running) "Pause" else "Resume")
                        }
                        FilledTonalButton(onClick = { timer.skip() }) { Text("Skip") }
                        TextButton(onClick = { timer.stop() }) { Text("Turn off") }
                    }
                }
                HorizontalDivider()
                Text("Phases", style = MaterialTheme.typography.labelLarge)
                Stepper("Focus", plan.workMinutes, "min", 1..180) { timer.setPlan(plan.copy(workMinutes = it)) }
                Stepper("Break", plan.breakMinutes, "min", 1..60) { timer.setPlan(plan.copy(breakMinutes = it)) }
                Stepper("Long break", plan.longBreakMinutes, "min", 1..90) { timer.setPlan(plan.copy(longBreakMinutes = it)) }
                Stepper("Long break after", plan.sessionsBeforeLongBreak, "focus phases", 1..12) {
                    timer.setPlan(plan.copy(sessionsBeforeLongBreak = it))
                }
            }
        },
        confirmButton = {
            if (cycle == null) Button(onClick = { timer.start(); onDismiss() }) { Text("Start") }
            else TextButton(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = { if (cycle == null) TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Stepper(label: String, value: Int, unit: String, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((value - step(value)).coerceIn(range)) }, enabled = value > range.first) {
            Icon(Icons.Filled.Remove, contentDescription = "Less")
        }
        Text("$value $unit")
        IconButton(onClick = { onChange((value + step(value)).coerceIn(range)) }, enabled = value < range.last) {
            Icon(Icons.Filled.Add, contentDescription = "More")
        }
    }
}

// Minutes go by 1 up to 10, then by 5.
private fun step(value: Int) = if (value < 10) 1 else 5

private val THICKNESS = 5.dp
private val TOUCH = 12.dp
