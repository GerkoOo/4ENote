package com.gerko.fourenote.data.sync.webdav

import com.gerko.fourenote.data.sync.CloudProvider
import com.gerko.fourenote.data.sync.RemoteFile
import com.gerko.fourenote.data.sync.UploadContent
import com.gerko.fourenote.data.sync.readLimited
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory

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
) : CloudProvider {
    private val folderUrl = baseUrl.trimEnd('/') + "/" + encode(folder) + "/"
    private var folderReady = false

    override suspend fun listFiles(): List<RemoteFile> = withContext(Dispatchers.IO) {
        ensureFolder()
        val response = request("PROPFIND", folderUrl, body = PROPS_BODY.toByteArray(), headers = mapOf("Depth" to "1"))
        val entries = parseMultistatus(response.body)
        entries.mapNotNull { entry ->
            // The folder itself and any sub-collection are not files.
            if (entry.isCollection || entry.href.trimEnd('/') == folderUrl.trimEnd('/')) return@mapNotNull null
            val name = nameOf(entry.href) ?: return@mapNotNull null
            RemoteFile(id = name, name = name, version = entry.version)
        }
    }

    override suspend fun download(remoteId: String): ByteArray = withContext(Dispatchers.IO) {
        ensureFolder()
        request("GET", folderUrl + encode(remoteId)).body
    }

    override suspend fun upload(name: String, mimeType: String, content: UploadContent, remoteId: String?): RemoteFile =
        withContext(Dispatchers.IO) {
            ensureFolder()
            val target = folderUrl + encode(name)
            val connection = open("PUT", target)
            try {
                connection.setRequestProperty("Content-Type", mimeType)
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(content.size)
                content.open().use { input -> connection.outputStream.use { input.copyTo(it, 64 * 1024) } }
                checkResponse(connection)
            } finally {
                connection.disconnect()
            }
            // A rename is a new file plus a delete of the old one.
            if (remoteId != null && remoteId != name) delete(remoteId)
            RemoteFile(id = name, name = name, version = versionOf(target))
        }

    override suspend fun delete(remoteId: String) = withContext(Dispatchers.IO) {
        ensureFolder()
        request("DELETE", folderUrl + encode(remoteId), allowMissing = true)
        Unit
    }

    /** Creates the app folder the first time something needs it. */
    private fun ensureFolder() {
        if (folderReady) return
        val exists = request("PROPFIND", folderUrl, body = PROPS_BODY.toByteArray(), headers = mapOf("Depth" to "0"), allowMissing = true)
        if (exists.code == 404) request("MKCOL", folderUrl)
        folderReady = true
    }

    /** The version of a single file: its ETag, or the last-modified time when the server has none. */
    private fun versionOf(url: String): String {
        val response = request("PROPFIND", url, body = PROPS_BODY.toByteArray(), headers = mapOf("Depth" to "0"))
        return parseMultistatus(response.body).firstOrNull()?.version ?: ""
    }

    private class Response(val code: Int, val body: ByteArray)

    private class Entry(val href: String, val version: String, val isCollection: Boolean)

    private fun request(
        method: String,
        url: String,
        body: ByteArray? = null,
        headers: Map<String, String> = emptyMap(),
        allowMissing: Boolean = false,
    ): Response {
        val connection = open(method, url)
        try {
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            if (body != null) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            if (code == 404 && allowMissing) return Response(code, ByteArray(0))
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readLimited() } ?: ByteArray(0)
            if (code !in 200..299) throw IllegalStateException("WebDAV $code: ${shortMessage(bytes)}")
            return Response(code, bytes)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(method: String, url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        setMethod(connection, method)
        val credentials = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
        connection.setRequestProperty("Authorization", "Basic $credentials")
        return connection
    }

    private fun checkResponse(connection: HttpURLConnection) {
        val code = connection.responseCode
        if (code in 200..299) return
        val bytes = connection.errorStream?.use { it.readLimited() } ?: ByteArray(0)
        throw IllegalStateException("WebDAV $code: ${shortMessage(bytes)}")
    }

    private fun parseMultistatus(body: ByteArray): List<Entry> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // The server is remote, so never let the parser fetch anything it names.
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val document = factory.newDocumentBuilder().parse(body.inputStream())
        val responses = document.getElementsByTagNameNS(DAV, "response")
        val entries = ArrayList<Entry>()
        for (i in 0 until responses.length) {
            val response = responses.item(i) as Element
            val href = childText(response, "href") ?: continue
            val prop = firstDescendant(response, "prop") ?: continue
            val etag = childText(prop, "getetag")?.trim()?.trim('"')
            val modified = childText(prop, "getlastmodified")
            val resourceType = firstDescendant(prop, "resourcetype")
            val isCollection = resourceType != null && firstDescendant(resourceType, "collection") != null
            entries += Entry(href, etag?.takeIf { it.isNotEmpty() } ?: modified.orEmpty(), isCollection)
        }
        return entries
    }

    private fun childText(parent: Element, localName: String): String? {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.localName == localName && node.namespaceURI == DAV) {
                return node.textContent
            }
        }
        return null
    }

    private fun firstDescendant(parent: Element, localName: String): Element? {
        val nodes = parent.getElementsByTagNameNS(DAV, localName)
        return if (nodes.length > 0) nodes.item(0) as Element else null
    }

    /** The URL-decoded last segment of an href, which is the file's name. */
    private fun nameOf(href: String): String? {
        val segment = href.trimEnd('/').substringAfterLast('/')
        if (segment.isEmpty()) return null
        return URLDecoder.decode(segment, "UTF-8")
    }

    private fun shortMessage(bytes: ByteArray) = String(bytes).trim().take(200).ifEmpty { "request failed" }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private companion object {
        const val DAV = "DAV:"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000
        const val PROPS_BODY =
            """<?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:"><d:prop>
            <d:getetag/><d:getlastmodified/><d:resourcetype/>
            </d:prop></d:propfind>"""

        /**
         * HttpURLConnection refuses PROPFIND and MKCOL through setRequestMethod, so the method
         * field is set directly; HTTPS connections keep the real connection in a delegate.
         */
        fun setMethod(connection: HttpURLConnection, method: String) {
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
}
