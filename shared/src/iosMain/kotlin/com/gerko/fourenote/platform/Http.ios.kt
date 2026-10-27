package com.gerko.fourenote.platform

import com.gerko.fourenote.data.sync.UploadContent
import kotlinx.coroutines.suspendCancellableCoroutine
import okio.IOException
import okio.buffer
import okio.use
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual fun platformHttp(): Http = IosHttp

private object IosHttp : Http {
    // No cache and no cookies: a sync must always see the server's current files.
    private val session = NSURLSession.sessionWithConfiguration(
        NSURLSessionConfiguration.ephemeralSessionConfiguration.apply {
            requestCachePolicy = NSURLRequestReloadIgnoringLocalCacheData
            URLCache = null
            timeoutIntervalForRequest = 60.0
        },
    )

    override suspend fun send(method: String, url: String, headers: Map<String, String>, body: UploadContent?, limit: Long): HttpResponse {
        val request = NSMutableURLRequest(uRL = NSURL(string = url))
        request.setHTTPMethod(method)
        headers.forEach { (k, v) -> request.setValue(v, forHTTPHeaderField = k) }
        // Read whole: NSURLSession streams only from files; uploads are notes and pictures.
        if (body != null) request.setHTTPBody(body.open().buffer().use { it.readByteArray() }.toNSData())
        return suspendCancellableCoroutine { continuation ->
            val task = session.dataTaskWithRequest(request) { data, response, error ->
                when {
                    error != null -> continuation.resumeWithException(IOException(error.localizedDescription))
                    data != null && data.length.toLong() > limit ->
                        continuation.resumeWithException(IOException("File too large (over ${limit / (1024 * 1024)} MB)"))
                    else -> continuation.resume(
                        HttpResponse((response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0, data?.toByteArray() ?: ByteArray(0)),
                    )
                }
            }
            continuation.invokeOnCancellation { task.cancel() }
            task.resume()
        }
    }
}
