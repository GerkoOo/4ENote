package com.gerko.fourenote.data.sync.webdav

import com.gerko.fourenote.data.sync.webDavErrorMessage
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.Base64

/**
 * The error messages [com.gerko.fourenote.data.sync.SyncManager.connectWebDav] shows, checked
 * against a real HTTP server so the mapping sees the same exceptions the app would.
 */
class WebDavConnectTest {
    private lateinit var server: HttpServer
    private lateinit var baseUrl: String

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

    @Test
    fun wrongPasswordMapsTo401Message() = runBlocking {
        val error = connect(password = "wrong")
        assertEquals("Wrong user name or password", error)
    }

    @Test
    fun goodCredentialsConnect() = runBlocking {
        assertNull(connect())
    }

    @Test
    fun unknownHostMapsToServerNotFound() = runBlocking {
        val error = connect(url = "http://no-such-host.invalid/dav")
        assertEquals("Server not found", error)
    }

    /** Same connection test and error mapping as SyncManager.connectWebDav. */
    private suspend fun connect(url: String = baseUrl, password: String = this.password): String? = try {
        WebDavProvider(url, user, password).listFiles()
        null
    } catch (e: Exception) {
        webDavErrorMessage(e)
    }

    private fun handle(exchange: HttpExchange) {
        try {
            val expected = "Basic " + Base64.getEncoder().encodeToString("$user:$password".toByteArray())
            if (exchange.requestHeaders.getFirst("Authorization") != expected) {
                exchange.sendResponseHeaders(401, -1)
                return
            }
            when (exchange.requestMethod) {
                "PROPFIND" -> {
                    val xml = """<?xml version="1.0" encoding="utf-8"?>
                        <d:multistatus xmlns:d="DAV:"><d:response><d:href>/dav/4ENote/</d:href>
                        <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop>
                        <d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>"""
                        .toByteArray()
                    exchange.responseHeaders.add("Content-Type", "application/xml; charset=utf-8")
                    exchange.sendResponseHeaders(207, xml.size.toLong())
                    exchange.responseBody.use { it.write(xml) }
                }
                "MKCOL" -> exchange.sendResponseHeaders(201, -1)
                else -> exchange.sendResponseHeaders(405, -1)
            }
        } finally {
            exchange.close()
        }
    }
}
