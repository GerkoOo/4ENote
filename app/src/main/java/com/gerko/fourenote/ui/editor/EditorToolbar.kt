package com.gerko.fourenote.ui.editor

import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.HighlightAlt
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gerko.fourenote.data.ink.Smoothing
import com.gerko.fourenote.data.ink.Tool
import kotlin.math.roundToInt

@Composable
internal fun ToolBar(
    viewModel: NoteEditorViewModel,
    darkPaper: Boolean,
    onOpenDial: () -> Unit,
    onAddColor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Floats over the pages in two rows: the tools, and under them the chosen tool's options.
    // On narrow screens each row swipes sideways for the rest.
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    FloatingRow {
            IconButton(onClick = onOpenDial) {
                Icon(Icons.Filled.DonutLarge, contentDescription = "Quick dial")
            }
            ToolChip("Pen", Icons.Filled.Create, viewModel.tool == Tool.PEN) { viewModel.selectTool(Tool.PEN) }
            ToolChip("Ink pen", Icons.Filled.HistoryEdu, viewModel.tool == Tool.INK_PEN) {
                viewModel.selectTool(Tool.INK_PEN)
            }
            ToolChip("Calligraphy", Icons.Filled.FormatItalic, viewModel.tool == Tool.CALLIGRAPHY) {
                viewModel.selectTool(Tool.CALLIGRAPHY)
            }
            ToolChip("Highlighter", Icons.Filled.BorderColor, viewModel.tool == Tool.HIGHLIGHTER) {
                viewModel.selectTool(Tool.HIGHLIGHTER)
            }
            ToolChip("Eraser", Icons.Outlined.CleaningServices, viewModel.tool == Tool.ERASER) {
                viewModel.selectTool(Tool.ERASER)
            }
            ToolChip("Select", Icons.Filled.HighlightAlt, viewModel.tool == Tool.LASSO) {
                viewModel.selectTool(Tool.LASSO)
            }
            ToolChip("Text", Icons.Filled.Title, viewModel.tool == Tool.TEXT) {
                viewModel.selectTool(Tool.TEXT)
            }
            ToolChip("Sticky", Icons.AutoMirrored.Filled.StickyNote2, viewModel.tool == Tool.STICKY) {
                viewModel.selectTool(Tool.STICKY)
            }
    }
    FloatingRow {
            if (viewModel.tool == Tool.LASSO) {
                SelectionOptions(viewModel)
            } else if (viewModel.tool == Tool.TEXT) {
                TextStyleBar(viewModel, darkPaper)
            } else if (viewModel.tool == Tool.STICKY) {
                StickyOptions(viewModel)
            } else if (viewModel.tool != Tool.ERASER) {
                if (viewModel.tool == Tool.CALLIGRAPHY) {
                    // Pointed: bold going down, thin going up (like a dip pen). Flat: a broad nib, angle on the dial.
                    FilterChip(selected = viewModel.pointedNib, onClick = { viewModel.pointedNib = true }, label = { Text("Pointed") })
                    FilterChip(selected = !viewModel.pointedNib, onClick = { viewModel.pointedNib = false }, label = { Text("Flat") })
                    VerticalDivider(Modifier.height(32.dp).padding(horizontal = 4.dp))
                }
                val highlighter = viewModel.tool == Tool.HIGHLIGHTER
                val colors = if (highlighter) HighlighterColors else PenColors
                val selectedColor = if (highlighter) viewModel.highlighterColor else viewModel.penColor
                (colors + viewModel.customColors.filter { it !in colors }).forEach { c ->
                    val custom = c in viewModel.customColors
                    ColorSwatch(
                        c,
                        selected = c == selectedColor,
                        darkPaper = darkPaper && !highlighter,
                        // Long-press one of your own colors to remove it.
                        onLongClick = if (custom) ({ viewModel.removeCustomColor(c) }) else null,
                    ) {
                        if (highlighter) viewModel.highlighterColor = c else viewModel.penColor = c
                    }
                }
                IconButton(onClick = onAddColor) {
                    Icon(Icons.Filled.Add, contentDescription = "Add a color")
                }

                VerticalDivider(Modifier.height(32.dp).padding(horizontal = 4.dp))
                val inkPen = viewModel.tool == Tool.INK_PEN
                val calligraphy = viewModel.tool == Tool.CALLIGRAPHY
                val widths = when {
                    highlighter -> HighlighterWidths
                    inkPen -> InkPenWidths
                    calligraphy -> CalligraphyWidths
                    else -> PenWidths
                }
                val selectedWidth = when {
                    highlighter -> viewModel.highlighterWidth
                    inkPen -> viewModel.inkPenWidth
                    calligraphy -> viewModel.calligraphyWidth
                    else -> viewModel.penWidth
                }
                widths.forEachIndexed { i, w ->
                    WidthDot(
                        dotSize = (6 + i * 6).dp,
                        selected = w == selectedWidth,
                        color = selectedColor,
                    ) {
                        when {
                            highlighter -> viewModel.highlighterWidth = w
                            inkPen -> viewModel.inkPenWidth = w
                            calligraphy -> viewModel.calligraphyWidth = w
                            else -> viewModel.penWidth = w
                        }
                    }
                }

                if (calligraphy) {
                    VerticalDivider(Modifier.height(32.dp).padding(horizontal = 4.dp))
                    // Flat: the nib's angle. Pointed: the slant of the thick down-strokes.
                    Text(
                        "${if (viewModel.pointedNib) "Slant" else "Nib angle"} ${viewModel.nibDegrees.roundToInt()}°",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Slider(
                        value = viewModel.nibDegrees,
                        onValueChange = { viewModel.nibDegrees = (it / 15f).roundToInt() * 15f },
                        valueRange = 0f..90f,
                        steps = 5,
                        modifier = Modifier.width(150.dp),
                    )
                }
                if (!highlighter) {
                    FilterChip(
                        selected = viewModel.scribbleToErase,
                        onClick = { viewModel.scribbleToErase = !viewModel.scribbleToErase },
                        label = { Text("Scribble or strike out to erase") },
                    )
                }
                VerticalDivider(Modifier.height(32.dp).padding(horizontal = 4.dp))
                Text("Smoothing ${viewModel.smoothing}", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = viewModel.smoothing.toFloat(),
                    onValueChange = { viewModel.smoothing = it.roundToInt() },
                    valueRange = 0f..Smoothing.MAX_LEVEL.toFloat(),
                    steps = Smoothing.MAX_LEVEL - 1,
                    modifier = Modifier.width(180.dp),
                )
            } else {
                FilterChip(
                    selected = !viewModel.eraseWholeStrokes,
                    onClick = { viewModel.eraseWholeStrokes = false },
                    label = { Text("Erase part") },
                )
                FilterChip(
                    selected = viewModel.eraseWholeStrokes,
                    onClick = { viewModel.eraseWholeStrokes = true },
                    label = { Text("Erase whole lines") },
                )
            }
    }
    }
}

