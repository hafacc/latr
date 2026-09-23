package io.hafa.latr.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.pow

typealias PatternKey = String

/** A decayed count as of [t]: one observed pattern set, or one quick-time clock slot. */
data class SetStat(val c: Double, val t: Long)

data class LastCustom(val target: Long, val at: Long)

/** One device's snooze-learning state. [tod] is keyed by local clock slot ("HHMM"). */
data class SnoozeStatsSnapshot(
    val sets: Map<String, SetStat> = emptyMap(),
    val tod: Map<String, SetStat> = emptyMap(),
    val lastCustom: LastCustom? = null,
)

/** Everything a commit changed, so undo can restore it exactly. */
data class CommitResult(
    val next: SnoozeStatsSnapshot,
    val touchedSetId: String?,
    val undoSetSnapshot: SetStat?,
    val touchedTodSlot: String?,
    val undoTodSnapshot: SetStat?,
    val undoLastCustom: LastCustom?,
)

data class SnoozeRow(val epochMillis: Long, val label: String, val score: Double, val key: PatternKey)

/** A custom-picker quick button: a learned clock time, [clockMinutes] after local midnight. */
data class QuickTime(val clockMinutes: Int, val weight: Double)

enum class RowIcon { OFFSET, TODAY_DAY, TODAY_NIGHT, TOMORROW, DAYS, WEEKDAY, MONTHLY }

/** Learned snooze suggestions; mirrors web's `utils/snooze-suggest.ts` 1:1. */
object SnoozeSuggestions {

    private const val MS_PER_DAY = 24.0 * 60 * 60 * 1000
    private const val SHORT_HALF_LIFE_DAYS = 21L
    private const val LONG_HALF_LIFE_DAYS = 60L
    private const val TOD_HALF_LIFE_DAYS = 21L
    const val SLOT_MINUTES = 5
    private const val DAY_BOUNDARY_HOUR = 5L
    const val DEFAULT_FLOOR = 0.05
    private const val WINDOW_MS = 30 * 60 * 1000L
    private const val WINDOW_MINUTES = 30
    private const val QUICK_TIMES_MAX = 4
    private const val SCORE_EPS = 1e-9
    private const val LITTLE_WHILE_MAX_HOURS = 3

    val KEY_RE = Regex("^(D[0-6]|W[1-4]|Wd[1-7]|Wn[1-7]|Dom1|Dom15|DomL|Mo[1-3])(@([01]\\d|2[0-3])[0-5][05]|_h([0-9]|1[0-2]))$")
    val SLOT_RE = Regex("^([01]\\d|2[0-3])[0-5][05]$")

    /** The first :00/:15/:30/:45 strictly after [time]; wraps past midnight. */
    fun nextQuarterHour(time: LocalTime): LocalTime {
        val minutes = time.hour * 60 + time.minute
        val next = (minutes / 15 + 1) * 15
        return LocalTime.of((next / 60) % 24, next % 60)
    }

    /** Today, unless the next quarter hour wraps past midnight, so the picker opens on a time that's still ahead. */
    fun defaultCustomDate(now: LocalDateTime): LocalDate =
        if (nextQuarterHour(now.toLocalTime()) <= now.toLocalTime()) now.toLocalDate().plusDays(1) else now.toLocalDate()

    fun isValidSetId(id: String): Boolean = id.split("__").all { KEY_RE.matches(it) }

    private val D_RE = Regex("^D([0-6])$")
    private val W_RE = Regex("^W([1-4])$")
    private val WD_RE = Regex("^Wd([1-7])$")
    private val WN_RE = Regex("^Wn([1-7])$")
    private val MO_RE = Regex("^Mo([1-3])$")
    private val LONG_PERIOD_RULES = setOf("Dom1", "Dom15", "DomL", "Mo1", "Mo2", "Mo3")

    /** The set of pattern keys a commit from [at] to [target] matches. Empty if no date rule reaches it. */
    fun extract(at: Instant, target: Instant, zone: ZoneId): Set<PatternKey> {
        val a = LocalDateTime.ofInstant(at, zone)
        val t = LocalDateTime.ofInstant(target, zone)
        return extractTimedKeys(a, t) + extractOffsetKeys(at, target, zone)
    }

    private fun extractTimedKeys(a: LocalDateTime, t: LocalDateTime): Set<PatternKey> {
        val slot = slotOf(t)
        return dateRulesFor(snoozeDay(a), snoozeDay(t)).map { "$it@$slot" }.toSet()
    }

