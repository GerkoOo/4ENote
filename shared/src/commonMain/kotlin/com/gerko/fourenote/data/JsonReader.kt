package com.gerko.fourenote.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reads JSON front to back without first building a tree of it. A long note holds millions of
 * numbers; as a tree (an object per number) it takes several hundred MB, more than Android gives
 * an app. Big parts (pen points) are read straight into arrays; small parts can still be read as
 * a [JsonElement] with [element].
 */
class JsonReader(private val text: String) {
    private var pos = 0

    /** Reads an object, calling [onKey] for each key; [onKey] must read (or [skip]) its value. */
    fun obj(onKey: (String) -> Unit) {
        expect('{')
        if (peek() == '}') {
            next()
            return
        }
        while (true) {
            val key = string()
            expect(':')
            onKey(key)
            when (next()) {
                ',' -> continue
                '}' -> return
                else -> fail("',' or '}' expected")
            }
        }
    }

    /** Reads an array, calling [onItem] for each item; [onItem] must read (or [skip]) it. */
    fun arr(onItem: () -> Unit) {
        expect('[')
        if (peek() == ']') {
            next()
            return
        }
        while (true) {
            onItem()
            when (next()) {
                ',' -> continue
                ']' -> return
                else -> fail("',' or ']' expected")
            }
        }
    }

    /** An array of numbers, as floats; null (or anything not an array) gives null. */
    fun floats(): FloatArray? {
        if (peek() != '[') {
            skip()
            return null
        }
        var out = FloatArray(64)
        var size = 0
        arr {
            if (size == out.size) out = out.copyOf(size * 2)
            val c = peek()
            out[size++] = if (c == '-' || c in '0'..'9') {
                numberText().toFloatOrNull() ?: 0f
            } else {
                // Not a number (damaged note): counts as 0, as before.
                skip()
                0f
            }
        }
        return out.copyOf(size)
    }

    fun string(): String {
        expect('"')
        val start = pos
        // Most strings have no escapes: take them as they are, without building them up.
        while (pos < text.length) {
            val c = text[pos]
            if (c == '"') return text.substring(start, pos++)
            if (c == '\\') break
            pos++
        }
        val out = StringBuilder(pos - start + 16).append(text, start, pos)
        while (true) {
            if (pos >= text.length) fail("unterminated string")
            val c = text[pos++]
            when (c) {
                '"' -> return out.toString()
                '\\' -> {
                    if (pos >= text.length) fail("unterminated string")
                    when (val e = text[pos++]) {
                        '"', '\\', '/' -> out.append(e)
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            if (pos + 4 > text.length) fail("bad \\u escape")
                            out.append(text.substring(pos, pos + 4).toInt(16).toChar())
                            pos += 4
                        }
                        else -> fail("bad escape")
                    }
                }
                else -> out.append(c)
            }
        }
    }

    /** Any value, as a [JsonElement]: for small parts only. */
    fun element(): JsonElement = when (peek()) {
        '{' -> {
            val map = LinkedHashMap<String, JsonElement>()
            obj { key -> map[key] = element() }
            JsonObject(map)
        }
        '[' -> {
            val list = ArrayList<JsonElement>()
            arr { list += element() }
            JsonArray(list)
        }
        '"' -> JsonPrimitive(string())
        't', 'f' -> JsonPrimitive(literal() == "true")
        'n' -> {
            literal()
            JsonNull
        }
        else -> {
            val raw = numberText()
            // Whole numbers stay whole, so reading them as Int (a color) still works.
            if (raw.any { it == '.' || it == 'e' || it == 'E' }) JsonPrimitive(raw.toDouble()) else JsonPrimitive(raw.toLong())
        }
    }

    /** Skips any value. */
    fun skip() {
        when (peek()) {
            '{' -> obj { skip() }
            '[' -> arr { skip() }
            '"' -> string()
            't', 'f', 'n' -> literal()
            else -> numberText()
        }
    }

    /** True once only whitespace is left. */
    fun atEnd(): Boolean {
        skipSpace()
        return pos >= text.length
    }

    fun peek(): Char {
        skipSpace()
        if (pos >= text.length) fail("unexpected end")
        return text[pos]
    }

    fun next(): Char {
        val c = peek()
        pos++
        return c
    }

    fun expect(c: Char) {
        if (next() != c) fail("'$c' expected")
    }

    fun fail(message: String): Nothing = throw IllegalArgumentException("JSON: $message at $pos")

    private fun numberText(): String {
        skipSpace()
        val start = pos
        while (pos < text.length && text[pos].let { it == '-' || it == '+' || it == '.' || it == 'e' || it == 'E' || it in '0'..'9' }) pos++
        if (pos == start) fail("value expected")
        return text.substring(start, pos)
    }

    private fun literal(): String {
        skipSpace()
        val start = pos
        while (pos < text.length && text[pos] in 'a'..'z') pos++
        return text.substring(start, pos).also { if (it != "true" && it != "false" && it != "null") fail("value expected") }
    }

    private fun skipSpace() {
        while (pos < text.length && text[pos].let { it == ' ' || it == '\n' || it == '\r' || it == '\t' }) pos++
    }

    companion object {
        /** [value] as a JSON string, quotes included, appended to [out]. */
        fun quote(value: String, out: Appendable) {
            out.append('"')
            for (c in value) {
                when (c) {
                    '"' -> out.append("\\\"")
                    '\\' -> out.append("\\\\")
                    '\n' -> out.append("\\n")
                    '\r' -> out.append("\\r")
                    '\t' -> out.append("\\t")
                    else -> if (c < ' ') {
                        out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                    } else {
                        out.append(c)
                    }
                }
            }
            out.append('"')
        }
    }
}
