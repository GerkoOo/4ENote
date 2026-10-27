package com.gerko.fourenote.data.sync.webdav

/**
 * Just enough XML for WebDAV answers: elements with their namespaces, text, CDATA and the
 * standard entities. A DOCTYPE is refused, so a server can't make the parser fetch or expand
 * anything.
 */
internal class XmlElement(val namespace: String?, val localName: String) {
    val children = ArrayList<Any>() // XmlElement or String

    /** All text inside, like DOM's textContent. */
    val text: String
        get() = buildString { appendText(this) }

    private fun appendText(out: StringBuilder) {
        for (c in children) if (c is String) out.append(c) else (c as XmlElement).appendText(out)
    }

    fun child(namespace: String, localName: String): XmlElement? =
        children.firstOrNull { it is XmlElement && it.namespace == namespace && it.localName == localName } as XmlElement?

    /** Elements below this one (not itself) with that name, in document order. */
    fun descendants(namespace: String, localName: String): List<XmlElement> {
        val found = ArrayList<XmlElement>()
        fun walk(e: XmlElement) {
            for (c in e.children) if (c is XmlElement) {
                if (c.namespace == namespace && c.localName == localName) found += c
                walk(c)
            }
        }
        walk(this)
        return found
    }

    fun firstDescendant(namespace: String, localName: String): XmlElement? = descendants(namespace, localName).firstOrNull()
}

internal object Xml {
    /** The root element; throws on broken XML. */
    fun parse(text: String): XmlElement {
        var i = if (text.startsWith('﻿')) 1 else 0
        val stack = ArrayList<XmlElement>()
        val scopes = ArrayList<Map<String, String>>()
        var root: XmlElement? = null

        fun fail(message: String): Nothing = throw IllegalArgumentException("Broken XML: $message")

        while (i < text.length) {
            if (text[i] != '<') {
                val end = text.indexOf('<', i).let { if (it < 0) text.length else it }
                val chunk = text.substring(i, end)
                if (stack.isNotEmpty()) stack.last().children += decode(chunk)
                else if (chunk.isNotBlank()) fail("text outside the root")
                i = end
                continue
            }
            when {
                text.startsWith("<?", i) -> i = text.indexOf("?>", i).takeIf { it >= 0 }?.plus(2) ?: fail("open <?")
                text.startsWith("<!--", i) -> i = text.indexOf("-->", i).takeIf { it >= 0 }?.plus(3) ?: fail("open comment")
                text.startsWith("<![CDATA[", i) -> {
                    val end = text.indexOf("]]>", i).takeIf { it >= 0 } ?: fail("open CDATA")
                    if (stack.isEmpty()) fail("CDATA outside the root")
                    stack.last().children += text.substring(i + 9, end)
                    i = end + 3
                }
                text.startsWith("<!", i) -> fail("DOCTYPE not allowed")
                text.startsWith("</", i) -> {
                    val end = text.indexOf('>', i).takeIf { it >= 0 } ?: fail("open end tag")
                    if (stack.isEmpty()) fail("unexpected end tag")
                    stack.removeAt(stack.lastIndex)
                    scopes.removeAt(scopes.lastIndex)
                    i = end + 1
                }
                else -> {
                    // A start tag: name, attributes, maybe "/>".
                    var j = i + 1
                    while (j < text.length && !text[j].isWhitespace() && text[j] != '>' && text[j] != '/') j++
                    val qName = text.substring(i + 1, j)
                    if (qName.isEmpty()) fail("empty tag name")
                    val attributes = LinkedHashMap<String, String>()
                    var selfClosing = false
                    while (true) {
                        while (j < text.length && text[j].isWhitespace()) j++
                        if (j >= text.length) fail("open tag")
                        if (text[j] == '>') { j++; break }
                        if (text.startsWith("/>", j)) { j += 2; selfClosing = true; break }
                        val eq = text.indexOf('=', j).takeIf { it >= 0 } ?: fail("attribute without value")
                        val name = text.substring(j, eq).trim()
                        var q = eq + 1
                        while (q < text.length && text[q].isWhitespace()) q++
                        val quote = text.getOrNull(q)?.takeIf { it == '"' || it == '\'' } ?: fail("unquoted attribute")
                        val close = text.indexOf(quote, q + 1).takeIf { it >= 0 } ?: fail("open attribute")
                        attributes[name] = decode(text.substring(q + 1, close))
                        j = close + 1
                    }
                    val scope = HashMap(scopes.lastOrNull() ?: emptyMap())
                    for ((name, value) in attributes) {
                        if (name == "xmlns") scope[""] = value
                        else if (name.startsWith("xmlns:")) scope[name.removePrefix("xmlns:")] = value
                    }
                    val prefix = qName.substringBefore(':', "")
                    val element = XmlElement(scope[prefix]?.ifEmpty { null }, qName.substringAfter(':'))
                    if (stack.isEmpty()) {
                        if (root != null) fail("two roots")
                        root = element
                    } else {
                        stack.last().children += element
                    }
                    if (!selfClosing) {
                        stack += element
                        scopes += scope
                    }
                    i = j
                }
            }
        }
        if (stack.isNotEmpty()) fail("unclosed <${stack.last().localName}>")
        return root ?: fail("no root element")
    }

    private fun decode(s: String): String {
        if ('&' !in s) return s
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '&') { out.append(c); i++; continue }
            val end = s.indexOf(';', i)
            if (end < 0) { out.append(c); i++; continue }
            when (val entity = s.substring(i + 1, end)) {
                "amp" -> out.append('&')
                "lt" -> out.append('<')
                "gt" -> out.append('>')
                "quot" -> out.append('"')
                "apos" -> out.append('\'')
                else -> {
                    val code = when {
                        entity.startsWith("#x") -> entity.substring(2).toIntOrNull(16)
                        entity.startsWith("#") -> entity.substring(1).toIntOrNull()
                        else -> null
                    } ?: throw IllegalArgumentException("Broken XML: unknown entity &$entity;")
                    if (code < 0x10000) out.append(code.toChar())
                    else {
                        val v = code - 0x10000
                        out.append((0xD800 + (v shr 10)).toChar()).append((0xDC00 + (v and 0x3FF)).toChar())
                    }
                }
            }
            i = end + 1
        }
        return out.toString()
    }
}
