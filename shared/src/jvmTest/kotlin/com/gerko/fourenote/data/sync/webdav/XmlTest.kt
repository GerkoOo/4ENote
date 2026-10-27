package com.gerko.fourenote.data.sync.webdav

import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class XmlTest {
    @Test
    fun nextcloudStyleAnswer() {
        val root = Xml.parse(
            """<?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:" xmlns:s="http://sabredav.org/ns" xmlns:oc="http://owncloud.org/ns">
             <d:response><d:href>/remote.php/dav/files/a/4ENote/</d:href>
              <d:propstat><d:prop><d:getetag>&quot;65f&quot;</d:getetag><d:resourcetype><d:collection/></d:resourcetype></d:prop>
              <d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
             <d:response><d:href>/remote.php/dav/files/a/4ENote/n%20A&amp;B.4enote</d:href>
              <d:propstat><d:prop><d:getetag>"abc"</d:getetag><d:resourcetype/><oc:size>3</oc:size></d:prop></d:propstat></d:response>
            </d:multistatus>""",
        )
        val responses = root.descendants("DAV:", "response")
        assertEquals(2, responses.size)
        assertEquals("/remote.php/dav/files/a/4ENote/n%20A&B.4enote", responses[1].child("DAV:", "href")!!.text)
        val prop = responses[0].firstDescendant("DAV:", "prop")!!
        assertEquals("\"65f\"", prop.child("DAV:", "getetag")!!.text)
        assertTrue(prop.firstDescendant("DAV:", "collection") != null)
        assertNull(responses[1].firstDescendant("DAV:", "collection"))
    }

    @Test
    fun defaultNamespaceCdataAndCharacterReferences() {
        val root = Xml.parse("<multistatus xmlns='DAV:'><response><href><![CDATA[a<b]]>&#x263A;&#128512;</href></response></multistatus>")
        assertEquals("a<b\u263A\uD83D\uDE00", root.firstDescendant("DAV:", "href")!!.text)
    }

    @Test
    fun doctypeAndBrokenXmlAreRefused() {
        assertFailsWith<IllegalArgumentException> { Xml.parse("<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><x>&e;</x>") }
        assertFailsWith<IllegalArgumentException> { Xml.parse("<a><b></a>") }
        assertFailsWith<IllegalArgumentException> { Xml.parse("<a>&unknown;</a>") }
    }

    @Test
    fun urlEncodingMatchesJava() {
        for (s in listOf("4ENote", "n A&B+c.4enote", "Grüße/ä?#%", "😀 x*y_z-1.pdf")) {
            assertEquals(URLEncoder.encode(s, "UTF-8").replace("+", "%20"), WebDavProvider.encode(s))
            assertEquals(s, WebDavProvider.decode(WebDavProvider.encode(s)))
            assertEquals(URLDecoder.decode(URLEncoder.encode(s, "UTF-8"), "UTF-8"), WebDavProvider.decode(URLEncoder.encode(s, "UTF-8")))
        }
    }
}