    /** The target's local clock time rounded to 5 minutes, as "HHMM"; never rounds across the 05:00 boundary. */
    fun slotOf(dt: LocalDateTime): String {
        val rounded = Math.floorDiv(minutesSince0500(dt) + SLOT_MINUTES / 2, SLOT_MINUTES) * SLOT_MINUTES
        val slot = minOf(rounded, 24 * 60 - SLOT_MINUTES)
        val clock = (slot + DAY_BOUNDARY_HOUR.toInt() * 60) % (24 * 60)
        return "%02d%02d".format(clock / 60, clock % 60)
    }

    private fun slotClockMinutes(slot: String): Int = slot.take(2).toInt() * 60 + slot.drop(2).toInt()

    // Days on the wall clock, hours as elapsed time: the exact inverse of resolveKey's offset branch.
    private fun extractOffsetKeys(at: Instant, target: Instant, zone: ZoneId): Set<PatternKey> {
        val aZoned = at.atZone(zone)
        val aLocal = aZoned.toLocalDateTime()
        val tLocal = LocalDateTime.ofInstant(target, zone)
        val wallMin = Math.round(ChronoUnit.MILLIS.between(aLocal, tLocal) / 60000.0)
        val dEff = Math.round(wallMin / 1440.0)
        val base = aZoned.plusDays(dEff)
        val deltaMin = Math.round((target.toEpochMilli() - base.toInstant().toEpochMilli()) / 60000.0)
        val h = Math.round(deltaMin / 60.0)
        if (h < 0 || h > 12) return emptySet()
        val rules = dateRulesFor(snoozeDay(aLocal), snoozeDay(base.toLocalDateTime()))
            .filter { isShortFamily(it) }
        return rules.mapNotNull { r -> if (r == "D0" && h == 0L) null else "${r}_h$h" }.toSet()
    }

    private fun isShortFamily(rule: String): Boolean =
        D_RE.matches(rule) || W_RE.matches(rule) || WD_RE.matches(rule) || WN_RE.matches(rule)

    /** Every date-rule id [toDay] satisfies relative to [fromDay] (0-4 of them). */
    private fun dateRulesFor(fromDay: LocalDate, toDay: LocalDate): List<String> {
        val d = ChronoUnit.DAYS.between(fromDay, toDay)
        val rules = mutableListOf<String>()
        if (d in 0..6) rules += "D$d"
        if (d == 7L || d == 14L || d == 21L || d == 28L) rules += "W${d / 7}"
        if (d in 1..7) rules += "Wd${toDay.dayOfWeek.value}"
        if (d in 8..14) rules += "Wn${toDay.dayOfWeek.value}"
        if (d >= 1) {
            if (toDay == nextDayOfMonth(fromDay, 1)) rules += "Dom1"
            if (toDay == nextDayOfMonth(fromDay, 15)) rules += "Dom15"
            if (toDay == nextMonthEnd(fromDay)) rules += "DomL"
            for (k in 1..3) if (toDay == fromDay.plusMonths(k.toLong())) rules += "Mo$k"
        }
        return rules
    }

    private fun nextDayOfMonth(from: LocalDate, dayOfMonth: Int): LocalDate {
        val thisMonth = from.withDayOfMonth(dayOfMonth)
        return if (thisMonth > from) thisMonth else from.plusMonths(1).withDayOfMonth(dayOfMonth)
    }

    private fun nextMonthEnd(from: LocalDate): LocalDate {
        val thisMonthEnd = YearMonth.from(from).atEndOfMonth()
        return if (thisMonthEnd > from) thisMonthEnd else YearMonth.from(from.plusMonths(1)).atEndOfMonth()
    }

    /** The calendar day this instant belongs to for pattern purposes: a 05:00 boundary, so 1am counts as "last night". */
    private fun snoozeDay(dt: LocalDateTime): LocalDate = dt.minusHours(DAY_BOUNDARY_HOUR).toLocalDate()

    private fun minutesSince0500(dt: LocalDateTime): Int {
        val minutesOfDay = dt.hour * 60 + dt.minute
        return (minutesOfDay - DAY_BOUNDARY_HOUR.toInt() * 60 + 24 * 60) % (24 * 60)
    }

    private fun bucketOf(minutesSince0500: Int): String = when {
        minutesSince0500 < 420 -> "morning"
        minutesSince0500 < 720 -> "afternoon"
        minutesSince0500 < 960 -> "evening"
        else -> "night"
    }

