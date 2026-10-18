package com.gerko.fourenote.ui.editor

import android.util.Base64
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.storage.decode
import androidx.ink.storage.encode
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInputBatch
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes/deserializes the strokes drawn in [NoteEditorScreen] to/from [com.gerko.fourenote.data.Note.inkData].
 *
 * Format: a small JSON envelope (`{"version":1,"strokes":[...]}`) where each stroke entry stores
 *   - "inputs": Base64 of the stroke's [StrokeInputBatch] encoded via androidx.ink.storage's own
 *     protobuf-based `encode`/`decode` extensions (position, pressure, tilt, timing, etc. for every
 *     recorded point) -- this is the serialization helper the androidx.ink team ships for exactly
 *     this purpose, so hand-rolling point serialization is unnecessary.
 *   - "colorLong", "size", "epsilon": the [Brush] parameters that aren't part of the input batch.
 *
 * All strokes currently share one [androidx.ink.brush.BrushFamily] (pressure-sensitive "pressure
 * pen"); only color and size vary per stroke. See PR description for why brush-family selection
 * (marker/highlighter/etc.) was left out of this first version.
 */
object InkSerialization {
    private const val CURRENT_VERSION = 1
    private const val KEY_VERSION = "version"
    private const val KEY_STROKES = "strokes"
    private const val KEY_INPUTS = "inputs"
    private const val KEY_COLOR_LONG = "colorLong"
    private const val KEY_SIZE = "size"
    private const val KEY_EPSILON = "epsilon"

    /** The single brush family supported by this version of the editor. */
    val brushFamily = StockBrushes.pressurePen()

    fun encode(strokes: List<Stroke>): String {
        val strokesJson = JSONArray()
        for (stroke in strokes) {
            val inputBytes = ByteArrayOutputStream().use { out ->
                stroke.inputs.encode(out)
                out.toByteArray()
            }
            val strokeJson = JSONObject()
            strokeJson.put(KEY_INPUTS, Base64.encodeToString(inputBytes, Base64.NO_WRAP))
            strokeJson.put(KEY_COLOR_LONG, stroke.brush.colorLong)
            strokeJson.put(KEY_SIZE, stroke.brush.size.toDouble())
            strokeJson.put(KEY_EPSILON, stroke.brush.epsilon.toDouble())
            strokesJson.put(strokeJson)
        }
        val root = JSONObject()
        root.put(KEY_VERSION, CURRENT_VERSION)
        root.put(KEY_STROKES, strokesJson)
        return root.toString()
    }

    fun decode(inkData: String): List<Stroke> {
        if (inkData.isBlank()) return emptyList()
        return try {
            val root = JSONObject(inkData)
            val strokesJson = root.optJSONArray(KEY_STROKES) ?: return emptyList()
            buildList {
                for (i in 0 until strokesJson.length()) {
                    val strokeJson = strokesJson.getJSONObject(i)
                    val inputBytes = Base64.decode(strokeJson.getString(KEY_INPUTS), Base64.NO_WRAP)
                    val inputs = ByteArrayInputStream(inputBytes).use { StrokeInputBatch.decode(it) }
                    val brush = Brush.createWithColorLong(
                        family = brushFamily,
                        colorLong = strokeJson.getLong(KEY_COLOR_LONG),
                        size = strokeJson.getDouble(KEY_SIZE).toFloat(),
                        epsilon = strokeJson.getDouble(KEY_EPSILON).toFloat(),
                    )
                    add(Stroke(brush, inputs))
                }
            }
        } catch (_: Exception) {
            // Corrupt or unrecognized inkData shouldn't crash the editor; start from a blank canvas.
            emptyList()
        }
    }
}
