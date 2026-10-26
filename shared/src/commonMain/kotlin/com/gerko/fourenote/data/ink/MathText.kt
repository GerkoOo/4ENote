package com.gerko.fourenote.data.ink

/**
 * Splits typed text into plain text and maths, the way LaTeX-aware note apps do:
 * `$…$` is maths inside a line, `$$…$$` is maths on its own centered line, `\$` is a
 * dollar sign. A `$` that is never closed (still being typed) stays plain text.
 */
object MathText {
    sealed interface Part {
        data class Plain(val text: String) : Part
        data class Math(val latex: String, val display: Boolean) : Part
    }

    fun split(text: String): List<Part> {
        val parts = ArrayList<Part>()
        val plain = StringBuilder()
        fun flushPlain() {
            if (plain.isNotEmpty()) parts += Part.Plain(plain.toString())
            plain.clear()
        }
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length && text[i + 1] == '$') {
                plain.append('$')
                i += 2
                continue
            }
            if (c != '$') {
                plain.append(c)
                i++
                continue
            }
            val display = i + 1 < text.length && text[i + 1] == '$'
            val open = if (display) 2 else 1
            val close = closingDollar(text, i + open, display)
            val latex = if (close >= 0) text.substring(i + open, close).trim() else ""
            if (close < 0 || latex.isEmpty()) {
                // Not (yet) a formula: keep the dollar signs as typed.
                plain.append(text, i, i + open)
                i += open
                continue
            }
            flushPlain()
            parts += Part.Math(latex, display)
            i = close + open
        }
        flushPlain()
        return parts
    }

    /** True if [text] contains at least one complete formula. */
    fun hasMath(text: String) = split(text).any { it is Part.Math }

    // The index of the `$` (or `$$`) closing a formula that starts at [from], skipping `\$`.
    private fun closingDollar(text: String, from: Int, display: Boolean): Int {
        var j = from
        while (j < text.length) {
            when {
                text[j] == '\\' -> j += 2
                text[j] == '$' && (!display || (j + 1 < text.length && text[j + 1] == '$')) -> return j
                // Inline maths ends at the line: an unclosed `$` doesn't swallow the next lines.
                !display && text[j] == '\n' -> return -1
                else -> j++
            }
        }
        return -1
    }
}
