package io.hafa.latr.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.NextWeek
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

private enum class SheetStep { MENU, DATE, TIME }

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
    todoText: String = "",
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
    var step by remember { mutableStateOf(SheetStep.MENU) }
    val today = LocalDate.ofInstant(now, zone)
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = PickerDates.initialPickerMillis(
            SnoozeSuggestions.defaultCustomDate(LocalDateTime.ofInstant(now, zone))
        ),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                !PickerDates.pickerMillisToDate(utcTimeMillis).isBefore(LocalDate.now(zone))
        }
    )

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
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier
    ) {
        BackHandler(enabled = step != SheetStep.MENU) {
            step = if (step == SheetStep.TIME) SheetStep.DATE else SheetStep.MENU
        }
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
            },
            label = "snoozeStep",
        ) { current ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                when (current) {
                    SheetStep.MENU -> MenuStep(
                        rows = rows,
                        todoText = todoText,
                        now = now,
                        zone = zone,
                        onRow = { row ->
                            when (row) {
                                is MenuRow.Custom -> step = SheetStep.DATE
                                is MenuRow.Suggestion -> pick(row.row.epochMillis, "suggestion", row.row.key)
                                is MenuRow.Last -> pick(row.epochMillis, "last", null)
                            }
                        },
                    )

                    SheetStep.DATE -> DateStep(
                        datePickerState = datePickerState,
                        todoText = todoText,
                        quickTimes = quickTimes,
                        now = now,
                        zone = zone,
                        onBack = { step = SheetStep.MENU },
                        onQuickTime = { epochMillis -> pick(epochMillis, "custom", null) },
                        onOtherTime = { step = SheetStep.TIME },
                    )

                    SheetStep.TIME -> {
                        val selectedDate = datePickerState.selectedDateMillis
                            ?.let { PickerDates.pickerMillisToDate(it) } ?: today
                        TimeStep(
                            selectedDate = selectedDate,
                            todoText = todoText,
                            quickTimes = quickTimes,
                            now = now,
                            zone = zone,
                            onBack = { step = SheetStep.DATE },
                            onConfirm = { epochMillis -> pick(epochMillis, "custom", null) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(title: String, todoText: String, onBack: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (onBack == null) 24.dp else 8.dp, end = 24.dp, top = 4.dp, bottom = 8.dp)
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (todoText.isNotBlank()) {
                Text(
                    todoText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MenuStep(
    rows: List<MenuRow>,
    todoText: String,
    now: Instant,
    zone: ZoneId,
    onRow: (MenuRow) -> Unit,
) {
    SheetHeader(title = "Snooze", todoText = todoText)
    rows.forEach { row ->
        MenuRowItem(row = row, now = now, zone = zone, onClick = { onRow(row) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateStep(
    datePickerState: DatePickerState,
    todoText: String,
    quickTimes: List<QuickTime>,
    now: Instant,
    zone: ZoneId,
    onBack: () -> Unit,
    onQuickTime: (epochMillis: Long) -> Unit,
    onOtherTime: () -> Unit,
) {
    val selectedDate = datePickerState.selectedDateMillis?.let { PickerDates.pickerMillisToDate(it) }
    SheetHeader(title = "Pick a date", todoText = todoText, onBack = onBack)
    DatePicker(state = datePickerState, showModeToggle = false, title = null, headline = null)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        quickTimes.forEach { quickTime ->
            val epoch = selectedDate?.let { SnoozeSuggestions.quickTimeEpoch(it, quickTime.clockMinutes, zone) }
            OutlinedButton(
                onClick = { epoch?.let(onQuickTime) },
                enabled = epoch != null && epoch > now.toEpochMilli(),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    LocalTime.of(quickTime.clockMinutes / 60, quickTime.clockMinutes % 60).toString(),
                    style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                )
            }
        }
        if (quickTimes.isEmpty()) Box(Modifier.weight(1f))
        TextButton(onClick = onOtherTime, enabled = selectedDate != null) {
            Text("Other time")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeStep(
    selectedDate: LocalDate,
    todoText: String,
    quickTimes: List<QuickTime>,
    now: Instant,
    zone: ZoneId,
    onBack: () -> Unit,
    onConfirm: (epochMillis: Long) -> Unit,
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

    SheetHeader(title = "Pick a time", todoText = todoText, onBack = onBack)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        TimePicker(state = timePickerState)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = { onConfirm(selectedEpoch) },
                enabled = selectedEpoch > now.toEpochMilli()
            ) { Text("Snooze") }
        }
    }
}

@Composable
private fun MenuRowItem(
    row: MenuRow,
    now: Instant,
    zone: ZoneId,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val (label, time) = when (row) {
        is MenuRow.Suggestion ->
            SnoozeSuggestions.labelText(row.row.key, row.row.epochMillis, now, zone) to
                SnoozeSuggestions.formatClock(row.row.epochMillis, zone)
        is MenuRow.Last -> SnoozeSuggestions.lastText(row.epochMillis, zone) to
            SnoozeSuggestions.formatClock(row.epochMillis, zone)
        is MenuRow.Custom -> "Pick a date & time" to null
    }
    val (tile, ink) = when (row) {
        is MenuRow.Suggestion -> colorScheme.tertiaryContainer to colorScheme.onTertiaryContainer
        else -> colorScheme.surfaceContainerHigh to colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .background(tile, CircleShape)
        ) {
            Icon(row.icon(now, zone), contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (time != null) {
            Text(
                time,
                style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                color = colorScheme.onSurfaceVariant,
            )
        } else {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun MenuRow.icon(now: Instant, zone: ZoneId): ImageVector = when (this) {
    is MenuRow.Suggestion -> when (SnoozeSuggestions.rowIcon(row.key, row.epochMillis, now, zone)) {
        RowIcon.OFFSET -> Icons.Rounded.Schedule
        RowIcon.TODAY_DAY -> Icons.Rounded.LightMode
        RowIcon.TODAY_NIGHT -> Icons.Rounded.Nightlight
        RowIcon.TOMORROW -> Icons.Rounded.Today
        RowIcon.DAYS -> Icons.Rounded.DateRange
        RowIcon.WEEKDAY -> Icons.AutoMirrored.Rounded.NextWeek
        RowIcon.MONTHLY -> Icons.Rounded.Event
    }
    is MenuRow.Last -> Icons.Rounded.History
    is MenuRow.Custom -> Icons.Rounded.CalendarMonth
}

@Preview(showBackground = true, name = "Empty - no history yet")
@Composable
private fun SnoozeBottomSheetPreview_Empty() {
    val now = LocalDateTime.of(2024, 1, 15, 6, 0)
        .atZone(ZoneId.systemDefault()).toInstant()
    LatrTheme(dynamicColor = false) {
        SnoozeOptionsPreviewContent(now = now, partitions = emptyList())
    }
}

@Preview(showBackground = true, name = "With a learned daily habit")
@Composable
private fun SnoozeBottomSheetPreview_LearnedHabit() {
    val zone = ZoneId.systemDefault()
    var stats = SnoozeStatsSnapshot()
    var day = LocalDateTime.of(2024, 1, 1, 10, 0)
    repeat(20) {
        val at = day.atZone(zone).toInstant()
        val target = day.plusDays(1).withHour(8).withMinute(0).atZone(zone).toInstant()
        val result = SnoozeSuggestions.commit(stats, at.toEpochMilli(), target.toEpochMilli(), zone, source = "suggestion")
        stats = result.next
        day = day.plusDays(1)
    }
    val now = day.atZone(zone).toInstant()
    LatrTheme(dynamicColor = false) {
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
            .background(Color.Transparent)
            .padding(vertical = 16.dp)
    ) {
        MenuStep(rows = rows, todoText = "Email Sam about the lease renewal", now = now, zone = zone, onRow = {})
    }
}
