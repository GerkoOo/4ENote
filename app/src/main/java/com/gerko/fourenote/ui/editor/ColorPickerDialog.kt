package com.gerko.fourenote.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/**
 * Mixes a color from hue, saturation, brightness and opacity, or from a hex
 * code (#RRGGBB or #AARRGGBB). The chosen color is added to the palette.
 */
@Composable
fun ColorPickerDialog(initial: Color, onPick: (Color) -> Unit, onDismiss: () -> Unit) {
    val hsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var brightness by remember { mutableFloatStateOf(hsv[2]) }
    var opacity by remember { mutableFloatStateOf(initial.alpha) }
    val color = Color.hsv(hue, saturation, brightness, opacity)
    var hex by remember { mutableStateOf(hexOf(color)) }
    fun update(block: () -> Unit) {
        block()
        hex = hexOf(Color.hsv(hue, saturation, brightness, opacity))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Checkerboard-free preview: the color over both paper colors shows its opacity.
                    Row(Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))) {
                        Box(Modifier.size(48.dp).background(Color.White)) { Box(Modifier.size(48.dp).background(color)) }
                        Box(Modifier.size(48.dp).background(DarkPaperColor)) { Box(Modifier.size(48.dp).background(color)) }
                    }
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { text ->
                            hex = text
                            parseHex(text)?.let { parsed ->
                                FloatArray(3).also { android.graphics.Color.colorToHSV(parsed.toArgb(), it) }.let {
                                    hue = it[0]
                                    saturation = it[1]
                                    brightness = it[2]
                                }
                                opacity = parsed.alpha
                            }
                        },
                        label = { Text("Hex") },
                        singleLine = true,
                        modifier = Modifier.width(170.dp),
                    )
                }
                ColorSlider("Hue", hue, 0f..360f, Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) })) {
                    update { hue = it }
                }
                ColorSlider("Saturation", saturation, 0f..1f, Brush.horizontalGradient(listOf(Color.hsv(hue, 0f, brightness), Color.hsv(hue, 1f, brightness)))) {
                    update { saturation = it }
                }
                ColorSlider("Brightness", brightness, 0f..1f, Brush.horizontalGradient(listOf(Color.Black, Color.hsv(hue, saturation, 1f)))) {
                    update { brightness = it }
                }
                ColorSlider("Opacity", opacity, 0.1f..1f, Brush.horizontalGradient(listOf(color.copy(alpha = 0.1f), color.copy(alpha = 1f)))) {
                    update { opacity = it }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color) }) { Text("Add color") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ColorSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, track: Brush, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(track))
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

private fun hexOf(color: Color): String {
    val argb = color.toArgb()
    return if (color.alpha >= 0.999f) "#%06X".format(argb and 0xFFFFFF) else "#%08X".format(argb)
}

private fun parseHex(text: String): Color? {
    val digits = text.trim().removePrefix("#")
    val value = digits.toLongOrNull(16) ?: return null
    return when (digits.length) {
        6 -> Color((0xFF000000 or value).toInt())
        8 -> Color(value.toInt())
        else -> null
    }
}
