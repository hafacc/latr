package cc.hafa.latr.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import cc.hafa.latr.util.Position
import cc.hafa.latr.util.Repeat
import java.util.UUID

enum class TodoState { ACTIVE, DONE }

@Entity(tableName = "todos")
data class Todo(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val serverModifiedAt: Timestamp? = null,
    val state: TodoState = TodoState.ACTIVE,
    val snoozeUntil: String? = null,
    val pinned: Boolean = false,
    /** Where a move put the row; null orders it by its time. */
    val position: String? = null,
    val deleted: Boolean = false,
    /** How often the todo comes back once done; null for a one-off. */
    val repeat: Repeat? = null,
    /** Whether marking this todo done has already made its next copy. */
    val repeatSpawned: Boolean = false,
    /** The scheduled local time this copy of a repeating todo stands for. */
    val occurrence: String? = null,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "text" to text,
        "createdAt" to createdAt,
        "modifiedAt" to modifiedAt,
        "serverModifiedAt" to FieldValue.serverTimestamp(),
        "state" to state.name,
        "snoozeUntil" to snoozeUntil,
        "pinned" to pinned,
        "position" to position,
        "deleted" to deleted,
        "repeat" to repeat?.toMap(),
        "repeatSpawned" to repeatSpawned,
        "occurrence" to occurrence,
    )

    companion object {
        /** Legacy rows carry state "SNOOZED"; snoozeUntil is the only snooze marker now. */
        fun readState(value: String?): TodoState =
            if (value == TodoState.DONE.name) TodoState.DONE else TodoState.ACTIVE

        fun fromMap(id: String, data: Map<String, Any?>): Todo {
            val modifiedAt = data["modifiedAt"] as? Long ?: System.currentTimeMillis()
            return Todo(
                id = id,
                text = data["text"] as? String ?: "",
                createdAt = data["createdAt"] as? Long ?: System.currentTimeMillis(),
                modifiedAt = modifiedAt,
                serverModifiedAt = data["serverModifiedAt"] as? Timestamp,
                state = readState(data["state"] as? String),
                snoozeUntil = data["snoozeUntil"] as? String,
                pinned = data["pinned"] as? Boolean ?: false,
                position = Position.readPosition(data["position"]),
                deleted = data["deleted"] as? Boolean ?: false,
                repeat = Repeat.read(data["repeat"]),
                repeatSpawned = data["repeatSpawned"] as? Boolean ?: false,
                occurrence = data["occurrence"] as? String,
            )
        }
    }
}
