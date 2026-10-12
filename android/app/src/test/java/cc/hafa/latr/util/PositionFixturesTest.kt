package cc.hafa.latr.util

import cc.hafa.latr.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Test

class PositionFixturesTest {
    private val fixtures = Fixtures.load("position-fixtures.json")

    @Suppress("UNCHECKED_CAST")
    private fun cases(section: String) = fixtures[section] as List<Map<String, Any?>>

    @Test
    fun `timeKey matches the shared fixture`() {
        for (case in cases("timeKey")) {
            // The web side floors a fractional time; Kotlin only ever has whole milliseconds.
            val epoch = Math.floor((case["epoch"] as Number).toDouble()).toLong()
            assertEquals("$epoch", case["key"], Position.timeKey(epoch))
        }
    }

    @Test
    fun `idKey matches the shared fixture`() {
        for (case in cases("idKey")) {
            assertEquals(case["key"], Position.idKey(case["id"] as String))
        }
    }

    @Test
    fun `readPosition matches the shared fixture`() {
        for (case in cases("readPosition")) {
            assertEquals("${case["in"]}", case["out"], Position.readPosition(case["in"]))
        }
    }
}
