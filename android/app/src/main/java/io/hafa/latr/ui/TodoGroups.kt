package io.hafa.latr.ui

import io.hafa.latr.data.Todo
import io.hafa.latr.util.LocalDateTimeUtil
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One labelled run of rows; mirrors web's `utils/group.ts`. */
data class TodoGroup(val label: String, val todos: List<Todo>)

private fun Long.localDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

private fun activeSortKey(todo: Todo, zone: ZoneId): Long =
    todo.snoozeUntil?.let { LocalDateTimeUtil.toEpochMillis(it, zone) } ?: todo.modifiedAt

private fun groupByPastKey(todos: List<Todo>, keyFn: (Todo) -> Long, today: LocalDate, zone: ZoneId): List<TodoGroup> {
    val labels = listOf("Today", "Yesterday", "This week", "Earlier")
    val buckets = labels.map { mutableListOf<Todo>() }
    for (todo in todos) {
        val day = keyFn(todo).localDate(zone)
        val index = when {
            !day.isBefore(today) -> 0
            !day.isBefore(today.minusDays(1)) -> 1
            !day.isBefore(today.minusDays(6)) -> 2
            else -> 3
        }
        buckets[index] += todo
    }
    return labels.zip(buckets).filter { it.second.isNotEmpty() }.map { TodoGroup(it.first, it.second) }
}

private fun groupBySnoozeUntil(todos: List<Todo>, today: LocalDate, zone: ZoneId): List<TodoGroup> {
    val labels = listOf("Later today", "Tomorrow", "This week", "Later")
    val buckets = labels.map { mutableListOf<Todo>() }
    for (todo in todos) {
        val snoozeUntil = todo.snoozeUntil ?: continue
        val day = LocalDateTimeUtil.toEpochMillis(snoozeUntil, zone).localDate(zone)
        val index = when {
            day.isBefore(today.plusDays(1)) -> 0
            day.isBefore(today.plusDays(2)) -> 1
            day.isBefore(today.plusDays(7)) -> 2
            else -> 3
        }
        buckets[index] += todo
    }
    return labels.zip(buckets).filter { it.second.isNotEmpty() }.map { TodoGroup(it.first, it.second) }
}

/** Buckets an already-sorted list, preserving order within each bucket. */
fun groupForFilter(
    sorted: List<Todo>,
    filter: StatusFilter,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): List<TodoGroup> {
    val today = nowMillis.localDate(zone)
    return when (filter) {
        StatusFilter.SNOOZED -> groupBySnoozeUntil(sorted, today, zone)
        StatusFilter.ACTIVE -> {
            val (pinned, rest) = sorted.partition { it.pinned }
            val pinnedGroup = if (pinned.isEmpty()) emptyList() else listOf(TodoGroup("Pinned", pinned))
            pinnedGroup + groupByPastKey(rest, { activeSortKey(it, zone) }, today, zone)
        }
        StatusFilter.DONE, StatusFilter.ALL -> groupByPastKey(sorted, { it.modifiedAt }, today, zone)
    }
}