    fun isTimedKey(key: PatternKey): Boolean = '@' in key

    fun dateRuleOf(key: PatternKey): String = key.substringBefore('@').substringBefore('_')

    /** The date-rule family: D, W, Wd, Wn, Dom, DomL or Mo. */
    internal fun familyOfRule(dateRule: String): String = when {
        dateRule == "DomL" -> "DomL"
        dateRule.startsWith("Dom") -> "Dom"
        dateRule.startsWith("Wd") -> "Wd"
        dateRule.startsWith("Wn") -> "Wn"
        dateRule.startsWith("Mo") -> "Mo"
        dateRule.startsWith("W") -> "W"
        else -> "D"
    }

    private fun classH(dateRule: String): Long = if (dateRule in LONG_PERIOD_RULES) LONG_HALF_LIFE_DAYS else SHORT_HALF_LIFE_DAYS

    private fun classHForKey(key: PatternKey): Long = classH(dateRuleOf(key))

    /** A set's half-life is the longest among its members'. */
    private fun setH(members: Set<PatternKey>): Long = members.maxOf { classHForKey(it) }

    private fun setH(id: String): Long = setH(splitSetId(id))

    fun setId(keys: Set<PatternKey>): String = keys.sorted().joinToString("__")

    private fun splitSetId(id: String): Set<PatternKey> = id.split("__").toSet()

    private fun decayFactor(ageMs: Long, halfLifeDays: Long): Double =
        2.0.pow(-(ageMs / MS_PER_DAY) / halfLifeDays)

    private fun bump(prev: SetStat?, at: Long, halfLifeDays: Long): SetStat {
        val decayed = prev?.let { it.c * decayFactor(at - it.t, halfLifeDays) } ?: 0.0
        return SetStat(decayed + 1.0, at)
    }

    /** Quick times learn only chosen clock times: not an "in N hours" row, and not a later-today snooze. */
    fun feedsQuickTimes(at: Long, target: Long, zone: ZoneId, source: String, pickedKey: PatternKey?): Boolean {
        val sameDay = snoozeDay(LocalDateTime.ofInstant(Instant.ofEpochMilli(at), zone)) ==
            snoozeDay(LocalDateTime.ofInstant(Instant.ofEpochMilli(target), zone))
        val relative = source == "suggestion" && pickedKey != null && !isTimedKey(pickedKey)
        return !sameDay && !relative
    }

    /** Pure: returns the next state plus exactly what to undo. [pickedKey] is the suggestion row's key, else null. */
    fun commit(
        stats: SnoozeStatsSnapshot,
        at: Long,
        target: Long,
        zone: ZoneId,
        source: String,
        pickedKey: PatternKey? = null,
    ): CommitResult {
        val keys = extract(Instant.ofEpochMilli(at), Instant.ofEpochMilli(target), zone)
        val nextLastCustom = if (source == "custom") LastCustom(target, at) else stats.lastCustom

        var sets = stats.sets
        var id: String? = null
        var prevSet: SetStat? = null
        if (keys.isNotEmpty()) {
            id = setId(keys)
            prevSet = stats.sets[id]
            sets = sets + (id to bump(prevSet, at, setH(keys)))
        }

        var tod = stats.tod
        var slot: String? = null
        var prevTod: SetStat? = null
        if (feedsQuickTimes(at, target, zone, source, pickedKey)) {
            slot = slotOf(LocalDateTime.ofInstant(Instant.ofEpochMilli(target), zone))
            prevTod = stats.tod[slot]
            tod = tod + (slot to bump(prevTod, at, TOD_HALF_LIFE_DAYS))
        }
        return CommitResult(
            next = SnoozeStatsSnapshot(sets, tod, nextLastCustom),
            touchedSetId = id,
            undoSetSnapshot = prevSet,
            touchedTodSlot = slot,
            undoTodSnapshot = prevTod,
            undoLastCustom = stats.lastCustom,
        )
    }

