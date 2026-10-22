package com.gerko.fourenote.data.recognition

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.gerko.fourenote.data.ink.InkStroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.security.MessageDigest

/** One file of a downloadable model, checked against its SHA-256 after downloading. */
data class ModelFile(val name: String, val size: Long, val sha256: String, val source: String = name)

/**
 * An open-source image-to-text model (a vision encoder and a text decoder, as in TrOCR) run on
 * the device with ONNX Runtime. Ink is drawn black on white into the 384 x 384 picture these
 * models read, the encoder reads it once and the decoder writes the text one token at a time.
 *
 * The files download once into [dir] (in the app's external files folder, where they can also
 * be copied by hand for a device without internet) and are checked against pinned hashes.
 */
class InkModel(
    val dir: File,
    private val baseUrl: String,
    private val files: List<ModelFile>,
    private val encoderFile: String,
    private val decoderFile: String,
    private val startId: Long,
    private val endId: Long,
    private val maxTokens: Int,
) {
    private var sessions: Pair<OrtSession, OrtSession>? = null

    val downloadMegabytes = files.sumOf { it.size } / 1_000_000

    fun isDownloaded(): Boolean = files.all { File(dir, it.name).isFile }

    /** Downloads the missing files; [onProgress] gets 0..1. */
    suspend fun download(onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val total = files.sumOf { it.size }
        var done = files.filter { File(dir, it.name).isFile }.sumOf { it.size }
        for (file in files) {
            val target = File(dir, file.name)
            if (target.isFile) continue
            val part = File(dir, file.name + ".part")
            val digest = MessageDigest.getInstance("SHA-256")
            val connection = URL("$baseUrl/${file.source}").openConnection() as HttpURLConnection
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
                error("The model download was damaged; please try again")
            }
            part.renameTo(target)
        }
    }

    /** The token ids the decoder writes for [picture] (one of [render]'s), without the start and end tokens. */
    suspend fun read(picture: Bitmap): List<Long> = withContext(Dispatchers.Default) {
        val env = OrtEnvironment.getEnvironment()
        val (encoder, decoder) = sessions ?: (
            env.createSession(File(dir, encoderFile).path, OrtSession.SessionOptions()) to
                env.createSession(File(dir, decoderFile).path, OrtSession.SessionOptions())
            ).also { sessions = it }
        val pixels = OnnxTensor.createTensor(env, FloatBuffer.wrap(pixelValues(picture)), longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong()))
        val hidden = pixels.use { encoder.run(mapOf("pixel_values" to it)).use { out -> copy(env, out[0] as OnnxTensor) } }
        val ids = mutableListOf(startId)
        hidden.use {
            while (ids.size <= maxTokens) {
                val input = OnnxTensor.createTensor(env, LongBuffer.wrap(ids.toLongArray()), longArrayOf(1, ids.size.toLong()))
                val next = input.use {
                    decoder.run(mapOf("input_ids" to input, "encoder_hidden_states" to hidden)).use { out ->
                        val logits = out[0] as OnnxTensor
                        val vocab = logits.info.shape.last().toInt()
                        val values = logits.floatBuffer
                        // Greedy: the most likely token after the last one.
                        val offset = (ids.size - 1) * vocab
                        (0 until vocab).maxBy { values.get(offset + it) }.toLong()
                    }
                }
                if (next == endId) break
                ids += next
            }
        }
        ids.drop(1)
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

    companion object {
        const val SIZE = 384
        private const val RENDER_HEIGHT = 160f

        /**
         * Draws [strokes] black on white, at their natural shape with a margin, then squeezes the
         * picture into the model's square, as its training images were. Very wide ink is drawn
         * smaller so it is at most [maxAspect] times wider than tall before squeezing.
         */
        fun render(strokes: List<InkStroke>, maxAspect: Float): Bitmap {
            var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (stroke in strokes) for (i in stroke.points.indices step 2) {
                minX = minOf(minX, stroke.points[i]); maxX = maxOf(maxX, stroke.points[i])
                minY = minOf(minY, stroke.points[i + 1]); maxY = maxOf(maxY, stroke.points[i + 1])
            }
            val inkWidth = (maxX - minX).coerceAtLeast(1f)
            val inkHeight = (maxY - minY).coerceAtLeast(1f)
            val scale = RENDER_HEIGHT / maxOf(inkHeight, inkWidth / maxAspect)
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

        /** RGB channels in planar order, scaled to -1..1 like the models' image processor. */
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
