package cc.hafa.latr.data

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/** What marking a todo done writes: the done todo, and the next copy when it repeats. */
data class Completion(val done: Todo, val spawned: Todo?)

private val LOCAL_ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

/** [epochMillis] as a local `YYYY-MM-DDTHH:mm:ss`, the format of `snoozeUntil` and `occurrence`. */
fun localIso(epochMillis: Long, zone: ZoneId): String =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone).format(LOCAL_ISO)

/** Mark done at [nowMillis]; a repeating todo that hasn't made its next copy yet makes it now, snoozed to the next occurrence. */
fun Todo.completed(
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    newId: String = UUID.randomUUID().toString(),
): Completion {
    val rule = repeat
    val spawns = rule != null && !repeatSpawned
    val done = copy(
        state = TodoState.DONE,
        snoozeUntil = null,
        modifiedAt = nowMillis,
        position = null,
        repeatSpawned = repeatSpawned || spawns,
    )
    val spawned = if (rule != null && spawns) {
        val nowIso = localIso(nowMillis, zone)
        val after = maxOf(occurrence ?: "", nowIso)
        val next = rule.nextOccurrence(after)
        Todo(
            id = newId,
            text = text,
            createdAt = nowMillis,
            modifiedAt = nowMillis,
            state = TodoState.ACTIVE,
            snoozeUntil = next,
            pinned = pinned,
            position = null,
            deleted = false,
            repeat = rule,
            repeatSpawned = false,
            occurrence = next,
        )
    } else {
        null
    }
    return Completion(done, spawned)
}

/** Back to active from done or snoozed; unsnoozing by hand also ends a repeat, while a restored done todo keeps it. */
fun Todo.restored(snoozed: Boolean): Todo =
    if (snoozed && state != TodoState.DONE) {
        copy(state = TodoState.ACTIVE, snoozeUntil = null, repeat = null, repeatSpawned = false, occurrence = null)
    } else {
        copy(state = TodoState.ACTIVE, snoozeUntil = null)
    }

/** Write a [Completion]: both todos in one batch when it made a copy. */
suspend fun TodoStore.write(completion: Completion) {
    val (done, spawned) = completion
    if (spawned == null) update(done)
    else writeAll(listOf(done, spawned))
}

/** Undo a completion: put [previous] back as it was and delete the copy it made, if any. */
suspend fun TodoStore.undoCompletion(previous: Todo, spawned: Todo?) {
    update(previous)
    if (spawned != null) delete(spawned)
}