/** One floating, rounded row of the tool bar; swipes sideways when it doesn't fit. */
@Composable
private fun FloatingRow(content: @Composable RowScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 4.dp, shadowElevation = 6.dp) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun SelectionOptions(viewModel: NoteEditorViewModel) {
    Hint(
        if (viewModel.selection.isEmpty()) {
            "Draw a loop around writing to select it"
        } else {
            "Drag to move · dots stretch width, height or both"
        },
    )
}

@Composable
private fun Hint(text: String) {
    Spacer(Modifier.width(4.dp))
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The sticky tool's options: the note's colour, and deleting the selected note. */
@Composable
private fun StickyOptions(viewModel: NoteEditorViewModel) {
    StickyColors.forEach { color ->
        val ring = if (color == viewModel.stickyColor) MaterialTheme.colorScheme.primary else Color.Transparent
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .border(3.dp, ring, CircleShape)
                .clickable { viewModel.changeStickyColor(color) }
                .padding(6.dp)
                .clip(CircleShape)
                .background(Color(color))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape),
        )
    }
    if (viewModel.selectedSticky != null) {
        TextButton(onClick = viewModel::deleteSelectedSticky) {
            Icon(Icons.Filled.Delete, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Delete")
        }
    } else {
        Hint("Tap a page to place a sticky note")
    }
}

