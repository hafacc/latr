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
    // Lifetime counts per pick log key (see pickLogKey); only written while the user opts in.
    val picks: Map<String, Long> = emptyMap(),
)

/** Everything a commit changed, so undo can restore it exactly. */
data class CommitResult(
    val next: SnoozeStatsSnapshot,
    val touchedSetId: String?,
    val undoSetSnapshot: SetStat?,
    val touchedTodSlot: String?,
    val undoTodSnapshot: SetStat?,
    val undoLastCustom: LastCustom?,
    val touchedPickKey: String? = null,
    val undoPickSnapshot: Long? = null,
    // Every other set and quick-time slot the commit took votes from, as they were.
    val undoNearbySets: Map<String, SetStat> = emptyMap(),
    val undoNearbyTod: Map<String, SetStat> = emptyMap(),
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
    const val TOD_HALF_LIFE_DAYS = 21L
    const val SLOT_MINUTES = 5
    private const val DAY_BOUNDARY_HOUR = 5L
    const val DEFAULT_FLOOR = 0.05
    private const val QUICK_TIMES_MAX = 4
    private const val SCORE_EPS = 1e-9
    // A key must resolve at least this far ahead of now.
    private const val MIN_LEAD_MS = 60_000L
    // A time this far from a pick gives way by half: half the pick's weight in the menu, half its own votes when the pick is snoozed to.
    private const val NEARBY_HALF_DISTANCE_MINUTES = 30.0
    private const val LITTLE_WHILE_MAX_HOURS = 3
    const val SHOWN_ROWS = 5
    const val DEEP_ROWS = 20

    val KEY_RE = Regex("^(D(0|[1-9]\\d*)|W[1-4]|Wd[1-7]|Wn[1-7]|Dom1|Dom15|DomL|Mo[1-3])(@([01]\\d|2[0-3])[0-5][05]|_h([0-9]|1[0-2]))$")
    val SLOT_RE = Regex("^([01]\\d|2[0-3])[0-5][05]$")
    val PICK_RE = Regex("^([1-9]|1\\d|20|none)$")

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

    private val D_RE = Regex("^D(0|[1-9]\\d*)$")
    private val W_RE = Regex("^W([1-4])$")
    private val WD_RE = Regex("^Wd([1-7])$")
    private val WN_RE = Regex("^Wn([1-7])$")
    private val MO_RE = Regex("^Mo([1-3])$")
    private val LONG_PERIOD_RULES = setOf("Dom1", "Dom15", "DomL", "Mo1", "Mo2", "Mo3")

    /** The set of pattern keys a commit from [at] to [target] matches. Empty if no date rule reaches it. */
    fun extract(at: Instant, target: Instant, zone: ZoneId): Set<PatternKey> =
        extractTimedKeys(at, LocalDateTime.ofInstant(target, zone), zone) + extractOffsetKeys(at, target, zone)

    /** Every clock key that, resolved at [at], lands on [t]'s slot. */
    private fun extractTimedKeys(at: Instant, t: LocalDateTime, zone: ZoneId): Set<PatternKey> {
        val slot = slotOf(t)
        val clock = slotClockMinutes(slot)
        val anchor = firstHitDay(clock, at, zone)
        val targetDay = snoozeDay(t)
        val d = ChronoUnit.DAYS.between(anchor, targetDay)
        if (d < 0) return emptySet()
        if (targetDay == snoozeDay(LocalDateTime.ofInstant(at, zone))) return setOf("D$d@$slot")
        val dow = targetDay.dayOfWeek.value
        val candidates = mutableListOf("D$d", "Wd$dow", "Wn$dow", "Dom1", "Dom15", "DomL")
        if (d % 7 == 0L && d in 7..28) candidates += "W${d / 7}"
        candidates += listOf("Mo1", "Mo2", "Mo3")
        return candidates.filter { clockRuleDay(it, anchor, at, zone) == targetDay }.map { "$it@$slot" }.toSet()
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
        // An offset that crosses into the next snooze day is really a clock time there, not "N hours later".
        if (h < 0 || h > 12 || snoozeDay(base.toLocalDateTime()) != snoozeDay(tLocal)) return emptySet()
        val rules = offsetRulesFor(snoozeDay(aLocal), snoozeDay(base.toLocalDateTime()))
        return rules.mapNotNull { r -> if (r == "D0" && h == 0L) null else "${r}_h$h" }.toSet()
    }

    /** The date rules an hour offset from [fromDay] can use to reach [toDay]. */
    private fun offsetRulesFor(fromDay: LocalDate, toDay: LocalDate): List<String> {
        val d = ChronoUnit.DAYS.between(fromDay, toDay)
        if (d < 0) return emptyList()
        val rules = mutableListOf("D$d")
        if (d == 7L || d == 14L || d == 21L || d == 28L) rules += "W${d / 7}"
        if (d in 1..7) rules += "Wd${toDay.dayOfWeek.value}"
        if (d in 8..14) rules += "Wn${toDay.dayOfWeek.value}"
        return rules
    }

    /** [clockMinutes] on snooze day [day], as wall-clock minutes past 05:00 so a night time keeps its clock reading across DST. */
    private fun onSnoozeDay(day: LocalDate, clockMinutes: Int, zone: ZoneId): Long {
        val sinceBoundary = (clockMinutes - DAY_BOUNDARY_HOUR.toInt() * 60 + 24 * 60) % (24 * 60)
        return LocalDateTime.of(day, LocalTime.of(DAY_BOUNDARY_HOUR.toInt(), 0)).plusMinutes(sinceBoundary.toLong())
            .atZone(zone).toInstant().toEpochMilli()
    }

    /** The snooze day of the first [clockMinutes] at least [MIN_LEAD_MS] after [from]. */
    private fun firstHitDay(clockMinutes: Int, from: Instant, zone: ZoneId): LocalDate {
        var day = snoozeDay(LocalDateTime.ofInstant(from, zone))
        while (onSnoozeDay(day, clockMinutes, zone) <= from.toEpochMilli() + MIN_LEAD_MS) day = day.plusDays(1)
        return day
    }

    /** The snooze day a clock key's date rule names: days and weeks count from [anchor] (its first hit), months from [now]'s snooze day. */
    private fun clockRuleDay(dateRule: String, anchor: LocalDate, now: Instant, zone: ZoneId): LocalDate? {
        D_RE.matchEntire(dateRule)?.let { return anchor.plusDays(it.groupValues[1].toLong()) }
        W_RE.matchEntire(dateRule)?.let { return anchor.plusWeeks(it.groupValues[1].toLong()) }
        WD_RE.matchEntire(dateRule)?.let { return nextWeekday(anchor, DayOfWeek.of(it.groupValues[1].toInt()), 0, 6) }
        WN_RE.matchEntire(dateRule)?.let { return nextWeekday(anchor, DayOfWeek.of(it.groupValues[1].toInt()), 7, 13) }
        if (dateRule == "Dom1") return nextDayOfMonth(anchor.minusDays(1), 1)
        if (dateRule == "Dom15") return nextDayOfMonth(anchor.minusDays(1), 15)
        if (dateRule == "DomL") return nextMonthEnd(anchor.minusDays(1))
        MO_RE.matchEntire(dateRule)?.let {
            return snoozeDay(LocalDateTime.ofInstant(now, zone)).plusMonths(it.groupValues[1].toLong())
        }
        return null
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

    fun setH(id: String): Long = setH(splitSetId(id))

    fun setId(keys: Set<PatternKey>): String = keys.sorted().joinToString("__")

    private fun splitSetId(id: String): Set<PatternKey> = id.split("__").toSet()

    /** The day count of the one D-rule key among [keys], or null unless there is exactly one. */
    private fun dayCountOf(keys: Collection<PatternKey>): Long? =
        keys.mapNotNull { D_RE.matchEntire(dateRuleOf(it))?.groupValues?.get(1)?.toLong() }.singleOrNull()

    /** A set's keys as they read now: none if its clock keys disagree about the day (older builds' 7- and 14-day sets), no offsets off its clock time's day (older builds saved some across midnight). */
    private fun liveMembers(id: String): Set<PatternKey> {
        val keys = splitSetId(id)
        val timed = keys.filter { isTimedKey(it) }.toSet()
        val days = dayCountOf(timed) ?: return timed
        val consistent = timed.all { key ->
            val rule = dateRuleOf(key)
            when {
                W_RE.matches(rule) -> rule.drop(1).toLong() * 7 == days
                WD_RE.matches(rule) -> days <= 6
                WN_RE.matches(rule) -> days in 7..13
                else -> true
            }
        }
        if (!consistent) return emptySet()
        val offsetDays = dayCountOf(keys.filter { !isTimedKey(it) })
        return if (offsetDays != null && offsetDays - days in 0..1) keys else timed
    }

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

    /** The share of its votes a time [distanceMinutes] from a snoozed-to time loses, and of a pick's weight it is discounted by. */
    private fun nearbyShare(distanceMinutes: Double): Double =
        2.0.pow(-Math.abs(distanceMinutes) / NEARBY_HALF_DISTANCE_MINUTES)

    /** Minutes between two clock times, the short way round midnight. */
    private fun clockDistance(clockMinutes: Int, other: Int): Int {
        val apart = Math.abs(clockMinutes - other)
        return minOf(apart, 24 * 60 - apart)
    }

    /** The instant of [target]'s 5-minute slot, where the commit's own clock keys resolve. */
    private fun slotTime(target: Long, zone: ZoneId): Long {
        val local = LocalDateTime.ofInstant(Instant.ofEpochMilli(target), zone)
        return onSnoozeDay(snoozeDay(local), slotClockMinutes(slotOf(local)), zone)
    }

    /** The share of its votes a set loses to a snooze to [targetTime]: the largest among its clock keys resolved at [at], none if one lands exactly there. */
    private fun nearbySetShare(id: String, at: Instant, targetTime: Long, zone: ZoneId): Double {
        val times = splitSetId(id).filter { isTimedKey(it) }.mapNotNull { resolveFuture(it, at, zone) }
        return if (targetTime in times) {
            0.0
        } else {
            times.maxOfOrNull { nearbyShare((it - targetTime) / 60_000.0) } ?: 0.0
        }
    }

    /** [entries] with each one's count cut by its [shareOf], paired with the entries that changed, as they were. */
    private fun takeVotes(
        entries: Map<String, SetStat>,
        shareOf: (String) -> Double,
    ): Pair<Map<String, SetStat>, Map<String, SetStat>> {
        val next = entries.toMutableMap()
        val taken = mutableMapOf<String, SetStat>()
        for ((id, stat) in entries) {
            val count = stat.c * (1.0 - shareOf(id))
            if (count != stat.c) {
                taken[id] = stat
                next[id] = SetStat(count, stat.t)
            }
        }
        return next to taken
    }

    /** Pure: returns the next state plus exactly what to undo. [pickedKey] is the suggestion row's key, else null. */
    fun commit(
        stats: SnoozeStatsSnapshot,
        at: Long,
        target: Long,
        zone: ZoneId,
        source: String,
        pickedKey: PatternKey? = null,
        pickLog: String? = null,
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
        val atInstant = Instant.ofEpochMilli(at)
        val targetTime = slotTime(target, zone)
        val (nextSets, nearbySets) = takeVotes(sets) { other ->
            if (other == id) 0.0 else nearbySetShare(other, atInstant, targetTime, zone)
        }
        sets = nextSets

        var tod = stats.tod
        var slot: String? = null
        var prevTod: SetStat? = null
        var nearbyTod = emptyMap<String, SetStat>()
        if (feedsQuickTimes(at, target, zone, source, pickedKey)) {
            slot = slotOf(LocalDateTime.ofInstant(Instant.ofEpochMilli(target), zone))
            prevTod = stats.tod[slot]
            tod = tod + (slot to bump(prevTod, at, TOD_HALF_LIFE_DAYS))
            val credited = slotClockMinutes(slot)
            val (nextTod, taken) = takeVotes(tod) { other ->
                if (other == slot) 0.0 else nearbyShare(clockDistance(slotClockMinutes(other), credited).toDouble())
            }
            tod = nextTod
            nearbyTod = taken
        }
        var picks = stats.picks
        val prevPick = pickLog?.let { stats.picks[it] }
        if (pickLog != null) picks = picks + (pickLog to (prevPick ?: 0L) + 1L)
        return CommitResult(
            next = SnoozeStatsSnapshot(sets, tod, nextLastCustom, picks),
            touchedSetId = id,
            undoSetSnapshot = prevSet,
            touchedTodSlot = slot,
            undoTodSnapshot = prevTod,
            undoLastCustom = stats.lastCustom,
            touchedPickKey = pickLog,
            undoPickSnapshot = prevPick,
            undoNearbySets = nearbySets,
            undoNearbyTod = nearbyTod,
        )
    }

    /** A clock key as builds before the first-hit rule resolved it: its date rule counted from [at]'s snooze day. For [upgradeLegacySets] only. */
    private fun legacyClockTime(key: PatternKey, at: Instant, zone: ZoneId): Long? {
        if (!isTimedKey(key) || !KEY_RE.matches(key)) return null
        val from = snoozeDay(LocalDateTime.ofInstant(at, zone))
        val dateRule = dateRuleOf(key)
        val day = when (dateRule) {
            "Dom1" -> nextDayOfMonth(from, 1)
            "Dom15" -> nextDayOfMonth(from, 15)
            "DomL" -> nextMonthEnd(from)
            else -> MO_RE.matchEntire(dateRule)?.let { from.plusMonths(it.groupValues[1].toLong()) }
                ?: offsetRuleDay(dateRule, from)
                ?: return null
        }
        return onSnoozeDay(day, slotClockMinutes(key.substringAfter('@')), zone).takeIf { it > at.toEpochMilli() + MIN_LEAD_MS }
    }

    /** Sets saved before the first-hit rule, re-learned from the target their clock keys named at their last commit; sets that land on the same new id merge. A set whose clock keys don't agree on one target is kept as saved. */
    fun upgradeLegacySets(sets: Map<String, SetStat>, zone: ZoneId): Map<String, SetStat> {
        val out = mutableMapOf<String, SetStat>()
        for ((id, stat) in sets) {
            val at = Instant.ofEpochMilli(stat.t)
            val targets = splitSetId(id).filter { isTimedKey(it) }.map { legacyClockTime(it, at, zone) }.toSet()
            val target = targets.singleOrNull()
            val keys = if (target != null) extract(at, Instant.ofEpochMilli(target), zone) else emptySet()
            val newId = if (keys.isNotEmpty()) setId(keys) else id
            val prev = out[newId]
            out[newId] = if (prev == null) {
                stat
            } else {
                val halfLife = setH(newId)
                val t = maxOf(prev.t, stat.t)
                SetStat(prev.c * decayFactor(t - prev.t, halfLife) + stat.c * decayFactor(t - stat.t, halfLife), t)
            }
        }
        return out
    }

    /** Reverts exactly what [result] changed. */
    fun revert(stats: SnoozeStatsSnapshot, result: CommitResult): SnoozeStatsSnapshot {
        var sets = stats.sets + result.undoNearbySets
        if (result.touchedSetId != null) {
            sets = if (result.undoSetSnapshot != null) {
                sets + (result.touchedSetId to result.undoSetSnapshot)
            } else {
                sets - result.touchedSetId
            }
        }
        var tod = stats.tod + result.undoNearbyTod
        if (result.touchedTodSlot != null) {
            tod = if (result.undoTodSnapshot != null) {
                tod + (result.touchedTodSlot to result.undoTodSnapshot)
            } else {
                tod - result.touchedTodSlot
            }
        }
        var picks = stats.picks
        if (result.touchedPickKey != null) {
            picks = if (result.undoPickSnapshot != null) {
                picks + (result.touchedPickKey to result.undoPickSnapshot)
            } else {
                picks - result.touchedPickKey
            }
        }
        return SnoozeStatsSnapshot(sets, tod, result.undoLastCustom, picks)
    }

    /** The menu's rows: the first [SHOWN_ROWS] of [rankDeep], in time order. */
    fun rank(
        partitions: List<SnoozeStatsSnapshot>,
        now: Instant,
        zone: ZoneId,
        n: Int = SHOWN_ROWS,
        floor: Double = DEFAULT_FLOOR,
    ): List<SnoozeRow> = shownRows(rankDeep(partitions, now, zone, n, floor), n)

    fun shownRows(deep: List<SnoozeRow>, n: Int = SHOWN_ROWS): List<SnoozeRow> =
        deep.take(n).sortedBy { it.epochMillis }

    /** The 1-based greedy rank of the deep row landing on [target]'s minute, or "none". */
    fun pickLogKey(deep: List<SnoozeRow>, target: Long): String {
        val minute = Math.floorDiv(target, 60_000L)
        val index = deep.indexOfFirst { Math.floorDiv(it.epochMillis, 60_000L) == minute }
        return if (index >= 0) "${index + 1}" else "none"
    }

    /** Greedy: take the key with the most live support less its nearby discount, spend every set backing it or a key at the same time, discount keys near it, repeat. Rows stay in selection order, so a row's index is its rank. */
    fun rankDeep(
        partitions: List<SnoozeStatsSnapshot>,
        now: Instant,
        zone: ZoneId,
        n: Int = DEEP_ROWS,
        floor: Double = DEFAULT_FLOOR,
    ): List<SnoozeRow> {
        val nowMillis = now.toEpochMilli()
        val liveById = mutableMapOf<String, Double>()
        val membersById = mutableMapOf<String, Set<PatternKey>>()
        for (partition in partitions) {
            for ((id, s) in partition.sets) {
                val members = membersById.getOrPut(id) { liveMembers(id) }
                if (members.isEmpty()) continue
                val live = s.c * decayFactor(nowMillis - s.t, setH(id))
                if (live <= 0.0) continue
                liveById[id] = (liveById[id] ?: 0.0) + live
            }
        }
        val available = liveById.keys.toMutableSet()

        val resolved = mutableMapOf<PatternKey, Long>()
        for (key in available.flatMap { membersById.getValue(it) }.toSet()) {
            resolveFuture(key, now, zone)?.let { resolved[key] = it }
        }

        val rows = mutableListOf<SnoozeRow>()
        val settled = mutableSetOf<PatternKey>()
        val discounts = mutableMapOf<PatternKey, Double>()
        while (rows.size < n) {
            var best: PatternKey? = null
            var bestScore = 0.0
            var bestNet = 0.0
            for ((key, time) in resolved) {
                if (key in settled) continue
                val agg = available.filter { key in membersById.getValue(it) }.sumOf { liveById.getValue(it) }
                if (agg <= floor) continue
                val net = maxOf(0.0, agg - (discounts[key] ?: 0.0))
                val current = best
                val better = current == null || outranks(net, agg, bestNet, bestScore) {
                    tieBreak(key, time, current, resolved.getValue(current), now, zone) < 0
                }
                if (better) {
                    best = key
                    bestScore = agg
                    bestNet = net
                }
            }
            if (best == null) break
            val pickedKey = best
            val pickedTime = resolved.getValue(pickedKey)
            for ((key, time) in resolved) {
                discounts[key] = (discounts[key] ?: 0.0) + nearbyDiscount(bestScore, (time - pickedTime) / 60_000.0)
            }
            val absorbed = resolved.keys.filter {
                it != pickedKey && it !in settled && resolved.getValue(it) == pickedTime
            }
            rows += SnoozeRow(pickedTime, labelFor(pickedKey, pickedTime, now, zone), bestScore, pickedKey)
            settled += pickedKey
            settled += absorbed
            val spent = settled.toSet()
            val toRemove = available.filter { id -> membersById.getValue(id).any { it in spent } }
            available -= toRemove.toSet()
        }
        return rows
    }

    /** The most recently updated custom pick across every partition, or null. */
    fun latestLastCustom(partitions: List<SnoozeStatsSnapshot>): LastCustom? =
        partitions.mapNotNull { it.lastCustom }.maxByOrNull { it.at }

    /** The "Last" row's target: the newest custom pick, if it's still ahead and no ranked row lands at exactly that time. */
    fun lastRow(partitions: List<SnoozeStatsSnapshot>, now: Instant, rows: List<SnoozeRow>): Long? {
        val last = latestLastCustom(partitions) ?: return null
        if (last.target <= now.toEpochMilli()) return null
        if (rows.any { it.epochMillis == last.target }) return null
        return last.target
    }

    // On a tie, after clock time over offset, lower wins: a day count landing today/tomorrow, then a named weekday/day of month, then a count of days/weeks/months.
    private fun tieRank(key: PatternKey, time: Long, now: Instant, zone: ZoneId): Int {
        val dateRule = dateRuleOf(key)
        return when {
            D_RE.matches(dateRule) -> if (labelDist(time, now, zone) <= 1) 0 else 4
            WD_RE.matches(dateRule) -> 1
            WN_RE.matches(dateRule) -> 2
            dateRule == "Dom1" || dateRule == "Dom15" || dateRule == "DomL" -> 3
            W_RE.matches(dateRule) -> 5
            else -> 6
        }
    }

    // Both platforms sum doubles in different orders, so an exact == could pick different winners.
    private fun sameScore(a: Double, b: Double): Boolean =
        Math.abs(a - b) <= SCORE_EPS * maxOf(1.0, Math.abs(a), Math.abs(b))

    /** How much a pick of [weight] discounts a time [distanceMinutes] away from it. */
    private fun nearbyDiscount(weight: Double, distanceMinutes: Double): Double =
        weight * nearbyShare(distanceMinutes)

    /** Whether a candidate beats the best so far: higher discounted score, then higher undiscounted score, then [tie]. */
    private inline fun outranks(net: Double, score: Double, bestNet: Double, bestScore: Double, tie: () -> Boolean): Boolean = when {
        !sameScore(net, bestNet) -> net > bestNet
        !sameScore(score, bestScore) -> score > bestScore
        else -> tie()
    }

    /** Negative if [a] (resolving to [aTime]) should be preferred over [b] when their aggregate scores tie. */
    private fun tieBreak(a: PatternKey, aTime: Long, b: PatternKey, bTime: Long, now: Instant, zone: ZoneId): Int {
        val oa = if (isTimedKey(a)) 0 else 1
        val ob = if (isTimedKey(b)) 0 else 1
        if (oa != ob) return oa - ob
        val ra = tieRank(a, aTime, now, zone)
        val rb = tieRank(b, bTime, now, zone)
        if (ra != rb) return ra - rb
        return a.compareTo(b) // code-unit order; must match web's `a < b`, not localeCompare
    }

    /** Where [key] points from [now], or null if that isn't at least a minute ahead. */
    internal fun resolveFuture(key: PatternKey, now: Instant, zone: ZoneId): Long? =
        resolveKey(key, now, zone)?.takeIf { it > now.toEpochMilli() + MIN_LEAD_MS }

    private fun resolveKey(key: PatternKey, now: Instant, zone: ZoneId): Long? {
        if (!KEY_RE.matches(key)) return null
        val dateRule = dateRuleOf(key)
        val nowLocal = LocalDateTime.ofInstant(now, zone)
        return if (!isTimedKey(key)) {
            val h = key.substringAfter("_h").toInt()
            val sd = snoozeDay(nowLocal)
            val date = offsetRuleDay(dateRule, sd) ?: return null
            val numDays = ChronoUnit.DAYS.between(sd, date)
            // Wall-clock days, then elapsed hours (ZonedDateTime.plusHours), matching extraction.
            val zoned = now.atZone(zone).plusDays(numDays).plusHours(h.toLong())
            val epoch = snapTo5Min(zoned.toInstant().toEpochMilli())
            if (snoozeDay(LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), zone)) != date) null else epoch
        } else {
            val clock = slotClockMinutes(key.substringAfter('@'))
            val date = clockRuleDay(dateRule, firstHitDay(clock, now, zone), now, zone) ?: return null
            onSnoozeDay(date, clock, zone)
        }
    }

    /** The snooze day an hour offset's date rule points to, counted from today's snooze day [from]. */
    private fun offsetRuleDay(dateRule: String, from: LocalDate): LocalDate? {
        D_RE.matchEntire(dateRule)?.let { return from.plusDays(it.groupValues[1].toLong()) }
        W_RE.matchEntire(dateRule)?.let { return from.plusWeeks(it.groupValues[1].toLong()) }
        WD_RE.matchEntire(dateRule)?.let { return nextWeekday(from, DayOfWeek.of(it.groupValues[1].toInt()), 1, 7) }
        WN_RE.matchEntire(dateRule)?.let { return nextWeekday(from, DayOfWeek.of(it.groupValues[1].toInt()), 8, 14) }
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

    /** Up to four slots, picked greedily with the same nearby discount as rows (earliest from 05:00 on a tie), in clock order. */
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
        val discounts = mutableMapOf<Int, Double>()
        val picked = mutableListOf<QuickTime>()
        while (picked.size < QUICK_TIMES_MAX && weights.isNotEmpty()) {
            var best: Int? = null
            var bestWeight = 0.0
            var bestNet = 0.0
            for ((clock, w) in weights) {
                if (w <= floor) continue
                val net = maxOf(0.0, w - (discounts[clock] ?: 0.0))
                val current = best
                val better = current == null || outranks(net, w, bestNet, bestWeight) { sinceBoundary(clock) < sinceBoundary(current) }
                if (better) {
                    best = clock
                    bestWeight = w
                    bestNet = net
                }
            }
            if (best == null) break
            picked += QuickTime(best, bestWeight)
            weights.remove(best)
            for (clock in weights.keys) {
                discounts[clock] = (discounts[clock] ?: 0.0) + nearbyDiscount(bestWeight, clockDistance(clock, best).toDouble())
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
                dist <= 6 -> DayPhrase("this", "This ${dayName(weekday)}", adjectival = true)
                dist <= 13 -> DayPhrase("next", "Next ${dayName(weekday)}", adjectival = true)
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

    /** The shortest phrase a ranked row would use for this day, else the date. */
    fun lastText(target: Long, now: Instant, zone: ZoneId): String {
        val dist = labelDist(target, now, zone)
        val rule = when {
            dist <= 1 -> "D1"
            dist <= 14 -> "Wd1"
            else -> null
        }
        val text = if (rule != null) {
            labelText("$rule@0000", target, now, zone)
        } else {
            val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(target), zone)
            "${dt.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${dt.dayOfMonth}"
        }
        return "Last · $text"
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
            D_RE.matches(dateRule) || W_RE.matches(dateRule) -> if (dist >= 14) RowIcon.DAYS else RowIcon.WEEKDAY
            WD_RE.matches(dateRule) || WN_RE.matches(dateRule) -> RowIcon.WEEKDAY
            else -> RowIcon.MONTHLY
        }
    }
}
