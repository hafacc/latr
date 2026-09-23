package io.hafa.latr.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PickerDatesTest {

    @Test
    fun `today's picker millis read back as today in zones on both sides of UTC`() {
        for (zone in listOf("Asia/Tokyo", "America/Los_Angeles", "Pacific/Kiritimati", "Pacific/Pago_Pago")) {
            val today = LocalDate.now(ZoneId.of(zone))
            assertEquals(zone, today, PickerDates.pickerMillisToDate(PickerDates.initialPickerMillis(today)))
        }
    }
}
