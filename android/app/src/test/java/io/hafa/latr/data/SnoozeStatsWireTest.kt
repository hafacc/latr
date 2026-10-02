package io.hafa.latr.data

import io.hafa.latr.util.LastCustom
import io.hafa.latr.util.SetStat
import io.hafa.latr.util.SnoozeStatsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SnoozeStatsWireTest {

    @Test
    fun `a partition round-trips through the wire form`() {
        val snapshot = SnoozeStatsSnapshot(
            sets = mapOf("D1@0900__Wd4@0900" to SetStat(1.5, 10L), "D0_h3" to SetStat(1.0, 20L)),
            tod = mapOf("0900" to SetStat(2.0, 30L)),
            lastCustom = LastCustom(100L, 40L),
            picks = mapOf("1" to 3L, "none" to 1L),
        )
        assertEquals(snapshot, SnoozeStatsWire.fromWire(SnoozeStatsWire.toWire(snapshot)))
    }

    @Test
    fun `the wire form has exactly v, sets, tod, lastCustom and picks`() {
        val wire = SnoozeStatsWire.toWire(SnoozeStatsSnapshot())
        assertEquals(setOf("v", "sets", "tod", "lastCustom", "picks"), wire.keys)
        assertFalse("deviceId" in wire)
        assertNull(wire["lastCustom"])
    }

    @Test
    fun `stale and malformed entries are dropped`() {
        val raw = mapOf(
            "v" to 2L,
            "sets" to mapOf(
                "D1_morning__Wd4_morning" to mapOf("c" to 1.0, "t" to 1L),
                "D1@0900__D1_morning" to mapOf("c" to 1.0, "t" to 1L),
                "D1@0900" to mapOf("c" to Double.NaN, "t" to 1L),
                "D1@0905" to mapOf("c" to "1", "t" to 1L),
                "Wd4@0900" to mapOf("c" to 2, "t" to 5),
            ),
            "tod" to mapOf(
                "morning" to mapOf("c" to 1.0, "t" to 1L),
                "0907" to mapOf("c" to 1.0, "t" to 1L),
                "2400" to mapOf("c" to 1.0, "t" to 1L),
                "2355" to mapOf("c" to 1.0, "t" to 1L),
            ),
            "patterns" to mapOf("D1_morning" to mapOf("bins" to mapOf("b180" to 1.0), "lb" to "b180", "t" to 1L)),
            "lastCustom" to mapOf("target" to 5L),
        )
        assertEquals(
            SnoozeStatsSnapshot(
                sets = mapOf("Wd4@0900" to SetStat(2.0, 5L)),
                tod = mapOf("2355" to SetStat(1.0, 1L)),
                lastCustom = null,
            ),
            SnoozeStatsWire.fromWire(raw),
        )
    }

    @Test
    fun `Firestore longs and whole doubles both read as pick counts`() {
        val raw = mapOf("picks" to mapOf("1" to 2L, "7" to 3.0, "2" to 2.5, "3" to Double.NaN, "none" to 0L, "m1" to 1L))
        assertEquals(mapOf("1" to 2L, "7" to 3L), SnoozeStatsWire.fromWire(raw).picks)
    }

    @Test
    fun `a missing partition reads as empty`() {
        assertEquals(SnoozeStatsSnapshot(), SnoozeStatsWire.fromWire(null))
        assertEquals(SnoozeStatsSnapshot(), SnoozeStatsWire.fromWire(mapOf("deviceId" to "abc")))
    }

    @Test
    fun `global pick counts put none first, then ranks, missing as 0`() {
        val counts = SnoozeStatsWire.globalPickCounts(mapOf("none" to 4L, "1" to 7L, "20" to 2L, "key" to "1"))
        assertEquals(21, counts.size)
        assertEquals(listOf(4L, 7L, 0L), counts.take(3))
        assertEquals(2L, counts[20])
        assertEquals(List(21) { 0L }, SnoozeStatsWire.globalPickCounts(null))
    }

    private val zone = java.time.ZoneId.of("America/New_York")
    private val at = java.time.LocalDateTime.of(2026, 9, 22, 10, 0).atZone(zone).toInstant().toEpochMilli()
    private val day = 24 * 60 * 60 * 1000L

    private fun block(): SnoozeStatsSnapshot {
        var stats = SnoozeStatsSnapshot()
        stats = io.hafa.latr.util.SnoozeSuggestions.commit(stats, at, at + day, zone, "custom").next
        stats = io.hafa.latr.util.SnoozeSuggestions.commit(stats, at + day, at + 3 * day, zone, "custom").next
        return io.hafa.latr.util.SnoozeSuggestions.commit(stats, at + 2 * day, at + 40 * day, zone, "custom").next
    }

    @Test
    fun `counts saved scaled rank the same as the block they came from`() {
        val block = block()
        val deltas = SnoozeStatsWire.voteDeltas(SnoozeStatsSnapshot(), block)
        val shared = SnoozeStatsWire.sharedFromWire(mapOf("sets" to deltas.sets, "tod" to deltas.tod))
        val now = at + 30 * day
        fun rows(stats: SnoozeStatsSnapshot) =
            io.hafa.latr.util.SnoozeSuggestions.rankDeep(listOf(stats), java.time.Instant.ofEpochMilli(now), zone).map { Triple(it.key, it.epochMillis, "%.9f".format(it.score)) }
        assertEquals(rows(block), rows(shared))
        assertEquals(false, rows(block).isEmpty())
    }

    @Test
    fun `a vote adds two to the power of half-lives since the epoch`() {
        val one = io.hafa.latr.util.SnoozeSuggestions.commit(SnoozeStatsSnapshot(), at, at + day, zone, "custom").next
        val added = SnoozeStatsWire.voteDeltas(SnoozeStatsSnapshot(), one).sets.values.single()
        assertEquals(Math.pow(2.0, (at - SnoozeStatsWire.VOTE_EPOCH) / (21.0 * day)), added, 1e-6)
    }

    @Test
    fun `shared counts at or below zero and malformed ids are dropped`() {
        val shared = SnoozeStatsWire.sharedFromWire(
            mapOf(
                "sets" to mapOf("D0@0900" to 2.0, "D0@1000" to 0.0, "D0@1100" to -1.0, "bogus" to 3.0),
                "tod" to mapOf("0900" to 1L, "0901" to 1L),
            ),
        )
        assertEquals(setOf("D0@0900"), shared.sets.keys)
        assertEquals(setOf("0900"), shared.tod.keys)
    }

    @Test
    fun `folding sums the blocks not yet folded`() {
        val wire = SnoozeStatsWire.toWire(block().copy(picks = mapOf("1" to 2L)))
        val one = SnoozeStatsWire.foldBlocks(mapOf("a" to wire), null, zone)
        val fold = SnoozeStatsWire.foldBlocks(mapOf("a" to wire, "b" to wire, "c" to wire), mapOf("c" to true), zone)
        assertEquals(listOf("a", "b"), fold.ids)
        assertEquals(mapOf("1" to 4L), fold.picks)
        assertEquals(block().lastCustom, fold.lastCustom)
        for ((id, amount) in one.deltas.sets) assertEquals(2 * amount, fold.deltas.sets.getValue(id), amount * 1e-9)
    }
}
