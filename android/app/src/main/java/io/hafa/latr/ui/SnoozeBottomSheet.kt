package io.hafa.latr.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NextWeek
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.hafa.latr.ui.theme.LatrTheme
import io.hafa.latr.util.LocalDateTimeUtil
import io.hafa.latr.util.PickerDates
import io.hafa.latr.util.QuickTime
import io.hafa.latr.util.RowIcon
import io.hafa.latr.util.SnoozeRow
import io.hafa.latr.util.SnoozeStatsSnapshot
import io.hafa.latr.util.SnoozeSuggestions
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.delay

private sealed class CustomPickerState {
    data object Hidden : CustomPickerState()
    data object ShowingDatePicker : CustomPickerState()
    data class ShowingTimePicker(val selectedDate: LocalDate) : CustomPickerState()
}

/** One rendered menu row: a learned suggestion, the persisted "Last" custom pick, or "Custom". */
private sealed class MenuRow {
    data class Suggestion(val row: SnoozeRow) : MenuRow()
    data class Last(val epochMillis: Long) : MenuRow()
    data object Custom : MenuRow()
}

private const val MENU_CLOCK_TICK_MS = 30_000L

private fun buildMenuRows(
    partitions: List<SnoozeStatsSnapshot>,
    now: Instant,
    zone: ZoneId,
): List<MenuRow> {
    val suggestions = SnoozeSuggestions.rank(partitions, now, zone)
    val rows = mutableListOf<MenuRow>()
    rows += suggestions.map { MenuRow.Suggestion(it) }
    SnoozeSuggestions.lastRow(partitions, now, suggestions)?.let { rows += MenuRow.Last(it) }
    rows += MenuRow.Custom
    return rows
}

