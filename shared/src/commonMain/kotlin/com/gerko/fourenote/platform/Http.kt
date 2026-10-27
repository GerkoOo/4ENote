package com.gerko.fourenote.platform

import com.gerko.fourenote.data.sync.MAX_DOWNLOAD_BYTES
import com.gerko.fourenote.data.sync.UploadContent

class HttpResponse(val code: Int, val body: ByteArray)

/**
 * Plain HTTP for the cloud providers: HttpURLConnection on Android, NSURLSession on the iPad.
 * Any status comes back as a response; only a failed connection throws. Every method is allowed
 * (WebDAV needs PROPFIND and MKCOL). Bodies over [limit] bytes are refused.
 */
interface Http {
    suspend fun send(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: UploadContent? = null,
        limit: Long = MAX_DOWNLOAD_BYTES,
    ): HttpResponse
}

expect fun platformHttp(): Http
