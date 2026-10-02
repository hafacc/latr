package io.hafa.latr.util

import io.hafa.latr.data.SnoozeStatsWire
import io.hafa.latr.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Runs the shared web/Android fixture so both platforms provably agree on keys, times and labels. */
class SnoozeFixturesTest {

    private val zone: ZoneId = ZoneId.of("America/New_York")

    private fun epoch(local: Any?): Long = LocalDateTime.parse(local as String).atZone(zone).toInstant().toEpochMilli()

    private fun instant(local: Any?): Instant = Instant.ofEpochMilli(epoch(local))

    @Test
    fun extract() {
        for (case in Fixtures.cases("extract")) {
            val name = case["name"]
            val keys = SnoozeSuggestions.extract(instant(case["at"]), instant(case["target"]), zone)
            assertEquals("$name: keys", case["keys"], keys.sorted())
            val setId = if (keys.isEmpty()) null else SnoozeSuggestions.setId(keys)
            assertEquals("$name: setId", case["setId"], setId)
        }
    }

    @Test
    fun quickCredit() {
        for (case in Fixtures.cases("quickCredit")) {
            val name = case["name"]
            val result = SnoozeSuggestions.commit(
                SnoozeStatsSnapshot(),
                epoch(case["at"]),
                epoch(case["target"]),
                zone,
                case["source"] as String,
                case["pickedKey"] as String?,
            )
            assertEquals("$name", case["slot"], result.touchedTodSlot)
        }
    }

    @Test
    fun resolve() {
        for (case in Fixtures.cases("resolve")) {
            val name = case["name"]
            val expected = case["epochLocal"]?.let { epoch(it) }
            assertEquals("$name", expected, SnoozeSuggestions.resolveFuture(case["key"] as String, instant(case["now"]), zone))
        }
    }

    @Test
    fun labelDist() {
        for (case in Fixtures.cases("labelDist")) {
            val name = case["name"]
            val now = instant(case["now"])
            val key = case["key"] as String
            val resolved = epoch(case["resolvedLocal"])
            assertEquals("$name: family", case["family"], SnoozeSuggestions.familyOfRule(SnoozeSuggestions.dateRuleOf(key)))
            assertEquals("$name: dist", (case["dist"] as Number).toInt(), SnoozeSuggestions.labelDist(resolved, now, zone))
            assertEquals("$name: weekday", (case["weekday"] as Number).toInt(), SnoozeSuggestions.labelWeekday(resolved, zone))
            assertEquals("$name: phrase", case["phrase"], SnoozeSuggestions.phraseKind(key, resolved, now, zone))
            case["region"]?.let {
                assertEquals("$name: region", it, SnoozeSuggestions.regionOf(resolved, zone))
            }
        }
    }

    @Test
    fun labelOffset() {
        for (case in Fixtures.cases("labelOffset")) {
            assertEquals("${case["name"]}", case["phrase"], SnoozeSuggestions.offsetPhraseKind(case["key"] as String))
        }
    }

    @Test
    fun quickTimes() {
        for (case in Fixtures.cases("quickTimes")) {
            val partitions = (case["partitions"] as List<*>).map { SnoozeStatsWire.fromWire(mapOf("tod" to it)) }
            val actual = SnoozeSuggestions.quickTimes(partitions, instant(case["now"])).map { it.clockMinutes }
            val expected = (case["expect"] as List<*>).map { (it as Number).toInt() }
            assertEquals("${case["name"]}", expected, actual)
        }
    }

    @Test
    fun rank() {
        for (case in Fixtures.cases("rank")) {
            val partitions = (case["partitions"] as List<*>).map { SnoozeStatsWire.fromWire(it as Map<*, *>, zone) }
            val actual = SnoozeSuggestions.rank(partitions, instant(case["now"]), zone).map { it.key }
            assertEquals("${case["name"]}", case["expectKeys"], actual)
            val deep = SnoozeSuggestions.rankDeep(partitions, instant(case["now"]), zone)
            assertEquals("${case["name"]}: via deep", case["expectKeys"], SnoozeSuggestions.shownRows(deep).map { it.key })
        }
    }

    @Test
    fun rankDeep() {
        for (case in Fixtures.cases("rankDeep")) {
            val name = case["name"]
            val partitions = (case["partitions"] as List<*>).map { SnoozeStatsWire.fromWire(it as Map<*, *>, zone) }
            val deep = SnoozeSuggestions.rankDeep(partitions, instant(case["now"]), zone)
            assertEquals("$name", case["expectKeys"], deep.map { it.key })
            assertEquals("$name: shown", case["expectShownKeys"], SnoozeSuggestions.shownRows(deep).map { it.key })
            val shown = SnoozeSuggestions.rank(partitions, instant(case["now"]), zone).map { it.key }
            assertEquals("$name: rank", case["expectShownKeys"], shown)
        }
    }

    @Test
    fun pickLog() {
        for (case in Fixtures.cases("pickLog")) {
            val partitions = (case["partitions"] as List<*>).map { SnoozeStatsWire.fromWire(it as Map<*, *>, zone) }
            val deep = SnoozeSuggestions.rankDeep(partitions, instant(case["now"]), zone)
            val actual = SnoozeSuggestions.pickLogKey(deep, epoch(case["target"]))
            assertEquals("${case["name"]}", case["expect"], actual)
        }
    }

    private fun assertCounters(name: String, expected: Any?, actual: Map<String, SetStat>) {
        val want = expected as Map<*, *>
        assertEquals("$name: ids", want.keys, actual.keys)
        for ((id, raw) in want) {
            val entry = raw as Map<*, *>
            val got = actual.getValue(id as String)
            val count = (entry["c"] as Number).toDouble()
            assertEquals("$name: $id t", (entry["t"] as Number).toLong(), got.t)
            assertEquals("$name: $id c", count, got.c, 1e-9 * maxOf(1.0, count))
        }
    }

    @Test
    fun commit() {
        for (case in Fixtures.cases("commit")) {
            val name = "${case["name"]}"
            val before = SnoozeStatsWire.fromWire(case["partition"] as Map<*, *>, zone)
            val result = SnoozeSuggestions.commit(
                before,
                epoch(case["at"]),
                epoch(case["target"]),
                zone,
                case["source"] as String,
                case["pickedKey"] as String?,
            )
            val expect = case["expect"] as Map<*, *>
            assertCounters("$name: sets", expect["sets"], result.next.sets)
            assertCounters("$name: tod", expect["tod"], result.next.tod)
            assertEquals("$name: undo", before, SnoozeSuggestions.revert(result.next, result))
        }
    }

    @Test
    fun wire() {
        for (case in Fixtures.cases("wire")) {
            val name = case["name"]
            val normalized = Fixtures.numbersAsDouble(case["normalized"])
            val parsed = SnoozeStatsWire.fromWire(case["raw"] as Map<*, *>, zone)
            assertEquals("$name: fromWire", normalized, Fixtures.numbersAsDouble(SnoozeStatsWire.toWire(parsed)))
            val reparsed = SnoozeStatsWire.fromWire(SnoozeStatsWire.toWire(parsed), zone)
            assertEquals("$name: round trip", parsed, reparsed)
        }
    }
}
