package com.gerko.fourenote.ui.focus

import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
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

/** Colours to choose from for the bar: muted and calm, so it doesn't pull at the eye while working. */
private val BarColors = listOf(
    0xFFC08A7A, // terracotta
    0xFFCBB089, // sand
    0xFFA9B38E, // olive
    0xFF93AE9C, // sage
    0xFF86A8A4, // teal grey
    0xFF8C9DB8, // slate blue
    0xFFA59BBE, // lavender
    0xFFC29FAA, // dusty rose
    0xFFA8A098, // warm grey
    0xFF8F99A3, // cool grey
).map { it.toInt() }

/** The colour of [phase] with the chosen [colors] (focus, break). */
private fun color(phase: FocusPhase, colors: Pair<Int, Int>) = Color(if (phase == FocusPhase.WORK) colors.first else colors.second)

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
 * The focus timer as a slim bar that starts full and runs down to the end of the phase, in the
 * focus or break colour, with the phase and time left written in it. Across the top on an upright
 * screen, down the side ([vertical]) on a sideways one, so it takes little room. Tap it for the
 * timer's controls. Nothing while off.
 */
@Composable
fun FocusBar(timer: FocusTimer, vertical: Boolean, onOpen: () -> Unit, onPhase: (FocusPhase) -> Unit, modifier: Modifier = Modifier) {
    val cycle by timer.cycle.collectAsState()
    val plan by timer.plan.collectAsState()
    val colors by timer.colors.collectAsState()
    val c = cycle ?: return
    val now = rememberNow(timer, onPhase)
    val left = c.fractionLeft(plan, now)
    val color = color(c.phase, colors).copy(alpha = if (c.running) 1f else 0.45f)
    val secondsLeft = c.remaining(plan, now) / 1000
    val label = "${c.phase.label}${if (c.running) "" else " (paused)"} · ${secondsLeft / 60}:${"%02d".format(secondsLeft % 60)}"
    // Light or dark text, whichever reads better on the bar's colour.
    val ink = if (color.copy(alpha = 1f).luminance() > 0.5f) Color.Black else Color.White
    Box(
        modifier
            .then(if (vertical) Modifier.fillMaxHeight().width(THICKNESS) else Modifier.fillMaxWidth().height(THICKNESS))
            .background(color.copy(alpha = 0.3f))
            .clickable(onClick = onOpen),
        contentAlignment = if (vertical) Alignment.BottomCenter else Alignment.CenterStart,
    ) {
        Box(
            (if (vertical) Modifier.fillMaxWidth().fillMaxHeight(left) else Modifier.fillMaxHeight().fillMaxWidth(left))
                .background(color),
        )
        // The phase and the time left, along the bar (turned to run down a side bar).
        Text(
            label,
            color = ink,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.Center)
                .then(if (vertical) Modifier.sideways() else Modifier),
        )
    }
}

// Lays the text out lying on its side: measured as if the side bar were a top bar, then turned.
private fun Modifier.sideways() = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = constraints.maxHeight, minHeight = 0, maxHeight = constraints.maxWidth))
    layout(placeable.height, placeable.width) {
        placeable.placeWithLayer(
            x = (placeable.height - placeable.width) / 2,
            y = (placeable.width - placeable.height) / 2,
        ) { rotationZ = -90f }
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
    val colors by timer.colors.collectAsState()
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
                        color = color(c.phase, colors),
                    )
                    LinearProgressIndicator(
                        progress = { c.fractionLeft(plan, now) },
                        color = color(c.phase, colors),
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
                HorizontalDivider()
                Text("Bar colours", style = MaterialTheme.typography.labelLarge)
                ColorChoice("Focus", colors.first) { timer.setColors(it, colors.second) }
                ColorChoice("Break", colors.second) { timer.setColors(colors.first, it) }
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

@Composable
private fun ColorChoice(label: String, selected: Int, onPick: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(56.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            BarColors.forEach { c ->
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .then(if (c == selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                        .clickable { onPick(c) },
                )
            }
        }
    }
}

// Minutes go by 1 up to 10, then by 5.
private fun step(value: Int) = if (value < 10) 1 else 5

private val THICKNESS = 18.dp
