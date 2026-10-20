package cc.hafa.latr.util

import cc.hafa.latr.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Test

@Suppress("UNCHECKED_CAST")
class RepeatFixturesTest {
    private val fixtures = Fixtures.load("repeat-fixtures.json")

    private fun cases(section: String) = fixtures[section] as List<Map<String, Any?>>

    private fun rule(value: Any?): Repeat = requireNotNull(Repeat.read(value)) { "unreadable $value" }

    @Test
    fun `next occurrence matches the shared fixture`() {
        for (case in cases("next")) {
            assertEquals(case["name"] as String, case["next"], rule(case["repeat"]).nextOccurrence(case["after"] as String))
        }
    }

    @Test
    fun `anchor matches the shared fixture`() {
        for (case in cases("anchor")) {
            val raw = case["repeat"] as Map<String, Any?>
            val unit = RepeatUnit.entries.first { it.wire == raw["unit"] }
            val anchored = Repeat.anchored(
                (raw["every"] as Number).toInt(),
                unit,
                (raw["weekday"] as Number).toInt(),
                (raw["monthDay"] as Number).toInt(),
                raw["at"] as String,
                case["now"] as String,
            )
            assertEquals(case["name"] as String, case["anchor"], anchored.anchor)
        }
    }

    @Test
    fun `text matches the shared fixture`() {
        for (case in cases("text")) {
            assertEquals(case["text"], rule(case["repeat"]).text())
        }
    }

    @Test
    fun `read matches the shared fixture`() {
        for (case in cases("read")) {
            val expected = case["out"]?.let { rule(it) }
            assertEquals(case["name"] as String, expected, Repeat.read(case["in"]))
        }
    }

    @Test
    fun `json round trips`() {
        val repeat = rule(cases("next").first()["repeat"])
        assertEquals(repeat, Repeat.fromJson(repeat.toJson()))
    }
}