    /** Reverts exactly what [result] changed. */
    fun revert(stats: SnoozeStatsSnapshot, result: CommitResult): SnoozeStatsSnapshot {
        var sets = stats.sets
        if (result.touchedSetId != null) {
            sets = if (result.undoSetSnapshot != null) {
                sets + (result.touchedSetId to result.undoSetSnapshot)
            } else {
                sets - result.touchedSetId
            }
        }
        var tod = stats.tod
        if (result.touchedTodSlot != null) {
            tod = if (result.undoTodSnapshot != null) {
                tod + (result.touchedTodSlot to result.undoTodSnapshot)
            } else {
                tod - result.touchedTodSlot
            }
        }
        return SnoozeStatsSnapshot(sets, tod, result.undoLastCustom)
    }

    /** Greedy: take the key with the most live support, spend every set backing it or a key within 30 minutes, repeat. */
    fun rank(
        partitions: List<SnoozeStatsSnapshot>,
        now: Instant,
        zone: ZoneId,
        n: Int = 5,
        floor: Double = DEFAULT_FLOOR,
    ): List<SnoozeRow> {
        val nowMillis = now.toEpochMilli()
        val liveById = mutableMapOf<String, Double>()
        val membersById = mutableMapOf<String, Set<PatternKey>>()
        for (partition in partitions) {
            for ((id, s) in partition.sets) {
                val live = s.c * decayFactor(nowMillis - s.t, setH(id))
                if (live <= 0.0) continue
                liveById[id] = (liveById[id] ?: 0.0) + live
                membersById.getOrPut(id) { splitSetId(id) }
            }
        }
        val available = liveById.keys.toMutableSet()

        val candidateKeys = available.flatMap { membersById.getValue(it) }.toSet()
        val resolved = mutableMapOf<PatternKey, Long>()
        for (key in candidateKeys) {
            resolveFuture(key, now, zone)?.let { resolved[key] = it }
        }

        val rows = mutableListOf<SnoozeRow>()
        val settled = mutableSetOf<PatternKey>()
        while (rows.size < n) {
            var best: PatternKey? = null
            var bestScore = 0.0
            for (key in resolved.keys) {
                if (key in settled) continue
                val agg = available.filter { key in membersById.getValue(it) }.sumOf { liveById.getValue(it) }
                if (agg <= 0.0) continue
                val better = if (sameScore(agg, bestScore)) best == null || tieBreak(key, best) < 0 else agg > bestScore
                if (better) {
                    best = key
                    bestScore = agg
                }
            }
            if (best == null || bestScore <= floor) break
            val pickedKey = best
            val pickedTime = resolved.getValue(pickedKey)
            val absorbed = resolved.keys.filter {
                it != pickedKey && it !in settled && Math.abs(resolved.getValue(it) - pickedTime) <= WINDOW_MS
            }
            rows += SnoozeRow(pickedTime, labelFor(pickedKey, pickedTime, now, zone), bestScore, pickedKey)
            settled += pickedKey
            settled += absorbed
            val spent = settled.toSet()
            val toRemove = available.filter { id -> membersById.getValue(id).any { it in spent } }
            available -= toRemove.toSet()
        }
        return rows.sortedBy { it.epochMillis }
    }

    /** The most recently updated custom pick across every partition, or null. */
    fun latestLastCustom(partitions: List<SnoozeStatsSnapshot>): LastCustom? =
        partitions.mapNotNull { it.lastCustom }.maxByOrNull { it.at }

    /** The "Last" row's target: the newest custom pick, if it's still ahead and no ranked row already covers it. */
    fun lastRow(partitions: List<SnoozeStatsSnapshot>, now: Instant, rows: List<SnoozeRow>): Long? {
        val last = latestLastCustom(partitions) ?: return null
        if (last.target <= now.toEpochMilli()) return null
        if (rows.any { Math.abs(it.epochMillis - last.target) <= WINDOW_MS }) return null
        return last.target
    }

    // On a tie, lower wins: a named weekday/day of month beats a count of days/weeks/months.
    private fun tieRank(dateRule: String): Int = when {
        dateRule == "D0" || dateRule == "D1" -> 0
        WD_RE.matches(dateRule) -> 1
        WN_RE.matches(dateRule) -> 2
        dateRule == "Dom1" || dateRule == "Dom15" || dateRule == "DomL" -> 3
        D_RE.matches(dateRule) -> 4
        W_RE.matches(dateRule) -> 5
        else -> 6
    }

    // Both platforms sum doubles in different orders, so an exact == could pick different winners.
    private fun sameScore(a: Double, b: Double): Boolean =
        Math.abs(a - b) <= SCORE_EPS * maxOf(1.0, Math.abs(a), Math.abs(b))

