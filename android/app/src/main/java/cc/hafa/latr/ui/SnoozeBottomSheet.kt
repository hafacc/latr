package cc.hafa.latr.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import cc.hafa.latr.data.localIso
import cc.hafa.latr.util.Repeat
import cc.hafa.latr.util.RepeatUnit
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.hafa.latr.ui.theme.LatrTheme
import cc.hafa.latr.util.LocalDateTimeUtil
import cc.hafa.latr.util.PickerDates
import cc.hafa.latr.util.QuickTime
import cc.hafa.latr.util.RowIcon
import cc.hafa.latr.util.SnoozeRow
import cc.hafa.latr.util.SnoozeStatsSnapshot
import cc.hafa.latr.util.SnoozeSuggestions
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

private enum class SheetStep { MENU, DATE, TIME, REPEAT, REPEAT_TIME }

/** One rendered menu row: a learned suggestion, the persisted "Last" custom pick, or "Custom". */
private sealed class MenuRow {
    data class Suggestion(val row: SnoozeRow) : MenuRow()
    data class Last(val epochMillis: Long) : MenuRow()
    data object Custom : MenuRow()
    data object Repeat : MenuRow()
}

/** The repeat editor's unsaved choices. */
private data class RepeatDraft(
    val every: Int,
    val unit: RepeatUnit,
    val weekday: Int,
    val monthDay: Int,
    val at: String,
)

private val WEEKDAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

private fun clockText(clockMinutes: Int): String = LocalTime.of(clockMinutes / 60, clockMinutes % 60).toString()

private fun initialDraft(repeat: Repeat?, today: LocalDate, quickTimes: List<QuickTime>): RepeatDraft =
    if (repeat != null) {
        RepeatDraft(repeat.every, repeat.unit, repeat.weekday, repeat.monthDay, repeat.at)
    } else {
        RepeatDraft(
            every = 1,
            unit = RepeatUnit.WEEK,
            weekday = today.dayOfWeek.value,
            monthDay = today.dayOfMonth,
            at = quickTimes.maxByOrNull { it.weight }?.let { clockText(it.clockMinutes) } ?: "09:00",
        )
    }

private val MENU_CLOCK_TICK = 30.seconds

private fun buildMenuRows(
    partitions: List<SnoozeStatsSnapshot>,
    deep: List<SnoozeRow>,
    now: Instant,
): List<MenuRow> {
    val suggestions = SnoozeSuggestions.shownRows(deep)
    val rows = mutableListOf<MenuRow>()
    rows += suggestions.map { MenuRow.Suggestion(it) }
    SnoozeSuggestions.lastRow(partitions, now, suggestions)?.let { rows += MenuRow.Last(it) }
    rows += MenuRow.Custom
    rows += MenuRow.Repeat
    return rows
}

