package cc.hafa.latr.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

object LocalDateTimeUtil {
    fun fromEpochMillis(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone).toString()
    }

    fun toEpochMillis(isoString: String, zone: ZoneId = ZoneId.systemDefault()): Long {
        return LocalDateTime.parse(isoString).atZone(zone).toInstant().toEpochMilli()
    }

    fun now(): String {
        return LocalDateTime.now().toString()
    }

    fun formatSnoozeTime(epochMillis: Long): String {
        val now = System.currentTimeMillis()
        val hoursDelta = abs(epochMillis - now) / (1000 * 60 * 60)
        val timeStr = SnoozeSuggestions.formatClock(epochMillis, ZoneId.systemDefault())
        return if (hoursDelta < 24) {
            timeStr
        } else {
            val dateStr = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
                .format(DATE_FORMAT)
            "$dateStr at $timeStr"
        }
    }

    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", SnoozeSuggestions.LABEL_LOCALE)
}
