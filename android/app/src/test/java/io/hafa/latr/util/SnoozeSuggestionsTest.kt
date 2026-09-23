package io.hafa.latr.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class SnoozeSuggestionsTest {

    private val zone: ZoneId = ZoneId.of("America/New_York")

    private fun epoch(dt: LocalDateTime): Long = dt.atZone(zone).toInstant().toEpochMilli()

    private fun instant(dt: LocalDateTime): Instant = dt.atZone(zone).toInstant()

    private fun atZone(epochMillis: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone)


    private fun commitOnce(
        stats: SnoozeStatsSnapshot,
        at: LocalDateTime,
        target: LocalDateTime,
        source: String = "custom",
        pickedKey: String? = null,
    ): CommitResult = SnoozeSuggestions.commit(stats, epoch(at), epoch(target), zone, source, pickedKey)

    private fun keys(at: LocalDateTime, target: LocalDateTime) =
        SnoozeSuggestions.extract(instant(at), instant(target), zone)

    private fun label(key: String, resolved: LocalDateTime, now: LocalDateTime) =
        SnoozeSuggestions.labelFor(key, epoch(resolved), instant(now), zone)

    @Test
    fun `setId is order independent`() {
        val a = SnoozeSuggestions.setId(setOf("Wd1@0900", "D3@0900"))
        val b = SnoozeSuggestions.setId(setOf("D3@0900", "Wd1@0900"))
        assertEquals(a, b)
        assertEquals("D3@0900__Wd1@0900", a)
    }

    @Test
    fun `a 2h48m gap credits both the in-3-hours offset and the exact clock slot`() {
        val ks = keys(LocalDateTime.of(2024, 1, 15, 13, 12), LocalDateTime.of(2024, 1, 15, 16, 0))
        assertEquals(setOf("D0@1600", "D0_h3"), ks)
    }

    @Test
    fun `a same-day snooze credits only later-today keys`() {
        assertEquals(
            setOf("D0@2000", "D0_h6"),
            keys(LocalDateTime.of(2026, 9, 21, 14, 0), LocalDateTime.of(2026, 9, 21, 20, 0)),
        )
        assertEquals(
            setOf("D0@0130", "D0_h3"),
            keys(LocalDateTime.of(2026, 9, 21, 23, 0), LocalDateTime.of(2026, 9, 22, 1, 30)),
        )
    }

    @Test
    fun `clock slots round to 5 minutes but never across the 5am boundary`() {
        val at = LocalDateTime.of(2026, 9, 21, 10, 0)
        assertTrue("D1@0905" in keys(at, LocalDateTime.of(2026, 9, 22, 9, 7)))
        assertTrue("D1@0910" in keys(at, LocalDateTime.of(2026, 9, 22, 9, 8)))
        assertTrue("D1@0455" in keys(at, LocalDateTime.of(2026, 9, 23, 4, 58)))
    }

    @Test
    fun `a commit made 00-05 gets offset keys that agree with its timed keys about the day`() {
        val at = LocalDateTime.of(2024, 1, 8, 1, 0)
        assertEquals(DayOfWeek.MONDAY, at.dayOfWeek)
        assertEquals(setOf("D1@0100", "Wd1@0100", "D1_h0", "Wd1_h0"), keys(at, at.plusDays(1)))
    }

    @Test
    fun `resolving a Wd offset key learned overnight lands +24h from now, not a week later`() {
        val at = LocalDateTime.of(2024, 1, 8, 1, 0)
        val stats = commitOnce(SnoozeStatsSnapshot(), at, at.plusDays(1)).next
        val now = LocalDateTime.of(2024, 1, 15, 1, 0)
        val rows = SnoozeSuggestions.rank(listOf(stats), instant(now), zone)
        assertEquals(1, rows.size)
        assertEquals(epoch(now.plusDays(1)), rows[0].epochMillis)
    }

    @Test
    fun `a daytime commit gets no offset keys when the gap rounds negative`() {
        assertEquals(
            setOf("D1@0800", "Wd2@0800"),
            keys(LocalDateTime.of(2024, 1, 1, 10, 0), LocalDateTime.of(2024, 1, 2, 8, 0)),
        )
    }

    @Test
    fun `decay then credit produces the expected set count one half-life later`() {
        val at1 = LocalDateTime.of(2024, 1, 1, 10, 0)
        val target1 = LocalDateTime.of(2024, 1, 2, 8, 0)
        val r1 = commitOnce(SnoozeStatsSnapshot(), at1, target1)
        val id = requireNotNull(r1.touchedSetId)
        assertEquals(1.0, r1.next.sets.getValue(id).c, 1e-9)
        val r2 = commitOnce(r1.next, at1.plusDays(21), target1.plusDays(21))
        assertEquals(id, r2.touchedSetId)
        assertEquals(1.5, r2.next.sets.getValue(id).c, 1e-6)
    }

    @Test
    fun `undo reverts a commit's set and quick-time slot exactly`() {
        val at = LocalDateTime.of(2024, 1, 1, 10, 0)
        val target = LocalDateTime.of(2024, 1, 2, 8, 0)
        val first = commitOnce(SnoozeStatsSnapshot(), at, target)
        assertEquals("0800", first.touchedTodSlot)
        val second = commitOnce(first.next, at.plusDays(1), target.plusDays(1))
        assertEquals(first.next, SnoozeSuggestions.revert(second.next, second))
        assertEquals(SnoozeStatsSnapshot(), SnoozeSuggestions.revert(first.next, first))
    }

    @Test
    fun `undo of a commit that gave no quick-time credit leaves quick times untouched`() {
        val before = SnoozeStatsSnapshot()
        val result = commitOnce(before, LocalDateTime.of(2024, 1, 1, 10, 0), LocalDateTime.of(2024, 1, 1, 13, 0))
        assertNull(result.touchedTodSlot)
        assertTrue(result.next.tod.isEmpty())
        assertEquals(before, SnoozeSuggestions.revert(result.next, result))
    }

    @Test
    fun `quick times learn chosen clock times, not relative or same-day picks`() {
        val at = epoch(LocalDateTime.of(2026, 9, 21, 10, 0))
        val tomorrow = epoch(LocalDateTime.of(2026, 9, 22, 9, 0))
        val laterToday = epoch(LocalDateTime.of(2026, 9, 21, 15, 0))
        assertTrue(SnoozeSuggestions.feedsQuickTimes(at, tomorrow, zone, "custom", null))
        assertTrue(SnoozeSuggestions.feedsQuickTimes(at, tomorrow, zone, "last", null))
        assertTrue(SnoozeSuggestions.feedsQuickTimes(at, tomorrow, zone, "suggestion", "D1@0900"))
        assertFalse(SnoozeSuggestions.feedsQuickTimes(at, tomorrow, zone, "suggestion", "D1_h2"))
        assertFalse(SnoozeSuggestions.feedsQuickTimes(at, laterToday, zone, "custom", null))
        val lateNight = epoch(LocalDateTime.of(2026, 9, 21, 23, 0))
        val afterMidnight = epoch(LocalDateTime.of(2026, 9, 22, 1, 30))
        assertFalse(SnoozeSuggestions.feedsQuickTimes(lateNight, afterMidnight, zone, "custom", null))
    }

    @Test
    fun `a daily tomorrow habit shows one row on Sunday, not a separate Monday row`() {
        var stats = SnoozeStatsSnapshot()
        var day = LocalDateTime.of(2024, 1, 1, 10, 0)
        repeat(20) {
            stats = commitOnce(stats, day, day.plusDays(1).withHour(8).withMinute(0)).next
            day = day.plusDays(1)
        }
        assertEquals(DayOfWeek.SUNDAY, day.dayOfWeek)
        val rows = SnoozeSuggestions.rank(listOf(stats), instant(day), zone)
        assertEquals(1, rows.size)
        assertEquals(DayOfWeek.MONDAY, atZone(rows[0].epochMillis).dayOfWeek)
    }

    @Test
    fun `a daily habit fades below the floor well after the user stops`() {
        var stats = SnoozeStatsSnapshot()
        var day = LocalDateTime.of(2024, 1, 1, 10, 0)
        repeat(20) {
            stats = commitOnce(stats, day, day.plusDays(1).withHour(8).withMinute(0)).next
            day = day.plusDays(1)
        }
        assertTrue(SnoozeSuggestions.rank(listOf(stats), instant(day.plusDays(400)), zone).isEmpty())
    }

    @Test
    fun `a Friday-to-Monday habit surfaces as This Monday, not a scattered offset row`() {
        var stats = SnoozeStatsSnapshot()
        var friday = LocalDateTime.of(2024, 1, 5, 6, 0)
        assertEquals(DayOfWeek.FRIDAY, friday.dayOfWeek)
        repeat(8) {
            stats = commitOnce(stats, friday, friday.plusDays(3).withHour(8).withMinute(0)).next
            friday = friday.plusWeeks(1)
        }
        val wednesday = friday.plusDays(5).withHour(10).withMinute(0)
        val rows = SnoozeSuggestions.rank(listOf(stats), instant(wednesday), zone)
        assertEquals(1, rows.size)
        assertEquals("Wd1@0800", rows[0].key)
        assertEquals("This Monday morning, 08:00", rows[0].label)
    }

    @Test
    fun `a clock reading and its aligning offset reading merge into one row`() {
        var stats = SnoozeStatsSnapshot()
        var friday = LocalDateTime.of(2024, 1, 5, 5, 0)
        repeat(6) {
            stats = commitOnce(stats, friday, friday.plusDays(3).withHour(8).withMinute(0)).next
            friday = friday.plusWeeks(1)
        }
        val rows = SnoozeSuggestions.rank(listOf(stats), instant(friday), zone)
        assertEquals(1, rows.size)
        assertEquals(epoch(friday.plusDays(3).withHour(8).withMinute(0)), rows[0].epochMillis)
    }

    @Test
    fun `a lone custom pick to the 1st resolves right but isn't guaranteed the 'The 1st' label`() {
        val stats = commitOnce(
            SnoozeStatsSnapshot(),
            LocalDateTime.of(2024, 4, 20, 9, 0),
            LocalDateTime.of(2024, 5, 1, 9, 0),
        ).next
        val rows = SnoozeSuggestions.rank(listOf(stats), instant(LocalDateTime.of(2024, 4, 21, 10, 0)), zone)
        assertTrue(
            "expected a row resolving to May 1st regardless of label, got ${rows.map { it.label }}",
            rows.any { val dt = atZone(it.epochMillis); dt.monthValue == 5 && dt.dayOfMonth == 1 },
        )
    }

    @Test
    fun `'the 1st' only wins outright once picked more than once, not from a single ambiguous pick`() {
        var stats = SnoozeStatsSnapshot()
        stats = commitOnce(stats, LocalDateTime.of(2027, 1, 21, 10, 0), LocalDateTime.of(2027, 2, 1, 9, 0)).next
        stats = commitOnce(stats, LocalDateTime.of(2027, 3, 20, 10, 0), LocalDateTime.of(2027, 4, 1, 9, 0)).next
        val rows = SnoozeSuggestions.rank(listOf(stats), instant(LocalDateTime.of(2027, 3, 25, 10, 0)), zone)
        assertTrue(
            "expected 'The 1st' to win on repeated evidence, got ${rows.map { it.label }}",
            rows.any { it.label.contains("1st") },
        )
    }

    @Test
    fun `an offset habit at varying times out-aggregates its scattered clock readings`() {
        var stats = SnoozeStatsSnapshot()
        val hours = listOf(8, 13, 18)
        var date = LocalDate.of(2024, 6, 1)
        repeat(9) { i ->
            val at = LocalDateTime.of(date, LocalTime.of(hours[i % hours.size], 0))
            stats = commitOnce(stats, at, at.plusHours(3)).next
            date = date.plusDays(1)
        }
        val rows = SnoozeSuggestions.rank(listOf(stats), instant(LocalDateTime.of(date, LocalTime.of(6, 0))), zone)
        assertEquals(1, rows.size)
        assertEquals("D0_h3", rows[0].key)
    }

    @Test
    fun `the most-used exact slot wins and a 5-minute neighbour is absorbed`() {
        var stats = SnoozeStatsSnapshot()
        var day = LocalDateTime.of(2024, 1, 1, 10, 0)
        for (m in listOf(0, 0, 0, 5)) {
            stats = commitOnce(stats, day, day.plusDays(1).withHour(8).withMinute(m)).next
            day = day.plusDays(1)
        }
        val rows = SnoozeSuggestions.rank(listOf(stats), instant(day), zone)
        val tomorrow = rows.filter { SnoozeSuggestions.labelDist(it.epochMillis, instant(day), zone) == 1 }
        assertEquals(listOf("D1@0800"), tomorrow.map { it.key })
    }

    @Test
    fun `two times for one date rule surface as two rows`() {
        var stats = SnoozeStatsSnapshot()
        var day = LocalDateTime.of(2026, 9, 1, 10, 0)
        repeat(10) { i ->
            val target = day.plusDays(1).with(if (i % 2 == 0) LocalTime.of(9, 0) else LocalTime.of(20, 0))
            stats = commitOnce(stats, day, target).next
            day = day.plusDays(1)
        }
        val rows = SnoozeSuggestions.rank(listOf(stats), instant(day), zone)
        val keys = rows.map { it.key }
        assertTrue("got $keys", "D1@0900" in keys && "D1@2000" in keys)
    }

    @Test
    fun `on a tie tomorrow beats the weekday reading, and the weekday shows once tomorrow is a different day`() {
        val stats = commitOnce(
            SnoozeStatsSnapshot(),
            LocalDateTime.of(2026, 9, 16, 15, 0),
            LocalDateTime.of(2026, 9, 17, 9, 0),
        ).next
        val wednesday = SnoozeSuggestions.rank(listOf(stats), instant(LocalDateTime.of(2026, 9, 23, 10, 0)), zone)
        assertEquals(listOf("D1@0900"), wednesday.map { it.key })
        assertEquals("Tomorrow morning, 09:00", wednesday[0].label)

        val thursday = SnoozeSuggestions.rank(listOf(stats), instant(LocalDateTime.of(2026, 9, 24, 7, 0)), zone)
        assertEquals(listOf("D1@0900"), thursday.map { it.key })
        assertEquals(epoch(LocalDateTime.of(2026, 9, 25, 9, 0)), thursday[0].epochMillis)
    }

    @Test
    fun `this and next weekday labels`() {
        assertEquals("This Thursday morning, 09:00", label("Wd4@0900", LocalDateTime.of(2026, 9, 24, 9, 0), LocalDateTime.of(2026, 9, 23, 10, 0)))
        assertEquals("Next Thursday morning, 09:00", label("Wd4@0900", LocalDateTime.of(2026, 10, 1, 9, 0), LocalDateTime.of(2026, 9, 24, 7, 0)))
    }

    @Test
    fun `weekday-after-next labels`() {
        val monday = LocalDateTime.of(2026, 9, 21, 10, 0)
        assertEquals("Next Wednesday morning, 09:00", label("Wn3@0900", LocalDateTime.of(2026, 9, 30, 9, 0), monday))
        assertEquals("Monday in 2 weeks, 09:00", label("Wn1@0900", LocalDateTime.of(2026, 10, 5, 9, 0), monday))
    }

    @Test
    fun `same-day labels use the region name or a relative phrase`() {
        val noon = LocalDateTime.of(2026, 9, 21, 12, 0)
        val tenPm = LocalDateTime.of(2026, 9, 21, 22, 0)
        assertEquals("This evening, 20:00", label("D0@2000", LocalDateTime.of(2026, 9, 21, 20, 0), noon))
        assertEquals("Tonight, 01:00", label("D0@0100", LocalDateTime.of(2026, 9, 22, 1, 0), tenPm))
        assertEquals("In a little while (15:00)", label("D0_h3", LocalDateTime.of(2026, 9, 21, 15, 0), noon))
        assertEquals("Much later (16:00)", label("D0_h4", LocalDateTime.of(2026, 9, 21, 16, 0), noon))
        assertEquals("In a little while (00:00)", label("D0_h2", LocalDateTime.of(2026, 9, 22, 0, 0), tenPm))
        assertEquals("Tomorrow evening, 20:30", label("D1@2030", LocalDateTime.of(2026, 9, 22, 20, 30), noon))
    }

    @Test
    fun `labels follow the 5am rule between midnight and 5am`() {
        val early = LocalDateTime.of(2026, 9, 22, 4, 50)
        assertEquals("This morning, 09:00", label("D1@0900", LocalDateTime.of(2026, 9, 22, 9, 0), early))
    }

    @Test
    fun `separate short offset habits both show, in time order`() {
        var stats = SnoozeStatsSnapshot()
        val day = LocalDate.of(2026, 9, 1)
        for (at in listOf(LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(16, 0))) {
            val dt = LocalDateTime.of(day, at)
            stats = commitOnce(stats, dt, dt.plusHours(1)).next
        }
        for (at in listOf(LocalTime.of(9, 10), LocalTime.of(13, 10), LocalTime.of(17, 10))) {
            val dt = LocalDateTime.of(day, at)
            stats = commitOnce(stats, dt, dt.plusHours(3)).next
        }
        val rows = SnoozeSuggestions.rank(listOf(stats), instant(LocalDateTime.of(2026, 9, 2, 10, 0)), zone)
        assertEquals(listOf("D0_h1", "D0_h3"), rows.map { it.key })
        assertEquals(listOf("In a little while (11:00)", "In a little while (13:00)"), rows.map { it.label })
    }

    @Test
    fun `an offset and a clock time from the same commits are one row`() {
        var stats = SnoozeStatsSnapshot()
        var day = LocalDateTime.of(2026, 9, 1, 12, 0)
        repeat(4) {
            stats = commitOnce(stats, day, day.withHour(15)).next
            day = day.plusDays(1)
        }
        assertEquals(1, SnoozeSuggestions.rank(listOf(stats), instant(day), zone).size)
    }

    @Test
    fun `row icons follow the resolved day and time`() {
        val noon = LocalDateTime.of(2026, 9, 21, 12, 0)
        fun icon(key: String, resolved: LocalDateTime) =
            SnoozeSuggestions.rowIcon(key, epoch(resolved), instant(noon), zone)
        assertEquals(RowIcon.TODAY_NIGHT, icon("D0@2300", LocalDateTime.of(2026, 9, 21, 23, 0)))
        assertEquals(RowIcon.TODAY_DAY, icon("D0@1500", LocalDateTime.of(2026, 9, 21, 15, 0)))
        assertEquals(RowIcon.TOMORROW, icon("D1@0900", LocalDateTime.of(2026, 9, 22, 9, 0)))
        assertEquals(RowIcon.DAYS, icon("D3@0900", LocalDateTime.of(2026, 9, 24, 9, 0)))
        assertEquals(RowIcon.WEEKDAY, icon("Wd5@0900", LocalDateTime.of(2026, 9, 25, 9, 0)))
        assertEquals(RowIcon.MONTHLY, icon("Dom1@0900", LocalDateTime.of(2026, 10, 1, 9, 0)))
        assertEquals(RowIcon.OFFSET, icon("D0_h3", LocalDateTime.of(2026, 9, 21, 15, 0)))
    }

    @Test
    fun `last text is the date and its clock time is 24-hour`() {
        val target = epoch(LocalDateTime.of(2026, 9, 24, 21, 5))
        assertEquals("Last · Sep 24", SnoozeSuggestions.lastText(target, zone))
        assertEquals("21:05", SnoozeSuggestions.formatClock(target, zone))
    }

    @Test
    fun `the Last row hides when past, near a ranked row, or superseded`() {
        val now = instant(LocalDateTime.of(2026, 9, 21, 12, 0))
        val future = epoch(LocalDateTime.of(2026, 9, 25, 9, 0))
        val older = SnoozeStatsSnapshot(lastCustom = LastCustom(epoch(LocalDateTime.of(2026, 9, 26, 9, 0)), 1L))
        val newer = SnoozeStatsSnapshot(lastCustom = LastCustom(future, 2L))
        assertEquals(future, SnoozeSuggestions.lastRow(listOf(older, newer), now, emptyList()))
        val near = SnoozeRow(future + 20 * 60 * 1000L, "x", 1.0, "D4@0920")
        assertNull(SnoozeSuggestions.lastRow(listOf(newer), now, listOf(near)))
        val past = SnoozeStatsSnapshot(lastCustom = LastCustom(epoch(LocalDateTime.of(2026, 9, 20, 9, 0)), 3L))
        assertNull(SnoozeSuggestions.lastRow(listOf(past), now, emptyList()))
    }

    @Test
    fun `quick times pick up to four spaced-out slots, shown in clock order`() {
        val now = instant(LocalDateTime.of(2026, 9, 21, 12, 0))
        val t = now.toEpochMilli()
        val tod = mapOf(
            "0900" to SetStat(3.0, t),
            "0905" to SetStat(3.0, t),
            "1400" to SetStat(2.0, t),
            "2000" to SetStat(1.0, t),
            "2300" to SetStat(1.0, t),
            "0030" to SetStat(0.5, t),
        )
        val picked = SnoozeSuggestions.quickTimes(listOf(SnoozeStatsSnapshot(tod = tod)), now)
        assertEquals(listOf(9 * 60, 14 * 60, 20 * 60, 23 * 60), picked.map { it.clockMinutes })
    }

    @Test
    fun `quick times treat slots across midnight as neighbours`() {
        val now = instant(LocalDateTime.of(2026, 9, 21, 12, 0))
        val t = now.toEpochMilli()
        val tod = mapOf("2350" to SetStat(2.0, t), "0010" to SetStat(1.0, t))
        val picked = SnoozeSuggestions.quickTimes(listOf(SnoozeStatsSnapshot(tod = tod)), now)
        assertEquals(listOf(23 * 60 + 50), picked.map { it.clockMinutes })
    }

    @Test
    fun `quick times sum across partitions`() {
        val now = instant(LocalDateTime.of(2026, 9, 21, 12, 0))
        val t = now.toEpochMilli()
        val a = SnoozeStatsSnapshot(tod = mapOf("0900" to SetStat(2.0, t), "1000" to SetStat(1.5, t)))
        val b = SnoozeStatsSnapshot(tod = mapOf("1000" to SetStat(1.5, t)))
        val combined = SnoozeSuggestions.quickTimes(listOf(a, b), now).maxBy { it.weight }
        assertEquals(10 * 60, combined.clockMinutes)
    }

    @Test
    fun `a single quick time lasts about 90 days`() {
        val committed = LocalDateTime.of(2026, 1, 1, 12, 0)
        val stats = SnoozeStatsSnapshot(tod = mapOf("0900" to SetStat(1.0, epoch(committed))))
        assertEquals(1, SnoozeSuggestions.quickTimes(listOf(stats), instant(committed.plusDays(90))).size)
        assertEquals(0, SnoozeSuggestions.quickTimes(listOf(stats), instant(committed.plusDays(91))).size)
    }

    @Test
    fun `a quick time means that calendar date at that clock time`() {
        assertEquals(
            epoch(LocalDateTime.of(2026, 9, 24, 1, 0)),
            SnoozeSuggestions.quickTimeEpoch(LocalDate.of(2026, 9, 24), 60, zone),
        )
    }

    @Test
    fun `two devices' partitions sum on read`() {
        val at = LocalDateTime.of(2024, 1, 1, 10, 0)
        val target = LocalDateTime.of(2024, 1, 2, 8, 0)
        val deviceA = commitOnce(SnoozeStatsSnapshot(), at, target).next
        val deviceB = commitOnce(SnoozeStatsSnapshot(), at, target).next
        val now = instant(at.plusHours(1))
        val combinedRows = SnoozeSuggestions.rank(listOf(deviceA, deviceB), now, zone)
        val soloRows = SnoozeSuggestions.rank(listOf(deviceA), now, zone)
        assertEquals(soloRows.map { it.key }, combinedRows.map { it.key })
        assertTrue(combinedRows.single().score > soloRows.single().score)
    }

    @Test
    fun `custom time defaults to the next quarter hour strictly after now`() {
        assertEquals(LocalTime.of(9, 15), SnoozeSuggestions.nextQuarterHour(LocalTime.of(9, 0)))
        assertEquals(LocalTime.of(9, 30), SnoozeSuggestions.nextQuarterHour(LocalTime.of(9, 17, 45)))
        assertEquals(LocalTime.of(0, 0), SnoozeSuggestions.nextQuarterHour(LocalTime.of(23, 50)))
    }

    @Test
    fun `custom date moves to tomorrow once the next quarter hour wraps midnight`() {
        assertEquals(LocalDate.of(2026, 9, 21), SnoozeSuggestions.defaultCustomDate(LocalDateTime.of(2026, 9, 21, 23, 44)))
        assertEquals(LocalDate.of(2026, 9, 22), SnoozeSuggestions.defaultCustomDate(LocalDateTime.of(2026, 9, 21, 23, 45)))
    }

    @Test
    fun `set ids with any old-format member are invalid`() {
        assertTrue(SnoozeSuggestions.isValidSetId("D1@0900__Wd4@0900"))
        assertFalse(SnoozeSuggestions.isValidSetId("D1@0900__D1_morning"))
    }

    @Test
    fun `label text is the label without its clock time`() {
        val noon = LocalDateTime.of(2026, 9, 21, 12, 0)
        val cases = listOf(
            "D0_h3" to LocalDateTime.of(2026, 9, 21, 15, 0),
            "D0@2000" to LocalDateTime.of(2026, 9, 21, 20, 0),
            "D1@0900" to LocalDateTime.of(2026, 9, 22, 9, 0),
            "D1_h0" to LocalDateTime.of(2026, 9, 22, 12, 0),
            "Wd4@0900" to LocalDateTime.of(2026, 9, 24, 9, 0),
        )
        for ((key, resolved) in cases) {
            val text = SnoozeSuggestions.labelText(key, epoch(resolved), instant(noon), zone)
            val time = SnoozeSuggestions.formatClock(epoch(resolved), zone)
            val full = label(key, resolved, noon)
            assertTrue("$key: $full vs $text", full == "$text, $time" || full == "$text ($time)")
        }
        assertEquals("In a little while", SnoozeSuggestions.labelText("D0_h3", epoch(cases[0].second), instant(noon), zone))
        assertEquals("Tomorrow morning", SnoozeSuggestions.labelText("D1@0900", epoch(cases[2].second), instant(noon), zone))
    }
}
