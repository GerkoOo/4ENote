package com.gerko.fourenote.platform

import com.gerko.fourenote.data.sync.UploadContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.buffer
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

actual fun platformHttp(): Http = JvmHttp

private object JvmHttp : Http {
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 60_000

    override suspend fun send(method: String, url: String, headers: Map<String, String>, body: UploadContent?, limit: Long): HttpResponse =
        withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                setMethod(connection, method)
                headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
                if (body != null) {
                    connection.doOutput = true
                    connection.setFixedLengthStreamingMode(body.size)
                    body.open().buffer().inputStream().use { input -> connection.outputStream.use { input.copyTo(it, 64 * 1024) } }
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                HttpResponse(code, stream?.use { it.readLimited(limit) } ?: ByteArray(0))
            } finally {
                connection.disconnect()
            }
        }

    private fun InputStream.readLimited(limit: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw IOException("File too large (over ${limit / (1024 * 1024)} MB)")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /**
     * HttpURLConnection refuses PROPFIND and MKCOL through setRequestMethod, so the method
     * field is set directly; HTTPS connections keep the real connection in a delegate.
     */
    private fun setMethod(connection: HttpURLConnection, method: String) {
        if (method in setOf("GET", "POST", "HEAD", "OPTIONS", "PUT", "DELETE", "TRACE")) {
            connection.requestMethod = method
            return
        }
        val field = HttpURLConnection::class.java.getDeclaredField("method")
        field.isAccessible = true
        field.set(connection, method)
        // The delegate field lives on the concrete HTTPS class (or a superclass), not on
        // HttpURLConnection itself, so walk the hierarchy to find it.
        var type: Class<*>? = connection.javaClass
        while (type != null) {
            val delegateField = runCatching { type.getDeclaredField("delegate") }.getOrNull()
            if (delegateField != null) {
                delegateField.isAccessible = true
                val delegate = delegateField.get(connection)
                if (delegate is HttpURLConnection) field.set(delegate, method)
                break
            }
            type = type.superclass
        }
    }
}
