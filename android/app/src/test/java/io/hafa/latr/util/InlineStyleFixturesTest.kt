package io.hafa.latr.util

import io.hafa.latr.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineStyleFixturesTest {
    @Suppress("UNCHECKED_CAST")
    private val cases = Fixtures.load("inline-style-fixtures.json")["cases"] as List<Map<String, Any?>>

    @Test
    fun `parse and plain match the shared fixture`() {
        for (case in cases) {
            val name = case["name"] as String
            val input = case["in"] as String
            val expected = (case["spans"] as List<List<Any?>>).map { (style, start, end) ->
                listOf(style, (start as Number).toInt(), (end as Number).toInt())
            }
            val actual = InlineStyle.parse(input).map { listOf(it.style.name.lowercase(), it.start, it.end) }
            assertEquals("$name: spans", expected, actual)
            assertEquals("$name: plain", case["plain"], InlineStyle.plain(input))
        }
    }

    @Test
    fun `adversarial long text parses in linear time`() {
        val text = "*a ".repeat(20_000) + "b_ ".repeat(20_000)
        val started = System.nanoTime()
        InlineStyle.parse(text)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("took ${elapsedMs}ms", elapsedMs < 200)
    }
}
