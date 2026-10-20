package cc.hafa.latr.util

import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** How often a repeating todo comes back. */
enum class RepeatUnit(val wire: String) { DAY("day"), WEEK("week"), MONTH("month") }

/**
 * A repeating todo's rule; same as web `utils/repeat.ts`, pinned by `testdata/repeat-fixtures.json`.
 * Times are local wall-clock, like `snoozeUntil`.
 */
data class Repeat(
    /** 1..7: every N days, weeks or months. */
    val every: Int,
    val unit: RepeatUnit,
    /** ISO weekday (Monday = 1); used by [RepeatUnit.WEEK]. */
    val weekday: Int,
    /** 1..31, or [LAST_DAY]; used by [RepeatUnit.MONTH]. */
    val monthDay: Int,
    /** HH:MM. */
    val at: String,
    /** YYYY-MM-DD the every-N count starts from: the first occurrence when the rule was saved. */
    val anchor: String,
) {
    /** The Firestore map. */
    fun toMap(): Map<String, Any> = mapOf(
        "every" to every,
        "unit" to unit.wire,
        "weekday" to weekday,
        "monthDay" to monthDay,
        "at" to at,
        "anchor" to anchor,
    )

    /** The Room column. */
    fun toJson(): String = JSONObject(toMap()).toString()

    private fun matches(date: LocalDate): Boolean {
        val anchorDate = LocalDate.parse(anchor)
        return when (unit) {
            RepeatUnit.DAY -> Math.floorMod(ChronoUnit.DAYS.between(anchorDate, date), every.toLong()) == 0L
            RepeatUnit.WEEK -> {
                val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val anchorMonday = anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                date.dayOfWeek.value == weekday &&
                    Math.floorMod(ChronoUnit.WEEKS.between(anchorMonday, monday), every.toLong()) == 0L
            }
            RepeatUnit.MONTH -> {
                val length = date.lengthOfMonth()
                val target = if (monthDay == LAST_DAY) length else minOf(monthDay, length)
                val months = (date.year * 12 + date.monthValue) - (anchorDate.year * 12 + anchorDate.monthValue)
                date.dayOfMonth == target && Math.floorMod(months, every) == 0
            }
        }
    }

    /** The first occurrence strictly after [after], both local `YYYY-MM-DDTHH:mm:ss`. */
    fun nextOccurrence(after: String): String {
        val afterTime = LocalDateTime.parse(after)
        val time = LocalTime.parse(at)
        var date = afterTime.toLocalDate()
        repeat(SEARCH_DAYS) {
            if (matches(date)) {
                val occurrence = LocalDateTime.of(date, time)
                if (occurrence.isAfter(afterTime)) return occurrence.format(LOCAL_ISO)
            }
            date = date.plusDays(1)
        }
        error("no occurrence of $this after $after")
    }

    /** The repeating row's second line, e.g. "Repeats every Monday at 07:00". */
    fun text(): String {
        val plural = every > 1
        val schedule = when (unit) {
            RepeatUnit.DAY -> if (plural) "every $every days" else "every day"
            RepeatUnit.WEEK -> {
                val name = WEEKDAY_NAMES[weekday - 1]
                if (plural) "every $every weeks on $name" else "every $name"
            }
            RepeatUnit.MONTH -> {
                val day = if (monthDay == LAST_DAY) "the last day" else "the ${ordinal(monthDay)}"
                if (plural) "every $every months on $day" else "every month on $day"
            }
        }
        return "Repeats $schedule at $at"
    }

    companion object {
        const val LAST_DAY = -1
        private const val SEARCH_DAYS = 400
        private val LOCAL_ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        private val WEEKDAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        private val CLOCK = Regex("""\d{2}:\d{2}""")
        private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")

        /** A rule saved at [now] (local ISO), anchored on its first occurrence. */
        fun anchored(every: Int, unit: RepeatUnit, weekday: Int, monthDay: Int, at: String, now: String): Repeat {
            val draft = Repeat(every, unit, weekday, monthDay, at, now.take(10))
            val first = draft.copy(every = 1).nextOccurrence(now)
            return draft.copy(anchor = first.take(10))
        }

        private fun intIn(value: Any?, range: IntRange): Int? {
            val number = value as? Number ?: return null
            val asInt = number.toInt()
            return asInt.takeIf { number.toDouble() == asInt.toDouble() && asInt in range }
        }

        private fun validDate(value: String): Boolean =
            DATE.matches(value) && try {
                LocalDate.parse(value)
                true
            } catch (_: DateTimeParseException) {
                false
            }

        /** A saved rule (Firestore map or parsed JSON), or null when it is missing or malformed. */
        fun read(value: Any?): Repeat? {
            val raw = value as? Map<*, *> ?: return null
            val every = intIn(raw["every"], 1..7) ?: return null
            val unit = RepeatUnit.entries.firstOrNull { it.wire == raw["unit"] } ?: return null
            val weekday = intIn(raw["weekday"], 1..7) ?: return null
            val monthDay = intIn(raw["monthDay"], LAST_DAY..31)?.takeIf { it != 0 } ?: return null
            val at = (raw["at"] as? String)?.takeIf {
                CLOCK.matches(it) && it.take(2).toInt() < 24 && it.takeLast(2).toInt() < 60
            } ?: return null
            val anchor = (raw["anchor"] as? String)?.takeIf(::validDate) ?: return null
            return Repeat(every, unit, weekday, monthDay, at, anchor)
        }

        /** The Room column back to a rule. */
        fun fromJson(json: String?): Repeat? {
            if (json == null) return null
            return try {
                val obj = JSONObject(json)
                read(obj.keys().asSequence().associateWith { obj.get(it) })
            } catch (_: org.json.JSONException) {
                null
            }
        }

        private fun ordinal(day: Int): String = when {
            day % 100 in 11..13 -> "${day}th"
            day % 10 == 1 -> "${day}st"
            day % 10 == 2 -> "${day}nd"
            day % 10 == 3 -> "${day}rd"
            else -> "${day}th"
        }
    }
}
