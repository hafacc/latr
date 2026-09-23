package io.hafa.latr.util

import android.content.Context
import android.text.format.DateUtils
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
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

    fun formatSnoozeTime(epochMillis: Long, context: Context): String {
        val now = System.currentTimeMillis()
        val hoursDelta = abs(epochMillis - now) / (1000 * 60 * 60)
        val timeStr = SnoozeSuggestions.formatClock(epochMillis, ZoneId.systemDefault())
        return if (hoursDelta < 24) {
            timeStr
        } else {
            val dateStr = DateUtils.formatDateTime(
                context,
                epochMillis,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL
            )
            "$dateStr at $timeStr"
        }
    }
}