/** The text tool's options: looks, size, colour, and whether `$…$` becomes maths while typing. */
@Composable
private fun TextStyleBar(viewModel: NoteEditorViewModel, darkPaper: Boolean) {
    val style = viewModel.textStyle
    fun change(new: com.gerko.fourenote.data.ink.TextStyleSpec) = viewModel.changeTextStyle(new)
    TextPreset.entries.forEach { preset ->
        val chosen = style.size == preset.size && style.bold == preset.bold && style.font == preset.font
        FilterChip(
            selected = chosen,
            onClick = { change(style.copy(size = preset.size, bold = preset.bold, font = preset.font)) },
            label = { Text(preset.label) },
        )
    }
    VerticalDivider(Modifier.height(32.dp).padding(horizontal = 4.dp))
    com.gerko.fourenote.data.ink.TextFont.entries.forEach { font ->
        FilterChip(
            selected = style.font == font,
            onClick = { change(style.copy(font = font)) },
            label = { Text(font.name.lowercase().replaceFirstChar { it.uppercase() }) },
        )
    }
    IconButton(onClick = { change(style.copy(size = (style.size - 2f).coerceAtLeast(8f))) }) {
        Icon(Icons.Filled.Remove, contentDescription = "Smaller text")
    }
    Text("${style.size.roundToInt()}", style = MaterialTheme.typography.labelLarge)
    IconButton(onClick = { change(style.copy(size = (style.size + 2f).coerceAtMost(96f))) }) {
        Icon(Icons.Filled.Add, contentDescription = "Bigger text")
    }
    FilterChip(selected = style.bold, onClick = { change(style.copy(bold = !style.bold)) }, label = { Text("B", fontWeight = FontWeight.Bold) })
    FilterChip(selected = style.italic, onClick = { change(style.copy(italic = !style.italic)) }, label = { Text("I", fontStyle = FontStyle.Italic) })
    VerticalDivider(Modifier.height(32.dp).padding(horizontal = 4.dp))
    viewModel.toolColors.forEach { c ->
        ColorSwatch(c, selected = Color(style.color) == c, darkPaper = darkPaper) {
            viewModel.penColor = c
            change(style.copy(color = c.toArgb()))
        }
    }
    VerticalDivider(Modifier.height(32.dp).padding(horizontal = 4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Auto LaTeX", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(8.dp))
        Switch(checked = viewModel.autoLatex, onCheckedChange = viewModel::changeAutoLatex)
    }
    if (viewModel.editingText != null) {
        TextButton(onClick = viewModel::finishTextEditing) { Text("Done") }
    }
}

@Composable
private fun ToolChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
    )
}

@Composable
internal fun ColorSwatch(
    color: Color,
    selected: Boolean,
    darkPaper: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val ring = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .border(3.dp, ring, CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(6.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape),
    )
}

@Composable
private fun WidthDot(dotSize: androidx.compose.ui.unit.Dp, selected: Boolean, color: Color, onClick: () -> Unit) {
    val ring = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .border(3.dp, ring, CircleShape)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.size(dotSize).clip(CircleShape).background(color))
    }
}

/** One pen button's action, as a dropdown. */
@Composable
internal fun PenButtonChoice(label: String, action: PenButtonAction, onChange: (PenButtonAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }) { Text(action.label) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                PenButtonAction.entries.forEach { choice ->
                    DropdownMenuItem(text = { Text(choice.label) }, onClick = { onChange(choice); open = false })
                }
            }
        }
    }
}

/** The floating tool bar's height plus its margins: the pages start below it. */
internal val TOOLBAR_SPACE = 136.dp
