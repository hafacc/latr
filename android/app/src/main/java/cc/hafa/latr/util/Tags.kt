package cc.hafa.latr.util

/** `#tags` in todo text; same rules as web `utils/tags.ts`, pinned by `testdata/tag-fixtures.json`. */
object Tags {
    /** [start, end) covers the `#` and the name. */
    data class Span(val start: Int, val end: Int)

    private val openers = setOf('('.code, '['.code, '{'.code, '"'.code, '\''.code, '*'.code, '_'.code, '“'.code, '‘'.code, '«'.code)
    private val trailing = setOf('_'.code, '-'.code, '/'.code)

    // Explicit set, identical to web's `\s`.
    private fun isSpace(codePoint: Int): Boolean = when (codePoint) {
        in 0x09..0x0D, 0x20, 0x85, 0xA0, 0x1680, in 0x2000..0x200A,
        0x2028, 0x2029, 0x202F, 0x205F, 0x3000, 0xFEFF -> true
        else -> false
    }

    private fun isNameChar(codePoint: Int): Boolean =
        Character.isLetter(codePoint) || Character.getType(codePoint).toByte() == Character.DECIMAL_DIGIT_NUMBER ||
            codePoint in trailing || when (Character.getType(codePoint).toByte()) {
                Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK -> true
                else -> false
            }

    private fun startsTag(text: String, index: Int): Boolean {
        if (index == 0) return true
        val prev = text[index - 1].code
        return isSpace(prev) || prev in openers
    }

    /** Every `#tag` in [text]: a `#` at a word start followed by a name with at least one letter. */
    fun parse(text: String): List<Span> {
        if ('#' !in text) return emptyList()
        val tags = mutableListOf<Span>()
        var index = 0
        while (index < text.length) {
            if (text[index] != '#' || !startsTag(text, index)) {
                index++
                continue
            }
            var end = index + 1
            var lastKept = index + 1
            var hasLetter = false
            while (end < text.length) {
                val codePoint = Character.codePointAt(text, end)
                if (!isNameChar(codePoint)) break
                end += Character.charCount(codePoint)
                if (codePoint !in trailing) {
                    lastKept = end
                    if (Character.isLetter(codePoint)) hasLetter = true
                }
            }
            if (hasLetter) tags.add(Span(index, lastKept))
            index = maxOf(end, index + 1)
        }
        return tags
    }
}