/** [onSnoozeSelected] returns false if the pick was rejected (its time already passed); the sheet then stays open with refreshed rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnoozeBottomSheet(
    onDismiss: () -> Unit,
    onSnoozeSelected: (isoDateTime: String, source: String, pickedKey: String?, pickLogKey: String) -> Boolean,
    onRepeatSaved: (Repeat) -> Unit,
    partitions: List<SnoozeStatsSnapshot>,
    modifier: Modifier = Modifier,
    todoText: String = "",
    repeat: Repeat? = null,
    initialNow: Instant? = null,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var now by remember { mutableStateOf(initialNow ?: Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(MENU_CLOCK_TICK)
            now = Instant.now()
        }
    }
    val deep = remember(now, partitions, zone) { SnoozeSuggestions.rankDeep(partitions, now, zone) }
    val rows = remember(deep, partitions, now) { buildMenuRows(partitions, deep, now) }
    val quickTimes = remember(now, partitions) { SnoozeSuggestions.quickTimes(partitions, now) }
    var step by remember { mutableStateOf(SheetStep.MENU) }
    val today = LocalDate.ofInstant(now, zone)
    var draft by remember { mutableStateOf(initialDraft(repeat, today, quickTimes)) }
    val saveRepeat = { at: String ->
        val nowIso = localIso(System.currentTimeMillis(), zone)
        onRepeatSaved(Repeat.anchored(draft.every, draft.unit, draft.weekday, draft.monthDay, at, nowIso))
        onDismiss()
    }
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
        val pickLogKey = SnoozeSuggestions.pickLogKey(deep, epochMillis)
        if (onSnoozeSelected(LocalDateTimeUtil.fromEpochMillis(epochMillis, zone), source, pickedKey, pickLogKey)) {
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
            step = when (step) {
                SheetStep.TIME -> SheetStep.DATE
                SheetStep.REPEAT_TIME -> SheetStep.REPEAT
                else -> SheetStep.MENU
            }
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
                        repeat = repeat,
                        now = now,
                        zone = zone,
                        onRow = { row ->
                            when (row) {
                                is MenuRow.Custom -> step = SheetStep.DATE
                                is MenuRow.Repeat -> step = SheetStep.REPEAT
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

                    SheetStep.REPEAT -> RepeatStep(
                        draft = draft,
                        onDraftChange = { draft = it },
                        todoText = todoText,
                        quickTimes = quickTimes,
                        onBack = { step = SheetStep.MENU },
                        onQuickTime = { at -> saveRepeat(at) },
                        onOtherTime = { step = SheetStep.REPEAT_TIME },
                    )

                    SheetStep.REPEAT_TIME -> RepeatTimeStep(
                        at = draft.at,
                        todoText = todoText,
                        onBack = { step = SheetStep.REPEAT },
                        onSave = { at -> saveRepeat(at) },
                    )
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
                val color = MaterialTheme.colorScheme.onSurfaceVariant
                val tagColor = MaterialTheme.colorScheme.primary
                var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
                Text(
                    remember(todoText, color, tagColor) { styledTodoText(todoText, color, tagColor) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { layout = it },
                    modifier = Modifier.tagChips(todoText, { layout }, tagColor),
                )
            }
        }
    }
}

@Composable
private fun MenuStep(
    rows: List<MenuRow>,
    todoText: String,
    repeat: Repeat?,
    now: Instant,
    zone: ZoneId,
    onRow: (MenuRow) -> Unit,
) {
    SheetHeader(title = "Snooze", todoText = todoText)
    rows.forEach { row ->
        MenuRowItem(row = row, repeat = repeat, now = now, zone = zone, onClick = { onRow(row) })
    }
}

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
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier.weight(1f),
            ) {
                val timeStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum")
                Text(
                    LocalTime.of(quickTime.clockMinutes / 60, quickTime.clockMinutes % 60).toString(),
                    style = timeStyle,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = timeStyle.fontSize),
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
private fun RepeatStep(
    draft: RepeatDraft,
    onDraftChange: (RepeatDraft) -> Unit,
    todoText: String,
    quickTimes: List<QuickTime>,
    onBack: () -> Unit,
    onQuickTime: (at: String) -> Unit,
    onOtherTime: () -> Unit,
) {
    SheetHeader(title = "Repeat", todoText = todoText, onBack = onBack)
    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        RepeatSection(label = "Every") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                (1..7).forEach { count ->
                    ChoiceButton(
                        label = count.toString(),
                        selected = draft.every == count,
                        onClick = { onDraftChange(draft.copy(every = count)) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                    )
                }
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                RepeatUnit.entries.forEachIndexed { index, unit ->
                    SegmentedButton(
                        selected = draft.unit == unit,
                        onClick = { onDraftChange(draft.copy(unit = unit)) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = RepeatUnit.entries.size),
                    ) {
                        Text(if (draft.every > 1) "${unit.wire}s" else unit.wire)
                    }
                }
            }
        }
        when (draft.unit) {
            RepeatUnit.WEEK -> RepeatSection(label = "On") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    WEEKDAY_NAMES.forEachIndexed { index, name ->
                        val isoDay = index + 1
                        ChoiceButton(
                            label = name.take(1),
                            description = name,
                            selected = isoDay == draft.weekday,
                            onClick = { onDraftChange(draft.copy(weekday = isoDay)) },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                        )
                    }
                }
            }

            RepeatUnit.MONTH -> RepeatSection(label = "On") {
                val rows = (1..28).chunked(7) + listOf(listOf(29, 30, 31, Repeat.LAST_DAY))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { day ->
                                val last = day == Repeat.LAST_DAY
                                ChoiceButton(
                                    label = if (last) "Last day" else day.toString(),
                                    selected = draft.monthDay == day,
                                    bordered = last,
                                    onClick = { onDraftChange(draft.copy(monthDay = day)) },
                                    modifier = Modifier
                                        .weight(if (last) 4f else 1f)
                                        .height(40.dp),
                                )
                            }
                        }
                    }
                }
            }

            RepeatUnit.DAY -> Unit
        }
        RepeatSection(label = "At") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                quickTimes.forEach { quickTime ->
                    val at = clockText(quickTime.clockMinutes)
                    OutlinedButton(
                        onClick = { onQuickTime(at) },
                        shape = MaterialTheme.shapes.small,
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        val timeStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum")
                        Text(
                            at,
                            style = timeStyle,
                            maxLines = 1,
                            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = timeStyle.fontSize),
                        )
                    }
                }
                if (quickTimes.isEmpty()) Box(Modifier.weight(1f))
                TextButton(onClick = onOtherTime) {
                    Text("Other time")
                }
            }
        }
    }
}

@Composable
private fun RepeatSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun ChoiceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    bordered: Boolean = true,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        selected = selected,
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) colorScheme.primary else Color.Transparent,
        contentColor = if (selected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
        border = if (selected || !bordered) null else BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = modifier.semantics { if (description != null) contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepeatTimeStep(
    at: String,
    todoText: String,
    onBack: () -> Unit,
    onSave: (at: String) -> Unit,
) {
    val initialTime = remember(at) { LocalTime.parse(at) }
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true
    )
    SheetHeader(title = "Pick a time", todoText = todoText, onBack = onBack)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        TimePicker(state = timePickerState)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = { onSave(LocalTime.of(timePickerState.hour, timePickerState.minute).toString()) }) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun MenuRowItem(
    row: MenuRow,
    repeat: Repeat?,
    now: Instant,
    zone: ZoneId,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val (label, time) = when (row) {
        is MenuRow.Suggestion ->
            SnoozeSuggestions.labelText(row.row.key, row.row.epochMillis, now, zone) to
                SnoozeSuggestions.formatClock(row.row.epochMillis, zone)
        is MenuRow.Last -> SnoozeSuggestions.lastText(row.epochMillis, now, zone) to
            SnoozeSuggestions.formatClock(row.epochMillis, zone)
        is MenuRow.Custom -> "Pick a date & time" to null
        is MenuRow.Repeat -> (repeat?.text() ?: "Repeat…") to null
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
    is MenuRow.Repeat -> Icons.Rounded.Repeat
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
    val rows = buildMenuRows(partitions, SnoozeSuggestions.rankDeep(partitions, now, zone), now)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(vertical = 16.dp)
    ) {
        MenuStep(rows = rows, todoText = "Email Sam about the lease renewal", repeat = null, now = now, zone = zone, onRow = {})
    }
}
