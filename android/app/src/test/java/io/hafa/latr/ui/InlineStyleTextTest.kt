package io.hafa.latr.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineStyleTextTest {
    private val base = Color.Black

    private fun styles(text: String) =
        styledTodoText(text, base).spanStyles.map { Triple(it.item, it.start, it.end) }

    @Test
    fun `plain text carries no styles`() {
        assertTrue(styles("buy milk").isEmpty())
    }

    @Test
    fun `bold covers the span and dims both markers`() {
        val marker = SpanStyle(color = base.copy(alpha = 0.45f))
        assertEquals(
            listOf(
                Triple(SpanStyle(fontWeight = FontWeight.SemiBold), 4, 10),
                Triple(marker, 4, 5),
                Triple(marker, 9, 10),
            ),
            styles("buy *milk* now"),
        )
    }

    @Test
    fun `italic uses the italic font style`() {
        assertEquals(SpanStyle(fontStyle = FontStyle.Italic), styles("_later_").first().first)
    }

    @Test
    fun `the editor transformation keeps text and offsets unchanged`() {
        val transformed = InlineStyleTransformation(base)
            .filter(androidx.compose.ui.text.AnnotatedString("a *b* c"))
        assertEquals("a *b* c", transformed.text.text)
        assertEquals(3, transformed.offsetMapping.originalToTransformed(3))
    }
}
