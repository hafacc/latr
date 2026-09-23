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
        )
        assertEquals(snapshot, SnoozeStatsWire.fromWire(SnoozeStatsWire.toWire(snapshot)))
    }

    @Test
    fun `the wire form has exactly sets, tod and lastCustom`() {
        val wire = SnoozeStatsWire.toWire(SnoozeStatsSnapshot())
        assertEquals(setOf("sets", "tod", "lastCustom"), wire.keys)
        assertFalse("deviceId" in wire)
        assertNull(wire["lastCustom"])
    }

    @Test
    fun `stale and malformed entries are dropped`() {
        val raw = mapOf(
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
    fun `a missing partition reads as empty`() {
        assertEquals(SnoozeStatsSnapshot(), SnoozeStatsWire.fromWire(null))
        assertEquals(SnoozeStatsSnapshot(), SnoozeStatsWire.fromWire(mapOf("deviceId" to "abc")))
    }
}
