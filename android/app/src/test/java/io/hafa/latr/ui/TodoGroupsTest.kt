package io.hafa.latr.ui

import io.hafa.latr.data.Todo
import io.hafa.latr.data.TodoState
import io.hafa.latr.util.LocalDateTimeUtil
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TodoGroupsTest {
    private val zone = ZoneId.of("America/New_York")
    private val now = LocalDateTime.of(2026, 4, 15, 12, 0).atZone(zone).toInstant().toEpochMilli()
    private val day = 24L * 60 * 60 * 1000
    private val hour = 60L * 60 * 1000

    private fun iso(epoch: Long) = LocalDateTimeUtil.fromEpochMillis(epoch, zone)

    private fun byLabel(groups: List<TodoGroup>) = groups.associate { g -> g.label to g.todos.map { it.id } }

    @Test
    fun `active keys date buckets on unsnooze time, not modifiedAt`() {
        val groups = groupForFilter(
            listOf(Todo(id = "unsnoozed", modifiedAt = now - 30 * day, snoozeUntil = iso(now - hour))),
            StatusFilter.ACTIVE, now, zone,
        )
        assertEquals(listOf("unsnoozed"), byLabel(groups)["Today"])
    }

    @Test
    fun `active plain rows bucket by modifiedAt`() {
        val groups = groupForFilter(
            listOf(Todo(id = "today", modifiedAt = now), Todo(id = "ancient", modifiedAt = now - 30 * day)),
            StatusFilter.ACTIVE, now, zone,
        )
        assertEquals(listOf("today"), byLabel(groups)["Today"])
        assertEquals(listOf("ancient"), byLabel(groups)["Earlier"])
    }

    @Test
    fun `pinned rows form a top bucket and are excluded from date buckets`() {
        val groups = groupForFilter(
            listOf(
                Todo(id = "pin", pinned = true, modifiedAt = now - 30 * day),
                Todo(id = "plain", modifiedAt = now),
            ),
            StatusFilter.ACTIVE, now, zone,
        )
        assertEquals("Pinned", groups[0].label)
        assertEquals(listOf("pin"), groups[0].todos.map { it.id })
        assertEquals(listOf("plain"), byLabel(groups)["Today"])
        assertNull(byLabel(groups)["Earlier"])
    }

    @Test
    fun `no Pinned bucket when nothing is pinned`() {
        val groups = groupForFilter(listOf(Todo(id = "a", modifiedAt = now)), StatusFilter.ACTIVE, now, zone)
        assertEquals(listOf("Today"), groups.map { it.label })
    }

    @Test
    fun `yesterday and this week buckets`() {
        val groups = groupForFilter(
            listOf(Todo(id = "y", modifiedAt = now - day), Todo(id = "w", modifiedAt = now - 5 * day)),
            StatusFilter.ACTIVE, now, zone,
        )
        assertEquals(listOf("Yesterday", "This week"), groups.map { it.label })
    }

    @Test
    fun `done buckets by modifiedAt and ignores pinned`() {
        val groups = groupForFilter(
            listOf(
                Todo(id = "pin-old", pinned = true, state = TodoState.DONE, modifiedAt = now - 30 * day),
                Todo(id = "today", state = TodoState.DONE, modifiedAt = now),
            ),
            StatusFilter.DONE, now, zone,
        )
        assertEquals(listOf("today"), byLabel(groups)["Today"])
        assertEquals(listOf("pin-old"), byLabel(groups)["Earlier"])
        assertFalse(groups.any { it.label == "Pinned" })
    }

    @Test
    fun `snoozed buckets by local-day threshold of snoozeUntil`() {
        val groups = groupForFilter(
            listOf(
                Todo(id = "later-today", snoozeUntil = iso(now + 4 * hour)),
                Todo(id = "tomorrow", snoozeUntil = iso(now + day)),
                Todo(id = "thisweek", snoozeUntil = iso(now + 3 * day)),
                Todo(id = "far", snoozeUntil = iso(now + 30 * day)),
            ),
            StatusFilter.SNOOZED, now, zone,
        )
        val labels = byLabel(groups)
        assertEquals(listOf("later-today"), labels["Later today"])
        assertEquals(listOf("tomorrow"), labels["Tomorrow"])
        assertEquals(listOf("thisweek"), labels["This week"])
        assertEquals(listOf("far"), labels["Later"])
    }
}