    /** Negative if [a] should be preferred over [b] when their aggregate scores tie. */
    private fun tieBreak(a: PatternKey, b: PatternKey): Int {
        val ra = tieRank(dateRuleOf(a))
        val rb = tieRank(dateRuleOf(b))
        if (ra != rb) return ra - rb
        val oa = if (isTimedKey(a)) 0 else 1
        val ob = if (isTimedKey(b)) 0 else 1
        if (oa != ob) return oa - ob
        return a.compareTo(b) // code-unit order; must match web's `a < b`, not localeCompare
    }

    /** Where [key] points from [now], or null if that isn't at least a minute ahead. */
    internal fun resolveFuture(key: PatternKey, now: Instant, zone: ZoneId): Long? =
        resolveKey(key, now, zone)?.takeIf { it > now.toEpochMilli() + 60_000 }

    private fun resolveKey(key: PatternKey, now: Instant, zone: ZoneId): Long? {
        if (!KEY_RE.matches(key)) return null
        val dateRule = dateRuleOf(key)
        val nowLocal = LocalDateTime.ofInstant(now, zone)
        return if (!isTimedKey(key)) {
            val h = key.substringAfter("_h").toInt()
            val sd = snoozeDay(nowLocal)
            val date = resolveDateRule(dateRule, sd) ?: return null
            val numDays = ChronoUnit.DAYS.between(sd, date)
            // Wall-clock days, then elapsed hours (ZonedDateTime.plusHours), matching extraction.
            val zoned = now.atZone(zone).plusDays(numDays).plusHours(h.toLong())
            snapTo5Min(zoned.toInstant().toEpochMilli())
        } else {
            val date = resolveDateRule(dateRule, snoozeDay(nowLocal)) ?: return null
            val sinceBoundary = (slotClockMinutes(key.substringAfter('@')) - DAY_BOUNDARY_HOUR.toInt() * 60 + 24 * 60) % (24 * 60)
            LocalDateTime.of(date, LocalTime.of(DAY_BOUNDARY_HOUR.toInt(), 0)).plusMinutes(sinceBoundary.toLong())
                .atZone(zone).toInstant().toEpochMilli()
        }
    }

    private fun resolveDateRule(dateRule: String, from: LocalDate): LocalDate? {
        D_RE.matchEntire(dateRule)?.let { return from.plusDays(it.groupValues[1].toLong()) }
        W_RE.matchEntire(dateRule)?.let { return from.plusWeeks(it.groupValues[1].toLong()) }
        WD_RE.matchEntire(dateRule)?.let { return nextWeekday(from, DayOfWeek.of(it.groupValues[1].toInt()), 1, 7) }
        WN_RE.matchEntire(dateRule)?.let { return nextWeekday(from, DayOfWeek.of(it.groupValues[1].toInt()), 8, 14) }
        if (dateRule == "Dom1") return nextDayOfMonth(from, 1)
        if (dateRule == "Dom15") return nextDayOfMonth(from, 15)
        if (dateRule == "DomL") return nextMonthEnd(from)
        MO_RE.matchEntire(dateRule)?.let { return from.plusMonths(it.groupValues[1].toLong()) }
        return null
    }

    private fun snapTo5Min(epochMillis: Long): Long {
        val fiveMinMs = 5 * 60 * 1000L
        return Math.round(epochMillis / fiveMinMs.toDouble()) * fiveMinMs
    }

    private fun nextWeekday(from: LocalDate, dow: DayOfWeek, minDays: Int, maxDays: Int): LocalDate {
        for (delta in minDays..maxDays) {
            val candidate = from.plusDays(delta.toLong())
            if (candidate.dayOfWeek == dow) return candidate
        }
        error("no matching weekday for $dow in [$minDays,$maxDays]")
    }

