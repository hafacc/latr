package cc.hafa.latr.util

import org.junit.Assert.assertEquals
import org.junit.Test

class InfoPagesTest {
    @Test
    fun `splits links out of the text`() {
        assertEquals(
            listOf(TextPart("Use the "), TextPart("page", "a/"), TextPart(", then go.")),
            InfoPages.textParts("Use the [page](a/), then go."),
        )
    }

    @Test
    fun `keeps text without links whole`() {
        assertEquals(listOf(TextPart("plain")), InfoPages.textParts("plain"))
    }

    @Test
    fun `the privacy page links to account deletion`() {
        val json = requireNotNull(javaClass.classLoader?.getResourceAsStream(InfoPages.PRIVACY_ASSET))
            .bufferedReader().use { it.readText() }
        val page = InfoPages.parse(json)
        assertEquals("Privacy", page.title)
        assertEquals(
            listOf(TextPart("delete account page", "delete-account/")),
            page.sections.flatMap { InfoPages.textParts(it.body) }.filter { it.href != null },
        )
    }
}
