package cc.hafa.latr.util

import cc.hafa.latr.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

@Suppress("UNCHECKED_CAST")
class TagCompleteFixturesTest {
    private val fixtures = Fixtures.load("tag-complete-fixtures.json")

    private fun cases(section: String) = fixtures[section] as List<Map<String, Any?>>

    @Test
    fun `typing matches the shared fixture`() {
        for (case in cases("typing")) {
            val expected = (case["out"] as List<Any>?)?.let { (start, prefix) -> listOf((start as Number).toInt(), prefix) }
            val typed = Tags.typing(case["in"] as String, (case["caret"] as Number).toInt())
            assertEquals("${case["name"]}", expected, typed?.let { listOf(it.start, it.prefix) })
        }
    }

    @Test
    fun `suggest matches the shared fixture`() {
        for (case in cases("suggest")) {
            val counts = Tags.count(case["texts"] as List<String>)
            val limit = (case["limit"] as Number?)?.toInt() ?: 5
            val actual = Tags.suggest(counts, case["own"] as String, case["prefix"] as String, limit)
            assertEquals("${case["name"]}", case["out"], actual)
        }
    }

    @Test
    fun `complete matches the shared fixture`() {
        for (case in cases("complete")) {
            val text = case["in"] as String
            val caret = (case["caret"] as Number).toInt()
            val typed = Tags.typing(text, caret)
            assertNotNull("${case["name"]}", typed)
            val (completed, newCaret) = Tags.complete(text, typed!!, caret, case["pick"] as String)
            assertEquals("${case["name"]}", listOf(case["out"], (case["outCaret"] as Number).toInt()), listOf(completed, newCaret))
        }
    }
}
