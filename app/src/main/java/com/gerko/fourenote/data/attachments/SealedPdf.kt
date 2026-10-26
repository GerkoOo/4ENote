package com.gerko.fourenote.data.attachments

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.system.ErrnoException
import android.system.OsConstants
import com.gerko.fourenote.data.ink.FileSealing
import java.io.Closeable
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile

/**
 * A PDF kept encrypted on the device in pieces, so Android's PDF renderer can read it with no
 * plain copy anywhere: every [CHUNK] bytes are sealed on their own, bound to the file's name, their
 * place and the number of pieces (so none can be swapped, dropped or put in another order), and
 * [open] hands the renderer a file descriptor that decrypts just the pieces it reads.
 *
 * Layout: "4EP1", the piece size (int), the plain length (long), a sealed piece's size (int),
 * then the sealed pieces one after another (the last one may be shorter).
 */
internal object SealedPdf {
    private val MAGIC = "4EP1".encodeToByteArray()
    private const val HEADER = 4 + 4 + 8 + 4
    const val CHUNK = 64 * 1024

    fun isSealed(head: ByteArray) = head.size >= MAGIC.size && MAGIC.indices.all { head[it] == MAGIC[it] }

    private fun pieceName(name: String, index: Long, count: Long) = "$name#$index/$count"

    private fun pieces(length: Long, chunk: Int) = (length + chunk - 1) / chunk

    /** Seals [plainLength] bytes read from [input] into [out]. */
    fun seal(input: InputStream, plainLength: Long, out: OutputStream, sealing: FileSealing, name: String) {
        val count = pieces(plainLength, CHUNK)
        // What sealing adds to each piece (nonce, tag): the same for every piece.
        val overhead = sealing.seal(ByteArray(0), "$name#size").size
        DataOutputStream(out).apply {
            write(MAGIC)
            writeInt(CHUNK)
            writeLong(plainLength)
            writeInt(CHUNK + overhead)
            flush()
        }
        val buffer = ByteArray(CHUNK)
        for (index in 0 until count) {
            val size = minOf(CHUNK.toLong(), plainLength - index * CHUNK).toInt()
            var read = 0
            while (read < size) {
                val n = input.read(buffer, read, size - read)
                if (n < 0) throw EOFException("The PDF ended early")
                read += n
            }
            out.write(sealing.seal(buffer.copyOf(size), pieceName(name, index, count)))
        }
    }

    /** Reads a sealed PDF's plain bytes anywhere in it, a piece at a time. */
    class Reader(file: File, private val sealing: FileSealing, private val name: String) : Closeable {
        private val raf = RandomAccessFile(file, "r")
        private val chunk: Int
        private val sealedChunk: Int
        val plainLength: Long
        private val count: Long
        private var cachedIndex = -1L
        private var cached = ByteArray(0)

        init {
            try {
                val magic = ByteArray(MAGIC.size).also { raf.readFully(it) }
                require(isSealed(magic)) { "Not a sealed PDF" }
                chunk = raf.readInt()
                plainLength = raf.readLong()
                sealedChunk = raf.readInt()
                require(chunk > 0 && sealedChunk > chunk && plainLength >= 0) { "Damaged sealed PDF" }
                count = pieces(plainLength, chunk)
            } catch (e: Exception) {
                raf.close()
                throw e
            }
        }

        private fun piece(index: Long): ByteArray {
            if (index == cachedIndex) return cached
            val plainSize = minOf(chunk.toLong(), plainLength - index * chunk).toInt()
            val data = ByteArray(plainSize + (sealedChunk - chunk))
            raf.seek(HEADER + index * sealedChunk)
            raf.readFully(data)
            val plain = sealing.open(data, pieceName(name, index, count))
            check(plain.size == plainSize) { "Damaged sealed PDF" }
            cached = plain
            cachedIndex = index
            return plain
        }

        /** Copies up to [size] plain bytes from [offset] into [into] at [at]; how many (0 at the end). */
        fun read(offset: Long, size: Int, into: ByteArray, at: Int = 0): Int {
            var done = 0
            while (done < size && offset + done < plainLength) {
                val position = offset + done
                val index = position / chunk
                val piece = piece(index)
                val within = (position - index * chunk).toInt()
                val n = minOf(size - done, piece.size - within)
                System.arraycopy(piece, within, into, at + done, n)
                done += n
            }
            return done
        }

        override fun close() = raf.close()
    }

    /** The plain PDF as a stream, decrypted as it is read (for backups and uploads). */
    fun inputStream(file: File, sealing: FileSealing, name: String): InputStream = object : InputStream() {
        private val reader = Reader(file, sealing, name)
        private var position = 0L

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            val n = reader.read(position, len, b, off)
            if (n == 0) return -1
            position += n
            return n
        }

        override fun close() = reader.close()
    }

    /** The plain length of a sealed PDF, from its header. */
    fun plainLength(file: File, sealing: FileSealing, name: String): Long = Reader(file, sealing, name).use { it.plainLength }

    // Android calls the file descriptor's reads back on this thread.
    private val callbacks by lazy { Handler(HandlerThread("sealed-pdf").apply { start() }.looper) }

    /** A read-only file descriptor showing the plain PDF, for Android's PDF renderer. */
    fun open(context: Context, file: File, sealing: FileSealing, name: String): ParcelFileDescriptor {
        val reader = Reader(file, sealing, name)
        val storage = context.getSystemService(StorageManager::class.java)
        return storage.openProxyFileDescriptor(
            ParcelFileDescriptor.MODE_READ_ONLY,
            object : ProxyFileDescriptorCallback() {
                override fun onGetSize() = reader.plainLength

                override fun onRead(offset: Long, size: Int, data: ByteArray): Int = try {
                    reader.read(offset, size, data)
                } catch (e: Exception) {
                    // A damaged or tampered piece reads as an I/O error, never as wrong bytes.
                    throw ErrnoException("read", OsConstants.EIO)
                }

                override fun onRelease() = reader.close()
            },
            callbacks,
        )
    }
}