/** [onSnoozeSelected] returns false if the pick was rejected (its time already passed); the sheet then stays open with refreshed rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnoozeBottomSheet(
    onDismiss: () -> Unit,
    onSnoozeSelected: (isoDateTime: String, source: String, pickedKey: String?) -> Boolean,
    partitions: List<SnoozeStatsSnapshot>,
    modifier: Modifier = Modifier,
    initialNow: Instant? = null,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var now by remember { mutableStateOf(initialNow ?: Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(MENU_CLOCK_TICK_MS)
            now = Instant.now()
        }
    }
    val rows = remember(now, partitions, zone) { buildMenuRows(partitions, now, zone) }
    val quickTimes = remember(now, partitions) { SnoozeSuggestions.quickTimes(partitions, now) }

    var customPickerState by remember { mutableStateOf<CustomPickerState>(CustomPickerState.Hidden) }

    val pick = { epochMillis: Long, source: String, pickedKey: String? ->
        if (onSnoozeSelected(LocalDateTimeUtil.fromEpochMillis(epochMillis, zone), source, pickedKey)) {
            onDismiss()
        } else {
            now = Instant.now()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            rows.forEach { row ->
                MenuRowItem(
                    row = row,
                    now = now,
                    zone = zone,
                    onClick = {
                        when (row) {
                            is MenuRow.Custom -> customPickerState = CustomPickerState.ShowingDatePicker
                            is MenuRow.Suggestion -> pick(row.row.epochMillis, "suggestion", row.row.key)
                            is MenuRow.Last -> pick(row.epochMillis, "last", null)
                        }
                    }
                )
            }
        }
    }

    when (val state = customPickerState) {
        CustomPickerState.Hidden -> { /* nothing */
        }

        CustomPickerState.ShowingDatePicker -> {
            DatePickerSheet(
                quickTimes = quickTimes,
                now = now,
                zone = zone,
                onDismiss = { customPickerState = CustomPickerState.Hidden },
                onDateSelected = { date -> customPickerState = CustomPickerState.ShowingTimePicker(date) },
                onQuickTime = { epochMillis -> pick(epochMillis, "custom", null) }
            )
        }

        is CustomPickerState.ShowingTimePicker -> {
            TimePickerSheet(
                selectedDate = state.selectedDate,
                quickTimes = quickTimes,
                now = now,
                zone = zone,
                onDismiss = { customPickerState = CustomPickerState.Hidden },
                onConfirm = { epochMillis -> pick(epochMillis, "custom", null) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(
    quickTimes: List<QuickTime>,
    now: Instant,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onQuickTime: (epochMillis: Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null
    ) {
        DatePickerSheetContent(
            quickTimes = quickTimes,
            now = now,
            zone = zone,
            onDateSelected = onDateSelected,
            onQuickTime = onQuickTime
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheetContent(
    quickTimes: List<QuickTime>,
    now: Instant,
    zone: ZoneId,
    onDateSelected: (LocalDate) -> Unit,
    onQuickTime: (epochMillis: Long) -> Unit
) {
    val today = LocalDate.ofInstant(now, zone)
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = PickerDates.initialPickerMillis(today),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                !PickerDates.pickerMillisToDate(utcTimeMillis).isBefore(today)
        }
    )
    val selectedDate = datePickerState.selectedDateMillis?.let { PickerDates.pickerMillisToDate(it) }

    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Pick a date", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        DatePicker(state = datePickerState, showModeToggle = false, title = null)

        if (quickTimes.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                quickTimes.forEach { quickTime ->
                    val epoch = SnoozeSuggestions.quickTimeEpoch(selectedDate ?: today, quickTime.clockMinutes, zone)
                    TextButton(
                        onClick = { onQuickTime(epoch) },
                        enabled = selectedDate != null && epoch > now.toEpochMilli(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(SnoozeSuggestions.formatClock(epoch, zone))
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextButton(
                onClick = { selectedDate?.let { onDateSelected(it) } },
                enabled = selectedDate != null
            ) {
                Text("Custom time")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerSheet(
    selectedDate: LocalDate,
    quickTimes: List<QuickTime>,
    now: Instant,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onConfirm: (epochMillis: Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null
    ) {
        TimePickerSheetContent(
            selectedDate = selectedDate,
            quickTimes = quickTimes,
            now = now,
            zone = zone,
            onDismiss = onDismiss,
            onConfirm = onConfirm
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerSheetContent(
    selectedDate: LocalDate,
    quickTimes: List<QuickTime>,
    now: Instant,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onConfirm: (epochMillis: Long) -> Unit
) {
    val initialTime = remember(selectedDate) {
        val best = quickTimes.maxByOrNull { it.weight }
        if (best != null && SnoozeSuggestions.quickTimeEpoch(selectedDate, best.clockMinutes, zone) > now.toEpochMilli()) {
            LocalTime.of(best.clockMinutes / 60, best.clockMinutes % 60)
        } else {
            SnoozeSuggestions.nextQuarterHour(LocalTime.ofInstant(now, zone))
        }
    }
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true
    )
    val selectedEpoch = LocalDateTime.of(selectedDate, LocalTime.of(timePickerState.hour, timePickerState.minute))
        .atZone(zone).toInstant().toEpochMilli()

    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Pick a time", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        TimePicker(state = timePickerState)

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TextButton(
                onClick = { onConfirm(selectedEpoch) },
                enabled = selectedEpoch > now.toEpochMilli()
            ) { Text("Confirm") }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun MenuRowItem(
    row: MenuRow,
    now: Instant,
    zone: ZoneId,
    onClick: () -> Unit
) {
    val label = when (row) {
        is MenuRow.Suggestion -> row.row.label
        is MenuRow.Last -> SnoozeSuggestions.lastLabel(row.epochMillis, zone)
        is MenuRow.Custom -> "Custom"
    }
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = {
            Icon(
                imageVector = row.icon(now, zone),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private fun MenuRow.icon(now: Instant, zone: ZoneId): ImageVector = when (this) {
    is MenuRow.Suggestion -> when (SnoozeSuggestions.rowIcon(row.key, row.epochMillis, now, zone)) {
        RowIcon.OFFSET -> Icons.Default.Schedule
        RowIcon.TODAY_DAY -> Icons.Default.LightMode
        RowIcon.TODAY_NIGHT -> Icons.Default.Nightlight
        RowIcon.TOMORROW -> Icons.Default.Today
        RowIcon.DAYS -> Icons.Default.DateRange
        RowIcon.WEEKDAY -> Icons.AutoMirrored.Filled.NextWeek
        RowIcon.MONTHLY -> Icons.Default.Event
    }
    is MenuRow.Last -> Icons.Default.History
    is MenuRow.Custom -> Icons.Default.CalendarMonth
}

@Preview(showBackground = true, name = "Empty - no history yet")
@Composable
private fun SnoozeBottomSheetPreview_Empty() {
    val now = LocalDateTime.of(2024, 1, 15, 6, 0)
        .atZone(ZoneId.systemDefault()).toInstant()
    LatrTheme {
        SnoozeOptionsPreviewContent(now = now, partitions = emptyList())
    }
}

@Preview(showBackground = true, name = "With a learned daily habit")
@Composable
private fun SnoozeBottomSheetPreview_LearnedHabit() {
    val zone = ZoneId.systemDefault()
    var stats = io.hafa.latr.util.SnoozeStatsSnapshot()
    var day = LocalDateTime.of(2024, 1, 1, 10, 0)
    repeat(20) {
        val at = day.atZone(zone).toInstant()
        val target = day.plusDays(1).withHour(8).withMinute(0).atZone(zone).toInstant()
        val result = SnoozeSuggestions.commit(stats, at.toEpochMilli(), target.toEpochMilli(), zone, source = "suggestion")
        stats = result.next
        day = day.plusDays(1)
    }
    val now = day.atZone(zone).toInstant()
    LatrTheme {
        SnoozeOptionsPreviewContent(now = now, partitions = listOf(stats))
    }
}

@Composable
private fun SnoozeOptionsPreviewContent(
    now: Instant,
    partitions: List<SnoozeStatsSnapshot>,
) {
    val zone = ZoneId.systemDefault()
    val rows = buildMenuRows(partitions, now, zone)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        rows.forEach { row ->
            MenuRowItem(row = row, now = now, zone = zone, onClick = {})
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, name = "Date Picker Sheet")
@Composable
private fun DatePickerSheetPreview() {
    val zone = ZoneId.systemDefault()
    val now = Instant.now()
    LatrTheme {
        DatePickerSheetContent(
            quickTimes = listOf(QuickTime(9 * 60, 3.0), QuickTime(21 * 60 + 30, 1.0)),
            now = now,
            zone = zone,
            onDateSelected = {},
            onQuickTime = {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, name = "Time Picker Sheet")
@Composable
private fun TimePickerSheetPreview() {
    LatrTheme {
        TimePickerSheetContent(
            selectedDate = LocalDate.now().plusDays(1),
            quickTimes = emptyList(),
            now = Instant.now(),
            zone = ZoneId.systemDefault(),
            onDismiss = {},
            onConfirm = {}
        )
    }
}
