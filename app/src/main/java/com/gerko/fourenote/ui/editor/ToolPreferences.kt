package com.gerko.fourenote.ui.editor

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.gerko.fourenote.data.ink.Calligraphy
import com.gerko.fourenote.data.ink.Smoothing
import com.gerko.fourenote.data.ink.Tool

/** The pen setup the user last wrote with, restored when any note opens. */
data class ToolSettings(
    val tool: Tool,
    val penColor: Color,
    val highlighterColor: Color,
    val penWidth: Float,
    val inkPenWidth: Float,
    val calligraphyWidth: Float,
    val highlighterWidth: Float,
    val smoothing: Int,
    val stylusOnly: Boolean,
    val scribbleToErase: Boolean,
    val eraseWholeStrokes: Boolean,
    val handwritingLanguage: String?,
    val nibDegrees: Float,
    /** Colors the user mixed with the color picker, shown after the standard palette. */
    val customColors: List<Color>,
    /** The pen's first side button (Android's BUTTON_STYLUS_PRIMARY) and its second one. */
    val primaryButtonAction: PenButtonAction,
    val secondaryButtonAction: PenButtonAction,
    val twoFingerDoubleTapUndo: Boolean,
    val threeFingerDoubleTapRedo: Boolean,
    /** New text boxes typeset `$…$` as maths while typing. */
    val autoLatex: Boolean = true,
    /** Calligraphy with a pointed pen (bold down-strokes, like Notewise) rather than a flat nib. */
    val pointedNib: Boolean = true,
    /** With any tool, holding the pen and a finger on a picture selects it. */
    val penAndFingerSelect: Boolean = false,
)

/**
 * What a pen side button does. Writing with it held lends the tool to that one stroke; clicking it
 * while the pen hovers switches to the tool (or back, if it's already selected).
 */
enum class PenButtonAction(val label: String, val tool: Tool? = null) {
    OPEN_DIAL("Open the quick dial"),
    ERASE("Eraser", Tool.ERASER),
    SELECT("Lasso select", Tool.LASSO),
    HIGHLIGHT("Highlighter", Tool.HIGHLIGHTER),
    LAST_TOOL("Switch to the last tool"),
    NONE("Nothing"),
}

class ToolPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("tools", Context.MODE_PRIVATE)
    private val ui = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    fun load(): ToolSettings {
        // First run: a pen that shows up on the paper (white on dark paper, black on light).
        val darkTheme = ui.getBoolean("dark_theme", true)
        val firstPenColor = if (darkTheme) InkWhite else InkBlack
        return ToolSettings(
            tool = runCatching { Tool.valueOf(prefs.getString("tool", null)!!) }.getOrDefault(Tool.PEN),
            penColor = color("penColor") ?: firstPenColor,
            highlighterColor = color("highlighterColor") ?: HighlighterColors[0],
            penWidth = prefs.getFloat("penWidth", PenWidths[1]),
            inkPenWidth = prefs.getFloat("inkPenWidth", InkPenWidths[1]),
            calligraphyWidth = prefs.getFloat("calligraphyWidth", CalligraphyWidths[1]),
            highlighterWidth = prefs.getFloat("highlighterWidth", HighlighterWidths[1]),
            smoothing = prefs.getInt("smoothing", Smoothing.DEFAULT_LEVEL),
            stylusOnly = prefs.getBoolean("stylusOnly", false),
            scribbleToErase = prefs.getBoolean("scribbleToErase", true),
            eraseWholeStrokes = prefs.getBoolean("eraseWholeStrokes", false),
            handwritingLanguage = prefs.getString("handwritingLanguage", null),
            nibDegrees = prefs.getFloat("nibDegrees", Calligraphy.DEFAULT_NIB_DEGREES),
            customColors = prefs.getString("customColors", "").orEmpty()
                .split(',').mapNotNull { it.toIntOrNull()?.let(::Color) },
            // "penButtonAction" is the single setting older versions had.
            primaryButtonAction = action("primaryButtonAction") ?: action("penButtonAction") ?: PenButtonAction.OPEN_DIAL,
            secondaryButtonAction = action("secondaryButtonAction") ?: PenButtonAction.ERASE,
            twoFingerDoubleTapUndo = prefs.getBoolean("twoFingerDoubleTapUndo", true),
            threeFingerDoubleTapRedo = prefs.getBoolean("threeFingerDoubleTapRedo", true),
            penAndFingerSelect = prefs.getBoolean("penAndFingerSelect", false),
            autoLatex = prefs.getBoolean("autoLatex", true),
            pointedNib = prefs.getBoolean("pointedNib", true),
        )
    }

    fun save(settings: ToolSettings) {
        prefs.edit()
            // The eraser, lasso, text and sticky tools aren't remembered: a note should always open ready to write.
            .apply { if (settings.tool !in setOf(Tool.ERASER, Tool.LASSO, Tool.TEXT, Tool.STICKY)) putString("tool", settings.tool.name) }
            .putInt("penColor", settings.penColor.toArgb())
            .putInt("highlighterColor", settings.highlighterColor.toArgb())
            .putFloat("penWidth", settings.penWidth)
            .putFloat("inkPenWidth", settings.inkPenWidth)
            .putFloat("calligraphyWidth", settings.calligraphyWidth)
            .putFloat("highlighterWidth", settings.highlighterWidth)
            .putInt("smoothing", settings.smoothing)
            .putBoolean("stylusOnly", settings.stylusOnly)
            .putBoolean("scribbleToErase", settings.scribbleToErase)
            .putBoolean("eraseWholeStrokes", settings.eraseWholeStrokes)
            .putString("handwritingLanguage", settings.handwritingLanguage)
            .putFloat("nibDegrees", settings.nibDegrees)
            .putString("customColors", settings.customColors.joinToString(",") { it.toArgb().toString() })
            .putString("primaryButtonAction", settings.primaryButtonAction.name)
            .putString("secondaryButtonAction", settings.secondaryButtonAction.name)
            .putBoolean("twoFingerDoubleTapUndo", settings.twoFingerDoubleTapUndo)
            .putBoolean("threeFingerDoubleTapRedo", settings.threeFingerDoubleTapRedo)
            .putBoolean("penAndFingerSelect", settings.penAndFingerSelect)
            .putBoolean("autoLatex", settings.autoLatex)
            .putBoolean("pointedNib", settings.pointedNib)
            .apply()
    }

    private fun action(key: String): PenButtonAction? =
        PenButtonAction.entries.firstOrNull { it.name == prefs.getString(key, null) }

    private fun color(key: String): Color? = if (prefs.contains(key)) Color(prefs.getInt(key, 0)) else null
}
