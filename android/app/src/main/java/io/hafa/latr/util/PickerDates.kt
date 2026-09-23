package io.hafa.latr.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Material3's DatePicker speaks in UTC-midnight millis, whatever the device zone. */
object PickerDates {
    fun initialPickerMillis(today: LocalDate): Long =
        today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun pickerMillisToDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}
