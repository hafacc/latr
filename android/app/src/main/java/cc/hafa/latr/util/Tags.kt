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

    /** A `#tag` being typed: [start] is its `#`, the caret ends the name typed so far. */
    data class Typed(val start: Int, val prefix: String)

    /** A known tag: its first spelling seen (without `#`) and how many texts use it. */
    data class Known(val name: String, val count: Int)

    /** The tag the caret sits at the end of, or null; the name may still be empty. */
    fun typing(text: String, caret: Int): Typed? {
        if (caret <= 0) return null
        val start = text.lastIndexOf('#', caret - 1)
        if (start < 0 || !startsTag(text, start)) return null
        val prefix = text.substring(start + 1, caret)
        var index = 0
        while (index < prefix.length) {
            val codePoint = Character.codePointAt(prefix, index)
            if (!isNameChar(codePoint)) return null
            index += Character.charCount(codePoint)
        }
        if (caret < text.length && isNameChar(Character.codePointAt(text, caret))) return null
        return Typed(start, prefix)
    }

    /** How many of [texts] use each tag, by lowercased name; a text counts once per tag. */
    fun count(texts: Iterable<String>): Map<String, Known> {
        val counts = LinkedHashMap<String, Known>()
        for (text in texts) {
            for (name in names(text)) {
                val key = name.lowercase()
                val known = counts[key]
                counts[key] = known?.copy(count = known.count + 1) ?: Known(name, 1)
            }
        }
        return counts
    }

    private fun names(text: String): Collection<String> {
        val seen = LinkedHashMap<String, String>()
        for (span in parse(text)) {
            val name = text.substring(span.start + 1, span.end)
            seen.getOrPut(name.lowercase()) { name }
        }
        return seen.values
    }

    /**
     * Known tags starting with [prefix] (ignoring case), most used first, then by name.
     *
     * [ownText] is the saved text of the todo being edited: its tags are left out, being
     * there already (and so is the half-typed one it holds). A tag spelled exactly as typed
     * is left out too.
     */
    fun suggest(counts: Map<String, Known>, ownText: String, prefix: String, limit: Int = 5): List<String> {
        val own = names(ownText).map { it.lowercase() }.toSet()
        val wanted = prefix.lowercase()
        return counts.entries
            .filter { (key, _) -> key !in own && key.startsWith(wanted) && key != wanted }
            .sortedWith(compareByDescending<Map.Entry<String, Known>> { it.value.count }.thenBy { it.key })
            .take(limit)
            .map { it.value.name }
    }

    /** [text] with the typed tag replaced by `#name` and a space; returns the text and the caret after the space. */
    fun complete(text: String, typed: Typed, caret: Int, name: String): Pair<String, Int> {
        val before = text.substring(0, typed.start)
        val after = text.substring(caret)
        val tag = "#$name"
        val completed = if (after.startsWith(" ")) before + tag + after else "$before$tag $after"
        return completed to before.length + tag.length + 1
    }
}
