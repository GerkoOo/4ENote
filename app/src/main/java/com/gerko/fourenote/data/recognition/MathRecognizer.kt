package com.gerko.fourenote.data.recognition

import android.content.Context
import com.gerko.fourenote.data.ink.InkStroke
import org.json.JSONObject
import java.io.File

/**
 * Turns handwritten maths into LaTeX with the open-source Pix2Text formula model
 * (breezedeus/pix2text-mfr, MIT), run on the device (see [InkModel]). The model, about
 * 118 MB, downloads once.
 */
class MathRecognizer(context: Context) {
    private val model = InkModel(
        dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "models/pix2text-mfr"),
        baseUrl = "https://huggingface.co/breezedeus/pix2text-mfr/resolve/main",
        files = listOf(
            ModelFile("encoder_model.onnx", 87_496_990, "bd8d5c322792e9ec45793af5569e9748f82a3d728a9e00213dbfc56c1486f37d"),
            ModelFile("decoder_model.onnx", 30_114_937, "fd0f92d7a012f3dae41e1ac79421aea0ea888b5a66cb3f9a004e424f82f3daed"),
            ModelFile("tokenizer.json", 39_161, "3e2ab757277d22639bec28c9d7972e352d3d1dba223051fa674002dc5ab64df3"),
        ),
        encoderFile = "encoder_model.onnx",
        decoderFile = "decoder_model.onnx",
        startId = 2,
        endId = 2,
        maxTokens = 256,
    )
    private var tokenizer: LatexTokenizer? = null

    val downloadMegabytes get() = model.downloadMegabytes

    fun isDownloaded() = model.isDownloaded()

    suspend fun download(onProgress: (Float) -> Unit) = model.download(onProgress)

    /** Reads [strokes] as one formula and returns its LaTeX. */
    suspend fun recognize(strokes: List<InkStroke>): String {
        val ids = model.read(InkModel.render(strokes, maxAspect = 8f))
        val tokens = tokenizer ?: LatexTokenizer(File(model.dir, "tokenizer.json").readText()).also { tokenizer = it }
        return tokens.decode(ids)
    }

    fun close() = model.close()
}

/** Turns the model's byte-level BPE token ids back into text (the decoding half of its tokenizer.json). */
class LatexTokenizer(tokenizerJson: String) {
    private val tokens: Map<Long, String> = JSONObject(tokenizerJson).getJSONObject("model").getJSONObject("vocab").let { vocab ->
        vocab.keys().asSequence().associate { vocab.getLong(it) to it }
    }

    fun decode(ids: List<Long>): String {
        val bytes = ids.filter { it > LAST_SPECIAL_ID }
            .flatMap { id -> tokens[id].orEmpty().map { BYTE_OF[it] ?: '?'.code.toByte() } }
        return String(bytes.toByteArray(), Charsets.UTF_8).trim()
    }

    private companion object {
        const val LAST_SPECIAL_ID = 4L

        /** GPT-2's byte-to-character table, reversed: printable bytes map to themselves, the rest above 255. */
        val BYTE_OF: Map<Char, Byte> = buildMap {
            val printable = (33..126) + (161..172) + (174..255)
            var extra = 0
            for (b in 0..255) {
                val char = if (b in printable) b else 256 + extra++
                put(char.toChar(), b.toByte())
            }
        }
    }
}
