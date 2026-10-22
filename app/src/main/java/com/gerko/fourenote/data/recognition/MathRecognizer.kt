package com.gerko.fourenote.data.recognition

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.gerko.fourenote.data.ink.InkStroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.security.MessageDigest

/**
 * Turns handwritten maths into LaTeX with the open-source Pix2Text formula model
 * (breezedeus/pix2text-mfr, MIT), run on the device with ONNX Runtime. The ink is drawn
 * black on white, the encoder reads the picture and the decoder writes LaTeX token by token.
 *
 * The model (about 118 MB) downloads once into the app's external files folder, where it can
 * also be copied by hand (adb push) for a device without internet.
 */
class MathRecognizer(context: Context) {
    val modelDir: File = File(context.getExternalFilesDir(null) ?: context.filesDir, "models/pix2text-mfr")

    private var sessions: Pair<OrtSession, OrtSession>? = null
    private var tokenizer: LatexTokenizer? = null

    fun isDownloaded(): Boolean = MODEL_FILES.all { File(modelDir, it.name).isFile }

    /** Downloads the missing model files; [onProgress] gets 0..1. */
    suspend fun download(onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        modelDir.mkdirs()
        val total = MODEL_FILES.sumOf { it.size }
        var done = MODEL_FILES.filter { File(modelDir, it.name).isFile }.sumOf { it.size }
        for (file in MODEL_FILES) {
            val target = File(modelDir, file.name)
            if (target.isFile) continue
            val part = File(modelDir, file.name + ".part")
            val digest = MessageDigest.getInstance("SHA-256")
            val connection = URL("$BASE_URL/${file.name}").openConnection() as HttpURLConnection
            try {
                connection.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            done += n
                            onProgress(done.toFloat() / total)
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (hash != file.sha256) {
                part.delete()
                error("The maths model download was damaged; please try again")
            }
            part.renameTo(target)
        }
    }

    /** Reads [strokes] as one formula and returns its LaTeX. */
    suspend fun recognize(strokes: List<InkStroke>): String = withContext(Dispatchers.Default) {
        val env = OrtEnvironment.getEnvironment()
        val (encoder, decoder) = sessions ?: (
            env.createSession(File(modelDir, "encoder_model.onnx").path, OrtSession.SessionOptions()) to
                env.createSession(File(modelDir, "decoder_model.onnx").path, OrtSession.SessionOptions())
            ).also { sessions = it }
        val tokens = tokenizer ?: LatexTokenizer(File(modelDir, "tokenizer.json").readText()).also { tokenizer = it }

        val pixels = OnnxTensor.createTensor(env, FloatBuffer.wrap(pixelValues(render(strokes))), longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong()))
        val hidden = pixels.use { encoder.run(mapOf("pixel_values" to it)).use { out -> copy(env, out[0] as OnnxTensor) } }
        val ids = mutableListOf(START_ID)
        hidden.use {
            while (ids.size <= MAX_TOKENS) {
                val input = OnnxTensor.createTensor(env, LongBuffer.wrap(ids.toLongArray()), longArrayOf(1, ids.size.toLong()))
                val next = input.use {
                    decoder.run(mapOf("input_ids" to input, "encoder_hidden_states" to hidden)).use { out ->
                        val logits = (out[0] as OnnxTensor).floatBuffer
                        // Greedy: the most likely token after the last one.
                        val offset = (ids.size - 1) * VOCAB
                        (0 until VOCAB).maxBy { logits.get(offset + it) }.toLong()
                    }
                }
                if (next == END_ID) break
                ids += next
            }
        }
        tokens.decode(ids.drop(1))
    }

    fun close() {
        sessions?.let { (encoder, decoder) ->
            encoder.close()
            decoder.close()
        }
        sessions = null
    }

    // A session's outputs are freed with its Result; keep a copy of the encoder output for the decoder loop.
    private fun copy(env: OrtEnvironment, tensor: OnnxTensor): OnnxTensor {
        val shape = tensor.info.shape
        val data = FloatArray(tensor.floatBuffer.remaining()).also { tensor.floatBuffer.get(it) }
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape)
    }

    private data class ModelFile(val name: String, val size: Long, val sha256: String)

    companion object {
        private const val BASE_URL = "https://huggingface.co/breezedeus/pix2text-mfr/resolve/main"
        private val MODEL_FILES = listOf(
            ModelFile("encoder_model.onnx", 87_496_990, "bd8d5c322792e9ec45793af5569e9748f82a3d728a9e00213dbfc56c1486f37d"),
            ModelFile("decoder_model.onnx", 30_114_937, "fd0f92d7a012f3dae41e1ac79421aea0ea888b5a66cb3f9a004e424f82f3daed"),
            ModelFile("tokenizer.json", 39_161, "3e2ab757277d22639bec28c9d7972e352d3d1dba223051fa674002dc5ab64df3"),
        )
        val DOWNLOAD_MB = MODEL_FILES.sumOf { it.size } / 1_000_000

        private const val SIZE = 384
        private const val VOCAB = 1200
        private const val START_ID = 2L
        private const val END_ID = 2L
        private const val MAX_TOKENS = 256

        /** Draws the strokes black on white, centred with a margin, in a square the model's size. */
        fun render(strokes: List<InkStroke>): Bitmap {
            var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (stroke in strokes) for (i in stroke.points.indices step 2) {
                minX = minOf(minX, stroke.points[i]); maxX = maxOf(maxX, stroke.points[i])
                minY = minOf(minY, stroke.points[i + 1]); maxY = maxOf(maxY, stroke.points[i + 1])
            }
            // The model stretches every picture to a square, as in its training; draw the
            // formula at its natural shape on a wide canvas first, then let that happen.
            val inkWidth = (maxX - minX).coerceAtLeast(1f)
            val inkHeight = (maxY - minY).coerceAtLeast(1f)
            val scale = RENDER_HEIGHT / maxOf(inkHeight, inkWidth / MAX_ASPECT)
            val margin = RENDER_HEIGHT * 0.15f
            val width = (inkWidth * scale + 2 * margin).toInt().coerceAtLeast(1)
            val height = (inkHeight * scale + 2 * margin).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = RENDER_HEIGHT * 0.035f
            }
            for (stroke in strokes) {
                val path = Path()
                for (i in stroke.points.indices step 2) {
                    val x = margin + (stroke.points[i] - minX) * scale
                    val y = margin + (stroke.points[i + 1] - minY) * scale
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                if (stroke.points.size == 2) path.lineTo(margin + (stroke.points[0] - minX) * scale + 0.1f, margin + (stroke.points[1] - minY) * scale)
                canvas.drawPath(path, paint)
            }
            return Bitmap.createScaledBitmap(bitmap, SIZE, SIZE, true).also { if (it !== bitmap) bitmap.recycle() }
        }

        private const val RENDER_HEIGHT = 160f
        private const val MAX_ASPECT = 8f

        /** RGB channels in planar order, scaled to -1..1 like the model's image processor. */
        private fun pixelValues(bitmap: Bitmap): FloatArray {
            val argb = IntArray(SIZE * SIZE)
            bitmap.getPixels(argb, 0, SIZE, 0, 0, SIZE, SIZE)
            val plane = SIZE * SIZE
            val values = FloatArray(3 * plane)
            for (i in argb.indices) {
                val c = argb[i]
                values[i] = ((c shr 16 and 0xFF) / 127.5f) - 1f
                values[plane + i] = ((c shr 8 and 0xFF) / 127.5f) - 1f
                values[2 * plane + i] = ((c and 0xFF) / 127.5f) - 1f
            }
            return values
        }
    }
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
