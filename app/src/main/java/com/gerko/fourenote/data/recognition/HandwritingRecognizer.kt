package com.gerko.fourenote.data.recognition

import android.content.Context
import com.gerko.fourenote.data.ink.InkStroke
import org.json.JSONObject
import java.io.File

/**
 * Turns handwriting into text with Microsoft's open-source TrOCR model (small, trained on
 * handwritten English lines; MIT), run on the device (see [InkModel]). The model, about 67 MB,
 * downloads once; after that recognition works offline. It reads English.
 */
class HandwritingRecognizer(context: Context) {
    private val model = InkModel(
        dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "models/trocr-small-handwritten"),
        baseUrl = "https://huggingface.co/Xenova/trocr-small-handwritten/resolve/main",
        files = listOf(
            ModelFile(
                "encoder_model_quantized.onnx", 23_082_942,
                "2f29edbd925f8a49c9c7d1349895f960cf09d2efdc76fe23f957048d476e0d03", "onnx/encoder_model_quantized.onnx",
            ),
            ModelFile(
                "decoder_model_quantized.onnx", 40_159_904,
                "40166e4f975b3cecd24bb422db80eb06912777c8e8a56ab44f6bd771ec5df553", "onnx/decoder_model_quantized.onnx",
            ),
            ModelFile("tokenizer.json", 4_494_727, "68bcb5468c854362a615f3d2ff6a5e4091a85f4c8198993ed9a30afe0b143737"),
        ),
        encoderFile = "encoder_model_quantized.onnx",
        decoderFile = "decoder_model_quantized.onnx",
        startId = 2,
        endId = 2,
        maxTokens = 96,
    )
    private var vocabulary: List<String>? = null

    val downloadMegabytes get() = model.downloadMegabytes

    fun isDownloaded() = model.isDownloaded()

    suspend fun download(onProgress: (Float) -> Unit) = model.download(onProgress)

    /** Reads [strokes] line by line (see [HandwritingLines]) and returns the text, one line per line. */
    suspend fun recognize(strokes: List<InkStroke>): String {
        val vocab = vocabulary ?: readVocabulary().also { vocabulary = it }
        val lines = ArrayList<String>()
        for (line in HandwritingLines.split(strokes)) {
            val words = ArrayList<String>()
            for (piece in pieces(line)) {
                val ids = model.read(InkModel.render(piece, maxAspect = MAX_PIECE_ASPECT))
                words += ids.filter { it > LAST_SPECIAL_ID }
                    .joinToString("") { vocab.getOrElse(it.toInt()) { "" } }
                    .replace(WORD_START, ' ')
                    .trim()
            }
            lines += words.joinToString(" ")
        }
        return lines.joinToString("\n").trim()
    }

    fun close() = model.close()

    // The vocabulary's tokens in id order, from the "Unigram" model in tokenizer.json.
    private fun readVocabulary(): List<String> {
        val vocab = JSONObject(File(model.dir, "tokenizer.json").readText()).getJSONObject("model").getJSONArray("vocab")
        return List(vocab.length()) { vocab.getJSONArray(it).getString(0) }
    }

    private companion object {
        const val LAST_SPECIAL_ID = 3L
        const val WORD_START = '▁'

        /** The model reads lines squeezed into a square; past this width-to-height ratio a line is cut at word gaps. */
        const val MAX_PIECE_ASPECT = 9f

        /** Splits a long line into pieces at the gaps between words, each at most [MAX_PIECE_ASPECT] wide. */
        fun pieces(line: List<InkStroke>): List<List<InkStroke>> {
            val bounds = HandwritingLines.bounds(line)
            val height = bounds.height.coerceAtLeast(1f)
            if (bounds.width <= height * MAX_PIECE_ASPECT) return listOf(line)
            val byLeft = line.sortedBy { HandwritingLines.bounds(listOf(it)).left }
            val pieces = mutableListOf(mutableListOf<InkStroke>())
            var pieceLeft = Float.NaN
            var pieceRight = -Float.MAX_VALUE
            for (stroke in byLeft) {
                val b = HandwritingLines.bounds(listOf(stroke))
                val gap = b.left - pieceRight
                val wouldBe = maxOf(pieceRight, b.right) - (if (pieceLeft.isNaN()) b.left else pieceLeft)
                if (pieces.last().isNotEmpty() && wouldBe > height * (MAX_PIECE_ASPECT - 2) && gap > height * 0.15f) {
                    pieces += mutableListOf<InkStroke>()
                    pieceLeft = Float.NaN
                    pieceRight = -Float.MAX_VALUE
                }
                pieces.last() += stroke
                if (pieceLeft.isNaN()) pieceLeft = b.left
                pieceRight = maxOf(pieceRight, b.right)
            }
            return pieces
        }
    }
}