    /** Strongest slots at least 30 minutes apart on the clock face (earliest from 05:00 on a tie), in clock order. */
    fun quickTimes(partitions: List<SnoozeStatsSnapshot>, now: Instant, floor: Double = DEFAULT_FLOOR): List<QuickTime> {
        val nowMs = now.toEpochMilli()
        val weights = mutableMapOf<Int, Double>()
        for (partition in partitions) {
            for ((slot, s) in partition.tod) {
                if (!SLOT_RE.matches(slot)) continue
                val clock = slotClockMinutes(slot)
                weights[clock] = (weights[clock] ?: 0.0) + s.c * decayFactor(nowMs - s.t, TOD_HALF_LIFE_DAYS)
            }
        }
        val boundary = DAY_BOUNDARY_HOUR.toInt() * 60
        fun sinceBoundary(clock: Int) = (clock - boundary + 24 * 60) % (24 * 60)
        val picked = mutableListOf<QuickTime>()
        while (picked.size < QUICK_TIMES_MAX && weights.isNotEmpty()) {
            var best: Int? = null
            var bestWeight = 0.0
            for ((clock, w) in weights) {
                val better = if (best == null) {
                    true
                } else if (sameScore(w, bestWeight)) {
                    sinceBoundary(clock) < sinceBoundary(best)
                } else {
                    w > bestWeight
                }
                if (better) {
                    best = clock
                    bestWeight = w
                }
            }
            if (best == null || bestWeight <= floor) break
            picked += QuickTime(best, bestWeight)
            val center = best
            weights.keys.removeAll { clock ->
                val d = Math.abs(clock - center)
                minOf(d, 24 * 60 - d) <= WINDOW_MINUTES
            }
        }
        return picked.sortedBy { it.clockMinutes }
    }

    /** A quick time on [date] means that calendar date at that clock time, exactly as typing it would. */
    fun quickTimeEpoch(date: LocalDate, clockMinutes: Int, zone: ZoneId): Long =
        LocalDateTime.of(date, LocalTime.of(clockMinutes / 60, clockMinutes % 60))
            .atZone(zone).toInstant().toEpochMilli()

    /** Days from today (calendar) to the resolved target's snooze day, never negative. */
    fun labelDist(resolvedEpoch: Long, now: Instant, zone: ZoneId): Int {
        val resolvedDay = snoozeDay(LocalDateTime.ofInstant(Instant.ofEpochMilli(resolvedEpoch), zone))
        val today = LocalDateTime.ofInstant(now, zone).toLocalDate()
        return maxOf(0L, ChronoUnit.DAYS.between(today, resolvedDay)).toInt()
    }

    /** The time-of-day name a label uses for [resolvedEpoch]. */
    internal fun regionOf(resolvedEpoch: Long, zone: ZoneId): String =
        bucketOf(minutesSince0500(LocalDateTime.ofInstant(Instant.ofEpochMilli(resolvedEpoch), zone)))

    /** ISO weekday (Mon=1..Sun=7) of the snooze day a label names. */
    internal fun labelWeekday(resolvedEpoch: Long, zone: ZoneId): Int =
        snoozeDay(LocalDateTime.ofInstant(Instant.ofEpochMilli(resolvedEpoch), zone)).dayOfWeek.value

    private data class DayPhrase(val kind: String, val text: String, val adjectival: Boolean)

    private fun dayPhrase(dateRule: String, dist: Int, weekday: DayOfWeek): DayPhrase {
        val today = DayPhrase("today", "", adjectival = false)
        if (D_RE.matches(dateRule) || W_RE.matches(dateRule)) {
            return when {
                dist == 0 -> today
                dist == 1 -> DayPhrase("tomorrow", "Tomorrow", adjectival = true)
                dist % 7 == 0 && dist <= 28 ->
                    DayPhrase("inWeeks", if (dist == 7) "In a week" else "In ${dist / 7} weeks", adjectival = false)
                else -> DayPhrase("inDays", "In $dist days", adjectival = false)
            }
        }
        if (WD_RE.matches(dateRule) || WN_RE.matches(dateRule)) {
            val name = dayName(weekday)
            return when {
                dist == 0 -> today
                dist <= 6 -> DayPhrase("this", "This $name", adjectival = true)
                dist <= 13 -> DayPhrase("next", "Next $name", adjectival = true)
                dist == 14 -> DayPhrase("inTwoWeeks", "$name in 2 weeks", adjectival = false)
                else -> DayPhrase("inDays", "In $dist days", adjectival = false)
            }
        }
        val text = when {
            dateRule == "Dom1" -> "The 1st"
            dateRule == "Dom15" -> "The 15th"
            dateRule == "DomL" -> "End of month"
            else -> {
                val n = MO_RE.matchEntire(dateRule)?.groupValues?.get(1)?.toInt() ?: 1
                if (n == 1) "In a month" else "In $n months"
            }
        }
        return DayPhrase("calendar", text, adjectival = false)
    }

