package cc.hafa.latr.util

import cc.hafa.latr.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Test

class TagsFixturesTest {
    @Suppress("UNCHECKED_CAST")
    private val cases = Fixtures.load("tag-fixtures.json")["cases"] as List<Map<String, Any?>>

    @Test
    fun `parse matches the shared fixture`() {
        for (case in cases) {
            @Suppress("UNCHECKED_CAST")
            val expected = (case["tags"] as List<List<Number>>).map { (start, end) -> listOf(start.toInt(), end.toInt()) }
            val actual = Tags.parse(case["in"] as String).map { listOf(it.start, it.end) }
            assertEquals("${case["name"]}", expected, actual)
        }
    }
}
