package cc.hafa.latr.util

import org.json.JSONObject

/** One headed section of an info page. */
data class InfoSection(val heading: String, val body: String)

/** An info page's text, read from `content/` (shared with web). */
data class InfoPage(val title: String, val sections: List<InfoSection>, val closing: String)

/** A run of page text, linked to a site path when [href] is set. */
data class TextPart(val text: String, val href: String? = null)

/** Info pages bundled from the repo's `content/`, and where their web copies live. */
object InfoPages {
    /** The web app's address, which info page links are relative to. */
    const val SITE_URL = "https://latr.hafa.cc/"

    /** Web address of the project's issue tracker, where feedback goes. */
    const val FEEDBACK_URL = "https://github.com/hafacc/latr/issues"

    /** Asset holding the privacy page. */
    const val PRIVACY_ASSET = "privacy.json"

    /** Site path of the privacy page. */
    const val PRIVACY_PATH = "privacy/"

    private val link = Regex("""\[([^\]]+)]\(([^)]+)\)""")

    /** Read a page from its JSON. */
    fun parse(json: String): InfoPage {
        val page = JSONObject(json)
        val sections = page.getJSONArray("sections")
        return InfoPage(
            title = page.getString("title"),
            sections = (0 until sections.length()).map { index ->
                val section = sections.getJSONObject(index)
                InfoSection(section.getString("heading"), section.getString("body"))
            },
            closing = page.getString("closing"),
        )
    }

    /** Split `[text](path)` links out of a page's text. */
    fun textParts(text: String): List<TextPart> {
        val parts = mutableListOf<TextPart>()
        var end = 0
        for (match in link.findAll(text)) {
            if (match.range.first > end) parts += TextPart(text.substring(end, match.range.first))
            parts += TextPart(match.groupValues[1], match.groupValues[2])
            end = match.range.last + 1
        }
        if (end < text.length) parts += TextPart(text.substring(end))
        return parts
    }
}