    private fun phraseFor(key: PatternKey, resolvedEpoch: Long, now: Instant, zone: ZoneId): DayPhrase {
        val day = snoozeDay(LocalDateTime.ofInstant(Instant.ofEpochMilli(resolvedEpoch), zone))
        return dayPhrase(dateRuleOf(key), labelDist(resolvedEpoch, now, zone), day.dayOfWeek)
    }

    /** Which day phrase a label uses (today, tomorrow, inDays, inWeeks, this, next, inTwoWeeks, calendar). */
    internal fun phraseKind(key: PatternKey, resolvedEpoch: Long, now: Instant, zone: ZoneId): String =
        phraseFor(key, resolvedEpoch, now, zone).kind

    /** A same-day hours-offset row's phrase: "littleWhile" up to [LITTLE_WHILE_MAX_HOURS], else "muchLater"; null for other keys. */
    internal fun offsetPhraseKind(key: PatternKey): String? {
        if (isTimedKey(key) || dateRuleOf(key) != "D0") return null
        return if (key.substringAfter("_h").toInt() <= LITTLE_WHILE_MAX_HOURS) "littleWhile" else "muchLater"
    }

    private fun todayPhrase(region: String): String = when (region) {
        "morning" -> "This morning"
        "afternoon" -> "This afternoon"
        "evening" -> "This evening"
        else -> "Tonight"
    }

    private data class LabelParts(val text: String, val parenthesizedTime: Boolean)

    private fun labelParts(key: PatternKey, resolvedEpoch: Long, now: Instant, zone: ZoneId): LabelParts {
        val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(resolvedEpoch), zone)
        val region = bucketOf(minutesSince0500(dt))
        val phrase = phraseFor(key, resolvedEpoch, now, zone)

        if (!isTimedKey(key)) {
            when (offsetPhraseKind(key)) {
                "littleWhile" -> return LabelParts("In a little while", true)
                "muchLater" -> return LabelParts("Much later", true)
            }
            // A non-D0 offset seen between 00:00 and 05:00 can land on the calendar "today".
            val dayText = if (phrase.kind == "today") "Today" else phrase.text
            val h = key.substringAfter("_h").toInt()
            return if (h == 0) LabelParts("$dayText, same time", true) else LabelParts(dayText, false)
        }
        if (phrase.kind == "today") return LabelParts(todayPhrase(region), false)
        return LabelParts(if (phrase.adjectival) "${phrase.text} $region" else phrase.text, false)
    }

    /** Labels name the target's day from the resolved instant; the region word comes from the resolved time. */
    fun labelFor(key: PatternKey, resolvedEpoch: Long, now: Instant, zone: ZoneId): String {
        val parts = labelParts(key, resolvedEpoch, now, zone)
        val timeStr = formatClock(resolvedEpoch, zone)
        return if (parts.parenthesizedTime) "${parts.text} ($timeStr)" else "${parts.text}, $timeStr"
    }

    /** [labelFor] without the clock time, for layouts that show the time in its own column. */
    fun labelText(key: PatternKey, resolvedEpoch: Long, now: Instant, zone: ZoneId): String =
        labelParts(key, resolvedEpoch, now, zone).text

    fun lastText(target: Long, zone: ZoneId): String {
        val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(target), zone)
        val month = dt.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        return "Last · $month ${dt.dayOfMonth}"
    }


    fun formatClock(epochMillis: Long, zone: ZoneId): String =
        formatClockTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone))

    private fun dayName(dow: DayOfWeek): String = dow.getDisplayName(TextStyle.FULL, Locale.getDefault())

    private val CLOCK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun formatClockTime(dt: LocalDateTime): String = dt.format(CLOCK_FORMAT)

    fun rowIcon(key: PatternKey, resolvedEpoch: Long, now: Instant, zone: ZoneId): RowIcon {
        val dateRule = dateRuleOf(key)
        if (!isTimedKey(key)) return RowIcon.OFFSET
        val dist = labelDist(resolvedEpoch, now, zone)
        val region = regionOf(resolvedEpoch, zone)
        return when {
            dist == 0 -> if (region == "morning" || region == "afternoon") RowIcon.TODAY_DAY else RowIcon.TODAY_NIGHT
            dist == 1 -> RowIcon.TOMORROW
            D_RE.matches(dateRule) || W_RE.matches(dateRule) -> RowIcon.DAYS
            WD_RE.matches(dateRule) || WN_RE.matches(dateRule) -> RowIcon.WEEKDAY
            else -> RowIcon.MONTHLY
        }
    }
}
