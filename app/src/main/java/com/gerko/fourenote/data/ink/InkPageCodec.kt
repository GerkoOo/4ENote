package com.gerko.fourenote.data.ink

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import kotlin.math.roundToInt

/**
 * The compact binary form of one page's strokes, as stored on disk. Points keep a tenth of a
 * page unit (like the JSON format) and are written as the step from the previous point in
 * variable-length integers, then deflated: dense handwriting comes out several times smaller
 * than JSON and is much faster to read.
 */
object InkPageCodec {
    private const val MAGIC = 0x34455031 // "4EP1"
    private const val VERSION = 1

    private const val HIGHLIGHTER = 1
    private const val HAS_WIDTHS = 2
    private const val HAS_NIB = 4

    fun encode(strokes: List<InkStroke>): ByteArray {
        val bytes = ByteArrayOutputStream()
        write(strokes, bytes)
        return bytes.toByteArray()
    }

    fun write(strokes: List<InkStroke>, target: OutputStream) {
        val deflater = Deflater(Deflater.BEST_SPEED)
        try {
            val deflating = DeflaterOutputStream(target, deflater, 64 * 1024)
            DataOutputStream(deflating).run {
                writeInt(MAGIC)
                writeVarInt(VERSION)
                writeVarInt(strokes.size)
                for (stroke in strokes) {
                    writeUTF(stroke.id)
                    writeUTF(stroke.layerId)
                    writeInt(stroke.color)
                    writeFloat(stroke.width)
                    val widths = stroke.widths
                    val nib = stroke.nibAngle
                    writeByte(
                        (if (stroke.highlighter) HIGHLIGHTER else 0) or
                            (if (widths != null) HAS_WIDTHS else 0) or
                            (if (nib != null) HAS_NIB else 0),
                    )
                    if (nib != null) writeFloat(nib)
                    val n = stroke.points.size / 2
                    writeVarInt(n)
                    var lastX = 0
                    var lastY = 0
                    for (i in 0 until n) {
                        val x = (stroke.points[2 * i] * 10).roundToInt()
                        val y = (stroke.points[2 * i + 1] * 10).roundToInt()
                        writeVarInt(zigzag(x - lastX))
                        writeVarInt(zigzag(y - lastY))
                        lastX = x
                        lastY = y
                    }
                    if (widths != null) {
                        writeVarInt(widths.size)
                        for (w in widths) writeVarInt((w * 100).roundToInt().coerceAtLeast(0))
                    }
                }
                flush()
                deflating.finish()
            }
        } finally {
            deflater.end()
        }
    }

    fun decode(data: ByteArray): List<InkStroke> = read(data.inputStream())

    fun read(source: InputStream): List<InkStroke> =
        DataInputStream(InflaterInputStream(source, java.util.zip.Inflater(), 64 * 1024)).run {
            require(readInt() == MAGIC) { "Not a 4ENote page file" }
            val version = readVarInt()
            require(version <= VERSION) { "Page file from a newer version of 4ENote" }
            val count = readVarInt()
            List(count) {
                val id = readUTF()
                val layerId = readUTF()
                val color = readInt()
                val width = readFloat()
                val flags = readByte().toInt()
                val nib = if (flags and HAS_NIB != 0) readFloat() else null
                val n = readVarInt()
                val points = FloatArray(2 * n)
                var x = 0
                var y = 0
                for (i in 0 until n) {
                    x += unzigzag(readVarInt())
                    y += unzigzag(readVarInt())
                    points[2 * i] = x / 10f
                    points[2 * i + 1] = y / 10f
                }
                val widths = if (flags and HAS_WIDTHS != 0) FloatArray(readVarInt()) { readVarInt() / 100f } else null
                InkStroke(color, width, flags and HIGHLIGHTER != 0, points, widths, layerId, id, nib)
            }
        }

    private fun zigzag(v: Int) = (v shl 1) xor (v shr 31)
    private fun unzigzag(v: Int) = (v ushr 1) xor -(v and 1)

    private fun DataOutputStream.writeVarInt(value: Int) {
        var v = value
        while (v and 0x7F.inv() != 0) {
            writeByte((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        writeByte(v)
    }

    private fun DataInputStream.readVarInt(): Int {
        var result = 0
        var shift = 0
        while (shift < 35) {
            val b = read()
            if (b < 0) throw EOFException()
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
        error("Malformed number in page file")
    }
}
