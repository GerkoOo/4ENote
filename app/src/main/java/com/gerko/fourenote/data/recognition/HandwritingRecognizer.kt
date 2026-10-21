package com.gerko.fourenote.data.recognition

import com.google.android.gms.tasks.Task
import com.gerko.fourenote.data.ink.InkStroke
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A handwriting language offered in the app; [tag] is a BCP 47 language tag. */
data class HandwritingLanguage(val tag: String, val label: String)

val HandwritingLanguages = listOf(
    HandwritingLanguage("en-US", "English"),
    HandwritingLanguage("de-DE", "Deutsch"),
    HandwritingLanguage("nl-NL", "Nederlands"),
    HandwritingLanguage("fr-FR", "Français"),
    HandwritingLanguage("es-ES", "Español"),
    HandwritingLanguage("it-IT", "Italiano"),
    HandwritingLanguage("tr-TR", "Türkçe"),
    HandwritingLanguage("ar", "العربية"),
)

/**
 * Turns handwriting into text with Google ML Kit Digital Ink Recognition,
 * on the device. Each language's model (about 20 MB) downloads once on first
 * use; after that recognition works offline.
 */
class HandwritingRecognizer {
    private val recognizers = mutableMapOf<String, DigitalInkRecognizer>()

    suspend fun isDownloaded(languageTag: String): Boolean =
        RemoteModelManager.getInstance().isModelDownloaded(model(languageTag)).await()

    /**
     * Recognizes [strokes] line by line (see [HandwritingLines]). Returns the
     * best reading first, followed by alternatives; alternatives are only
     * offered for single-line text, where they are meaningful.
     */
    suspend fun recognize(strokes: List<InkStroke>, languageTag: String): List<String> {
        val model = model(languageTag)
        val manager = RemoteModelManager.getInstance()
        if (!manager.isModelDownloaded(model).await()) {
            manager.download(model, DownloadConditions.Builder().build()).await()
        }
        val recognizer = recognizers.getOrPut(languageTag) {
            DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
        }

        val lines = HandwritingLines.split(strokes)
        val readings = mutableListOf<List<String>>()
        var preContext = ""
        for (line in lines) {
            val ink = Ink.builder()
            for (stroke in line) {
                val builder = Ink.Stroke.builder()
                for (i in stroke.points.indices step 2) builder.addPoint(Ink.Point.create(stroke.points[i], stroke.points[i + 1]))
                ink.addStroke(builder.build())
            }
            val bounds = HandwritingLines.bounds(line)
            val context = RecognitionContext.builder()
                .setPreContext(preContext)
                .setWritingArea(WritingArea(bounds.width.coerceAtLeast(1f), bounds.height.coerceAtLeast(1f)))
                .build()
            val candidates = recognizer.recognize(ink.build(), context).await().candidates.map { it.text }
            readings += candidates
            // Earlier words help the recognizer read the next line.
            preContext = (preContext + " " + candidates.firstOrNull().orEmpty()).takeLast(20).trim()
        }
        if (readings.isEmpty()) return emptyList()
        if (readings.size == 1) return readings.single()
        return listOf(readings.joinToString("\n") { it.firstOrNull().orEmpty() })
    }

    fun close() {
        recognizers.values.forEach { it.close() }
        recognizers.clear()
    }

    private fun model(languageTag: String): DigitalInkRecognitionModel {
        val identifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag(languageTag)
            ?: throw IllegalArgumentException("Handwriting in $languageTag isn't supported")
        return DigitalInkRecognitionModel.builder(identifier).build()
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
