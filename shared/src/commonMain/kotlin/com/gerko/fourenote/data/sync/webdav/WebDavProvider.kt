package com.gerko.fourenote.data.sync.webdav

import com.gerko.fourenote.data.sync.CloudProvider
import com.gerko.fourenote.data.sync.RemoteFile
import com.gerko.fourenote.data.sync.UploadContent
import com.gerko.fourenote.platform.Http
import com.gerko.fourenote.platform.HttpResponse
import com.gerko.fourenote.platform.platformHttp
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * WebDAV through plain HTTP (no libraries): everything lives in a "4ENote" folder on the server.
 * Works with Nextcloud, ownCloud, Koofr and any other WebDAV server. Files are addressed by name,
 * so a rename is an upload under the new name followed by a delete of the old one.
 */
class WebDavProvider(
    baseUrl: String,
    private val username: String,
    private val password: String,
    folder: String = "4ENote",
    private val http: Http = platformHttp(),
) : CloudProvider {
    private val folderUrl = baseUrl.trimEnd('/') + "/" + encode(folder) + "/"
    private var folderReady = false

    override suspend fun listFiles(): List<RemoteFile> {
        ensureFolder()
        val response = request("PROPFIND", folderUrl, body = PROPS_BODY, headers = mapOf("Depth" to "1"))
        val entries = parseMultistatus(response.body)
        return entries.mapNotNull { entry ->
            // The folder itself and any sub-collection are not files.
            if (entry.isCollection || entry.href.trimEnd('/') == folderUrl.trimEnd('/')) return@mapNotNull null
            val name = nameOf(entry.href) ?: return@mapNotNull null
            RemoteFile(id = name, name = name, version = entry.version)
        }
    }

    override suspend fun download(remoteId: String): ByteArray {
        ensureFolder()
        return request("GET", folderUrl + encode(remoteId)).body
    }

    override suspend fun upload(name: String, mimeType: String, content: UploadContent, remoteId: String?): RemoteFile {
        ensureFolder()
        val target = folderUrl + encode(name)
        check(http.send("PUT", target, auth() + ("Content-Type" to mimeType), content))
        // A rename is a new file plus a delete of the old one.
        if (remoteId != null && remoteId != name) delete(remoteId)
        return RemoteFile(id = name, name = name, version = versionOf(target))
    }

    override suspend fun delete(remoteId: String) {
        ensureFolder()
        request("DELETE", folderUrl + encode(remoteId), allowMissing = true)
    }

    /** Creates the app folder the first time something needs it. */
    private suspend fun ensureFolder() {
        if (folderReady) return
        val exists = request("PROPFIND", folderUrl, body = PROPS_BODY, headers = mapOf("Depth" to "0"), allowMissing = true)
        if (exists.code == 404) request("MKCOL", folderUrl)
        folderReady = true
    }

    /** The version of a single file: its ETag, or the last-modified time when the server has none. */
    private suspend fun versionOf(url: String): String {
        val response = request("PROPFIND", url, body = PROPS_BODY, headers = mapOf("Depth" to "0"))
        return parseMultistatus(response.body).firstOrNull()?.version ?: ""
    }

    private class Entry(val href: String, val version: String, val isCollection: Boolean)

    private suspend fun request(
        method: String,
        url: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
        allowMissing: Boolean = false,
    ): HttpResponse {
        val response = http.send(method, url, auth() + headers, body?.let { UploadContent.of(it.encodeToByteArray()) })
        if (response.code == 404 && allowMissing) return HttpResponse(404, ByteArray(0))
        return check(response)
    }

    private fun check(response: HttpResponse): HttpResponse {
        if (response.code !in 200..299) throw IllegalStateException("WebDAV ${response.code}: ${shortMessage(response.body)}")
        return response
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun auth() = mapOf("Authorization" to "Basic " + Base64.encode("$username:$password".encodeToByteArray()))

    private fun parseMultistatus(body: ByteArray): List<Entry> {
        val document = Xml.parse(body.decodeToString())
        val responses = (listOf(document).filter { it.namespace == DAV && it.localName == "response" }) +
            document.descendants(DAV, "response")
        return responses.mapNotNull { response ->
            val href = response.child(DAV, "href")?.text ?: return@mapNotNull null
            val prop = response.firstDescendant(DAV, "prop") ?: return@mapNotNull null
            val etag = prop.child(DAV, "getetag")?.text?.trim()?.trim('"')
            val modified = prop.child(DAV, "getlastmodified")?.text
            val resourceType = prop.firstDescendant(DAV, "resourcetype")
            val isCollection = resourceType != null && resourceType.firstDescendant(DAV, "collection") != null
            Entry(href, etag?.takeIf { it.isNotEmpty() } ?: modified.orEmpty(), isCollection)
        }
    }

    /** The URL-decoded last segment of an href, which is the file's name. */
    private fun nameOf(href: String): String? {
        val segment = href.trimEnd('/').substringAfterLast('/')
        if (segment.isEmpty()) return null
        return decode(segment)
    }

    private fun shortMessage(bytes: ByteArray) = bytes.decodeToString().trim().take(200).ifEmpty { "request failed" }

    internal companion object {
        const val DAV = "DAV:"
        const val PROPS_BODY =
            """<?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:"><d:prop>
            <d:getetag/><d:getlastmodified/><d:resourcetype/>
            </d:prop></d:propfind>"""

        private const val HEX = "0123456789ABCDEF"

        /** Like Java's URLEncoder (UTF-8), but a space is %20 instead of "+". */
        fun encode(value: String): String {
            val out = StringBuilder()
            for (b in value.encodeToByteArray()) {
                val c = b.toInt() and 0xff
                val ch = c.toChar()
                if (c < 0x80 && (ch.isLetterOrDigit() || ch in ".-*_")) out.append(ch)
                else out.append('%').append(HEX[c shr 4]).append(HEX[c and 0xf])
            }
            return out.toString()
        }

        /** Like Java's URLDecoder (UTF-8): %XX bytes, and "+" is a space. */
        fun decode(value: String): String {
            val bytes = ArrayList<Byte>(value.length)
            var i = 0
            while (i < value.length) {
                val c = value[i]
                when {
                    c == '+' -> { bytes += ' '.code.toByte(); i++ }
                    c == '%' && i + 2 < value.length -> {
                        bytes += value.substring(i + 1, i + 3).toInt(16).toByte(); i += 3
                    }
                    c == '%' -> throw IllegalArgumentException("Broken escape in $value")
                    else -> { bytes += c.toString().encodeToByteArray().toList(); i++ }
                }
            }
            return bytes.toByteArray().decodeToString()
        }
    }
}
