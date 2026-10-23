package com.gerko.fourenote.data.sync.webdav

import com.gerko.fourenote.data.sync.UploadContent
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.Base64

class WebDavProviderTest {
    private lateinit var server: HttpServer
    private lateinit var baseUrl: String
    private val files = LinkedHashMap<String, ByteArray>()
    private val etags = HashMap<String, String>()
    private var folderExists = false
    private var mkcolCount = 0
    private var nextEtag = 1

    private val user = "alice"
    private val password = "secret"

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange -> handle(exchange) }
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}/dav"
    }

    @After
    fun stop() {
        server.stop(0)
    }

    private fun provider(password: String = this.password) = WebDavProvider(baseUrl, user, password)

    @Test
    fun folderIsCreatedOnFirstUse() = runBlocking {
        provider().listFiles()
        assertEquals(1, mkcolCount)
        assertTrue(folderExists)
    }

    @Test
    fun uploadThenListShowsFileWithVersion() = runBlocking {
        val p = provider()
        val uploaded = p.upload("note.txt", "text/plain", UploadContent.of("hello".toByteArray()), null)
        assertEquals("note.txt", uploaded.name)
        assertTrue(uploaded.version.isNotEmpty())

        val listed = p.listFiles()
        assertEquals(1, listed.size)
        assertEquals("note.txt", listed[0].name)
        assertEquals(uploaded.version, listed[0].version)
    }

    @Test
    fun downloadReturnsSameBytes() = runBlocking {
        val p = provider()
        val bytes = "some content".toByteArray()
        p.upload("a.txt", "text/plain", UploadContent.of(bytes), null)
        assertArrayEquals(bytes, p.download("a.txt"))
    }

    @Test
    fun uploadingAgainChangesVersion() = runBlocking {
        val p = provider()
        val first = p.upload("a.txt", "text/plain", UploadContent.of("one".toByteArray()), null)
        val second = p.upload("a.txt", "text/plain", UploadContent.of("two".toByteArray()), "a.txt")
        assertNotEquals(first.version, second.version)
        assertEquals(1, p.listFiles().size)
    }

    @Test
    fun uploadWithDifferentRemoteIdRenames() = runBlocking {
        val p = provider()
        p.upload("old.txt", "text/plain", UploadContent.of("x".toByteArray()), null)
        p.upload("new.txt", "text/plain", UploadContent.of("x".toByteArray()), "old.txt")
        val names = p.listFiles().map { it.name }
        assertEquals(listOf("new.txt"), names)
    }

    @Test
    fun deleteWorksAndMissingIsFine() = runBlocking {
        val p = provider()
        p.upload("a.txt", "text/plain", UploadContent.of("x".toByteArray()), null)
        p.delete("a.txt")
        assertTrue(p.listFiles().isEmpty())
        p.delete("a.txt")
    }

    @Test
    fun namesWithSpacesAndTildeWork() = runBlocking {
        val p = provider()
        val name = "my note ~1.txt"
        val bytes = "data".toByteArray()
        p.upload(name, "text/plain", UploadContent.of(bytes), null)
        assertEquals(listOf(name), p.listFiles().map { it.name })
        assertArrayEquals(bytes, p.download(name))
    }

    @Test
    fun wrongPasswordMentions401() = runBlocking {
        try {
            provider(password = "wrong").listFiles()
            fail("expected an exception")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("401"))
        }
    }

    private fun handle(exchange: HttpExchange) {
        try {
            val expected = "Basic " + Base64.getEncoder().encodeToString("$user:$password".toByteArray())
            if (exchange.requestHeaders.getFirst("Authorization") != expected) {
                exchange.sendResponseHeaders(401, -1)
                return
            }
            val path = URLDecoder.decode(exchange.requestURI.path, "UTF-8")
            val method = exchange.requestMethod
            when (method) {
                "PROPFIND" -> propfind(exchange, path)
                "MKCOL" -> {
                    mkcolCount++
                    folderExists = true
                    exchange.sendResponseHeaders(201, -1)
                }
                "PUT" -> {
                    val body = exchange.requestBody.use { it.readBytes() }
                    files[path] = body
                    etags[path] = "\"${nextEtag++}\""
                    exchange.sendResponseHeaders(201, -1)
                }
                "GET" -> {
                    val body = files[path]
                    if (body == null) {
                        exchange.sendResponseHeaders(404, -1)
                    } else {
                        exchange.sendResponseHeaders(200, body.size.toLong())
                        exchange.responseBody.use { it.write(body) }
                    }
                }
                "DELETE" -> {
                    if (files.remove(path) != null) {
                        etags.remove(path)
                        exchange.sendResponseHeaders(204, -1)
                    } else {
                        exchange.sendResponseHeaders(404, -1)
                    }
                }
                else -> exchange.sendResponseHeaders(405, -1)
            }
        } finally {
            exchange.close()
        }
    }

    private fun propfind(exchange: HttpExchange, path: String) {
        val depth = exchange.requestHeaders.getFirst("Depth") ?: "0"
        val folderPath = "/dav/4ENote/"
        val isFolder = path == folderPath || path == folderPath.trimEnd('/')
        if (isFolder && !folderExists) {
            exchange.sendResponseHeaders(404, -1)
            return
        }
        if (!isFolder && !files.containsKey(path)) {
            exchange.sendResponseHeaders(404, -1)
            return
        }
        val entries = StringBuilder()
        if (isFolder) {
            entries.append(response(folderPath, null, true))
            if (depth == "1") {
                for ((filePath, _) in files) {
                    if (filePath.startsWith(folderPath)) entries.append(response(filePath, etags[filePath], false))
                }
            }
        } else {
            entries.append(response(path, etags[path], false))
        }
        val xml = """<?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:">$entries</d:multistatus>""".toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/xml; charset=utf-8")
        exchange.sendResponseHeaders(207, xml.size.toLong())
        exchange.responseBody.use { it.write(xml) }
    }

    private fun response(path: String, etag: String?, collection: Boolean): String {
        val type = if (collection) "<d:collection/>" else ""
        val etagXml = etag?.let { "<d:getetag>$it</d:getetag>" } ?: ""
        return """<d:response><d:href>$path</d:href><d:propstat><d:prop>
            <d:resourcetype>$type</d:resourcetype>$etagXml
            <d:getlastmodified>Mon, 01 Jan 2024 00:00:00 GMT</d:getlastmodified>
            </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"""
    }
}
