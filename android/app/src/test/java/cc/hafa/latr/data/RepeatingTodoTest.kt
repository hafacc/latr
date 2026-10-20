package cc.hafa.latr.data

import cc.hafa.latr.util.Repeat
import cc.hafa.latr.util.RepeatUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class RepeatingTodoTest {
    private val zone = ZoneOffset.UTC
    private val mondays = Repeat(1, RepeatUnit.WEEK, 1, 1, "07:00", "2026-10-19")

    private fun millis(iso: String): Long = LocalDateTime.parse(iso).toInstant(zone).toEpochMilli()

    private class MemoryStore : TodoStore {
        val rows = linkedMapOf<String, Todo>()
        override fun observeAll(): Flow<List<Todo>> = MutableStateFlow(rows.values.toList())
        override suspend fun snapshot(): List<Todo> = rows.values.filter { !it.deleted }
        override suspend fun insert(todo: Todo) { rows[todo.id] = todo }
        override suspend fun update(todo: Todo) { rows[todo.id] = todo }
        override suspend fun writeAll(todos: List<Todo>) { todos.forEach { rows[it.id] = it } }
        override suspend fun delete(todo: Todo) { rows[todo.id] = todo.copy(deleted = true) }
        override suspend fun clearAllDone(done: List<Todo>) = Unit
        override suspend fun restoreMany(todos: List<Todo>) = Unit
        override suspend fun deleteEmptyTodosExcept(exceptId: String) = Unit
    }

    @Test
    fun `done on a repeating todo makes the next copy`() {
        val now = millis("2026-10-21T10:00:00")
        val todo = Todo(id = "a", text = "Take out recycling", pinned = true, repeat = mondays, position = "p", snoozeUntil = "2026-10-20T07:00:00")
        val (done, spawned) = todo.completed(now, zone, newId = "b")

        assertEquals(TodoState.DONE, done.state)
        assertTrue(done.repeatSpawned)
        assertEquals(mondays, done.repeat)
        assertNull(done.snoozeUntil)
        assertNull(done.position)
        assertEquals(now, done.modifiedAt)

        assertNotNull(spawned)
        spawned!!
        assertEquals("b", spawned.id)
        assertEquals("Take out recycling", spawned.text)
        assertTrue(spawned.pinned)
        assertEquals(mondays, spawned.repeat)
        assertFalse(spawned.repeatSpawned)
        assertEquals("2026-10-26T07:00:00", spawned.occurrence)
        assertEquals("2026-10-26T07:00:00", spawned.snoozeUntil)
        assertEquals(TodoState.ACTIVE, spawned.state)
        assertEquals(now, spawned.createdAt)
        assertEquals(now, spawned.modifiedAt)
        assertNull(spawned.position)
        assertFalse(spawned.deleted)
    }

    @Test
    fun `the next copy counts from the later of the occurrence and now`() {
        val early = Todo(repeat = mondays, occurrence = "2026-10-26T07:00:00")
        val spawned = early.completed(millis("2026-10-21T10:00:00"), zone).spawned
        assertEquals("2026-11-02T07:00:00", spawned?.occurrence)
    }

    @Test
    fun `done makes nothing once the copy exists, or without a repeat`() {
        val now = millis("2026-10-21T10:00:00")
        val (done, spawned) = Todo(repeat = mondays, repeatSpawned = true).completed(now, zone)
        assertNull(spawned)
        assertTrue(done.repeatSpawned)
        val plain = Todo().completed(now, zone)
        assertNull(plain.spawned)
        assertFalse(plain.done.repeatSpawned)
    }

    @Test
    fun `undo restores the todo and deletes its copy`() = runBlocking {
        val store = MemoryStore()
        val todo = Todo(id = "a", repeat = mondays)
        store.insert(todo)
        val completion = todo.completed(millis("2026-10-21T10:00:00"), zone, newId = "b")
        store.write(completion)
        assertTrue(store.rows.getValue("a").repeatSpawned)
        assertFalse(store.rows.getValue("b").deleted)

        store.undoCompletion(todo, completion.spawned)
        assertEquals(todo, store.rows.getValue("a"))
        assertFalse(store.rows.getValue("a").repeatSpawned)
        assertTrue(store.rows.getValue("b").deleted)
    }

    @Test
    fun `unsnoozing ends the repeat, restoring a done todo keeps it`() {
        val snoozed = Todo(repeat = mondays, occurrence = "2026-10-26T07:00:00", snoozeUntil = "2026-10-26T07:00:00")
        val unsnoozed = snoozed.restored(snoozed = true)
        assertEquals(TodoState.ACTIVE, unsnoozed.state)
        assertNull(unsnoozed.snoozeUntil)
        assertNull(unsnoozed.repeat)
        assertFalse(unsnoozed.repeatSpawned)
        assertNull(unsnoozed.occurrence)

        val done = Todo(state = TodoState.DONE, repeat = mondays, repeatSpawned = true)
        val restored = done.restored(snoozed = false)
        assertEquals(TodoState.ACTIVE, restored.state)
        assertEquals(mondays, restored.repeat)
        assertTrue(restored.repeatSpawned)
    }
}
