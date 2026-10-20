package cc.hafa.latr.data

import cc.hafa.latr.util.Repeat
import cc.hafa.latr.util.RepeatUnit
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

class TodoMapTest {

    @Test
    fun `toMap emits every client-controlled field and serverTimestamp sentinel`() {
        val todo = Todo(
            id = "abc",
            text = "buy milk",
            createdAt = 1_700_000_000_000L,
            modifiedAt = 1_700_000_001_000L,
            // serverModifiedAt is in-memory only; toMap always stamps the sentinel.
            serverModifiedAt = null,
            state = TodoState.ACTIVE,
            snoozeUntil = "2026-04-24T09:00:00",
            pinned = true,
            deleted = false,
        )
        val map = todo.toMap()
        // Exactly the keys we expect on the wire (no id, serverModifiedAt is a
        // Firestore sentinel object — presence matters, value is opaque).
        assertEquals(
            setOf(
                "text", "createdAt", "modifiedAt", "serverModifiedAt",
                "state", "snoozeUntil", "pinned", "position", "deleted",
                "repeat", "repeatSpawned", "occurrence",
            ),
            map.keys,
        )
        assertEquals("buy milk", map["text"])
        assertEquals(1_700_000_000_000L, map["createdAt"])
        assertEquals(1_700_000_001_000L, map["modifiedAt"])
        assertEquals("ACTIVE", map["state"])
        assertEquals("2026-04-24T09:00:00", map["snoozeUntil"])
        assertEquals(true, map["pinned"])
        assertEquals(false, map["deleted"])
        assertNotNull(map["serverModifiedAt"])
    }

    @Test
    fun `fromMap reads a well-formed document`() {
        val ts = Timestamp(Date(333L))
        val data = mapOf<String, Any?>(
            "text" to "ship release",
            "createdAt" to 111L,
            "modifiedAt" to 222L,
            "serverModifiedAt" to ts,
            "state" to "DONE",
            "snoozeUntil" to null,
            "pinned" to true,
            "deleted" to false,
        )
        val todo = Todo.fromMap("id-1", data)
        assertEquals("id-1", todo.id)
        assertEquals("ship release", todo.text)
        assertEquals(111L, todo.createdAt)
        assertEquals(222L, todo.modifiedAt)
        assertEquals(ts, todo.serverModifiedAt)
        assertEquals(TodoState.DONE, todo.state)
        assertNull(todo.snoozeUntil)
        assertEquals(true, todo.pinned)
        assertEquals(false, todo.deleted)
    }

    @Test
    fun `fromMap preserves a Firestore Timestamp losslessly`() {
        val ts = Timestamp(12L, 345_678_000)
        val todo = Todo.fromMap("id", mapOf("serverModifiedAt" to ts, "modifiedAt" to 1L))
        assertEquals(ts, todo.serverModifiedAt)
        assertEquals(12L, todo.serverModifiedAt?.seconds)
        assertEquals(345_678_000, todo.serverModifiedAt?.nanoseconds)
    }

    @Test
    fun `fromMap nulls serverModifiedAt when missing`() {
        val todo = Todo.fromMap("id", mapOf("modifiedAt" to 777L))
        assertNull(todo.serverModifiedAt)
    }

    @Test
    fun `fromMap nulls serverModifiedAt when not a Timestamp (legacy Long)`() {
        // Older clients wrote Long millis here. They're well-formed for the
        // prior schema but ignored under the new contract — the basis we'd
        // resend has no fidelity, and fresh writes re-stamp it.
        val todo = Todo.fromMap("id", mapOf("serverModifiedAt" to 999L))
        assertNull(todo.serverModifiedAt)
    }

    @Test
    fun `fromMap defaults unknown state to ACTIVE`() {
        val todo = Todo.fromMap("id", mapOf("state" to "BOGUS"))
        assertEquals(TodoState.ACTIVE, todo.state)
    }

    @Test
    fun `fromMap reads a legacy SNOOZED row as ACTIVE, keeping its snooze time`() {
        val todo = Todo.fromMap(
            "id",
            mapOf("state" to "SNOOZED", "snoozeUntil" to "2026-04-24T09:00:00"),
        )
        assertEquals(TodoState.ACTIVE, todo.state)
        assertEquals("2026-04-24T09:00:00", todo.snoozeUntil)
    }

    @Test
    fun `fromMap tolerates an entirely empty document`() {
        val todo = Todo.fromMap("id", emptyMap())
        assertEquals("", todo.text)
        assertEquals(TodoState.ACTIVE, todo.state)
        assertNull(todo.snoozeUntil)
        assertEquals(false, todo.pinned)
        assertEquals(false, todo.deleted)
    }

    @Test
    fun `fromMap treats deleted only as boolean true`() {
        assertEquals(true, Todo.fromMap("id", mapOf("deleted" to true)).deleted)
        assertEquals(false, Todo.fromMap("id", mapOf("deleted" to "true")).deleted)
        assertEquals(false, Todo.fromMap("id", mapOf("deleted" to 1)).deleted)
    }

    @Test
    fun `position round-trips, and one this build can't order by reads as none`() {
        val position = "1700000000000abcz"
        assertEquals(position, Todo(position = position).toMap()["position"])
        assertEquals(position, Todo.fromMap("id", mapOf("position" to position)).position)
        assertNull(Todo.fromMap("id", mapOf("position" to "not a position")).position)
        assertNull(Todo.fromMap("id", emptyMap()).position)
    }

    @Test
    fun `repeat fields round-trip through the Firestore map, Room and a malformed rule reads as none`() {
        val rule = Repeat(1, RepeatUnit.WEEK, 3, 15, "07:00", "2026-10-19")
        val todo = Todo(repeat = rule, repeatSpawned = true, occurrence = "2026-10-21T07:00:00")
        val map = todo.toMap()
        // Firestore hands numbers back as Long.
        val wire = map.mapValues { (key, value) ->
            if (key == "repeat") (value as Map<*, *>).mapValues { (_, field) ->
                when (field) {
                    is Int -> field.toLong()
                    is List<*> -> field.map { (it as Int).toLong() }
                    else -> field
                }
            } else value
        }
        val read = Todo.fromMap("id", wire)
        assertEquals(rule, read.repeat)
        assertEquals(true, read.repeatSpawned)
        assertEquals("2026-10-21T07:00:00", read.occurrence)

        val converters = Converters()
        assertEquals(rule, converters.toRepeat(converters.fromRepeat(rule)))
        assertNull(converters.toRepeat(converters.fromRepeat(null)))

        val empty = Todo.fromMap("id", mapOf("repeat" to mapOf("every" to 0L)))
        assertNull(empty.repeat)
        assertEquals(false, empty.repeatSpawned)
        assertNull(empty.occurrence)
    }
}
