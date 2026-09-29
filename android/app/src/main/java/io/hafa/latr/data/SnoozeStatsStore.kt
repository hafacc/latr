package io.hafa.latr.data

import android.util.Log
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import io.hafa.latr.ui.auth.AuthManager
import io.hafa.latr.util.CommitResult
import io.hafa.latr.util.LastCustom
import io.hafa.latr.util.SetStat
import io.hafa.latr.util.SnoozeStatsSnapshot
import io.hafa.latr.util.SnoozeSuggestions
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** This device's snooze-learning partition (Room), mirrored whole to `users/{uid}.snoozeStats.<deviceId>`; in-memory state is main-thread only. */
class SnoozeStatsStore(
    private val dao: SnoozeStatsDao,
    private val userPreferences: UserPreferences,
    private val authManager: AuthManager?,
    private val scope: CoroutineScope,
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val firestore: FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (_: Exception) {
        null
    }

    val deviceId: String get() = userPreferences.deviceId

    fun currentUidOrNull(): String? = authManager?.currentUser?.value?.uid

    private val _local = MutableStateFlow(SnoozeStatsSnapshot())
    private val _remote = MutableStateFlow<Map<String, SnoozeStatsSnapshot>>(emptyMap())
    private var remoteListener: ListenerRegistration? = null

    // `users/{uid}.snoozePickLog`; null while signed out or before the first snapshot.
    private val _pickLog = MutableStateFlow<Boolean?>(null)
    val pickLog: StateFlow<Boolean?> = _pickLog

    /** This device's partition plus every other device's, ready to feed [SnoozeSuggestions.rank]. */
    val partitions: StateFlow<List<SnoozeStatsSnapshot>> =
        combine(_local, _remote) { local, remote -> listOf(local) + remote.values }
            .stateIn(scope, SharingStarted.WhileSubscribed(5000), listOf(SnoozeStatsSnapshot()))

    private val loaded = CompletableDeferred<Unit>()

    // Room writes, applied strictly in the order they were issued.
    private val roomWrites = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch(Dispatchers.Main.immediate) {
            _local.value = withContext(Dispatchers.IO) { loadLocal() }
            loaded.complete(Unit)
            scope.launch(Dispatchers.IO) {
                for (write in roomWrites) {
                    try {
                        write()
                    } catch (e: Exception) {
                        Log.w(TAG, "snoozeStats Room write failed", e)
                    }
                }
            }
            if (authManager != null && firestore != null) {
                var prevUid: String? = null
                authManager.currentUser.collect { user ->
                    val uid = user?.uid
                    if (uid != prevUid) {
                        if (uid != null) attachRemote(uid) else detachRemote()
                    }
                    prevUid = uid
                }
            }
        }
    }

    private suspend fun loadLocal(): SnoozeStatsSnapshot {
        // Counters restored from a backup without their identity would double-count another device's partition.
        if (userPreferences.snoozeIdentityWasMissing) {
            dao.clearAll()
            return SnoozeStatsSnapshot()
        }
        val sets = dao.getAllSets()
            .filter { SnoozeSuggestions.isValidSetId(it.id) }
            .associate { it.id to SetStat(it.c, it.t) }
        val tod = dao.getAllTod()
            .filter { SnoozeSuggestions.SLOT_RE.matches(it.slot) }
            .associate { it.slot to SetStat(it.c, it.t) }
        val lastCustom = userPreferences.lastCustomTarget?.let { target ->
            userPreferences.lastCustomAt?.let { at -> LastCustom(target, at) }
        }
        val picks = dao.getAllPicks()
            .filter { SnoozeSuggestions.PICK_RE.matches(it.pick) && it.n > 0 }
            .associate { it.pick to it.n }
        return SnoozeStatsSnapshot(sets, tod, lastCustom, picks)
    }

    private var retryAttempt = 0
    private var retryJob: Job? = null

    private fun attachRemote(uid: String) {
        if (firestore == null) return
        detachRemote()
        ensureOwner(uid)
        retryAttempt = 0
        startListening(uid)
    }

    /** A different account gets a new device id too, or switching back would overwrite its old partition with an empty one. */
    private fun ensureOwner(uid: String) {
        val owner = userPreferences.snoozeStatsOwnerUid
        if (owner != null && owner != uid) {
            _local.value = SnoozeStatsSnapshot()
            userPreferences.lastCustomTarget = null
            userPreferences.lastCustomAt = null
            roomWrites.trySend { dao.clearAll() }
            userPreferences.rotateDeviceId()
        }
        userPreferences.snoozeStatsOwnerUid = uid
    }

    /** A delivered error ends the listener; re-register with capped backoff, like [FirestoreTodoStore]. */
    private fun startListening(uid: String) {
        val fs = firestore ?: return
        val doc = fs.collection("users").document(uid)
        remoteListener = doc.addSnapshotListener { snap, err ->
            if (err != null) {
                Log.w(TAG, "snoozeStats listener error", err)
                remoteListener = null
                scheduleReattach(uid)
                return@addSnapshotListener
            }
            retryAttempt = 0
            _pickLog.value = snap?.get("snoozePickLog") == true
            val raw = snap?.get("snoozeStats") as? Map<*, *> ?: emptyMap<String, Any?>()
            val myId = deviceId
            _remote.value = raw.entries
                .filter { (k, _) -> k is String && k != myId }
                .associate { (k, v) -> (k as String) to SnoozeStatsWire.fromWire(v as? Map<*, *>) }
        }
    }

    private fun scheduleReattach(uid: String) {
        if (retryJob != null) return
        val backoff = minOf(MAX_RETRY_DELAY_MS, BASE_RETRY_DELAY_MS shl retryAttempt.coerceAtMost(RETRY_SHIFT_CAP))
        retryAttempt++
        retryJob = scope.launch(Dispatchers.Main.immediate) {
            delay(backoff)
            retryJob = null
            startListening(uid)
        }
    }

    private fun detachRemote() {
        remoteListener?.remove()
        remoteListener = null
        retryJob?.cancel()
        retryJob = null
        _remote.value = emptyMap()
        _pickLog.value = null
    }

    /** [pickLogKey] is counted only while signed in and opted in. */
    suspend fun commit(at: Long, target: Long, source: String, pickedKey: String?, pickLogKey: String?): CommitResult =
        withContext(Dispatchers.Main.immediate) {
            loaded.await()
            val uid = currentUidOrNull()
            uid?.let { ensureOwner(it) }
            val pickLogged = if (uid != null && _pickLog.value == true) pickLogKey else null
            val result = SnoozeSuggestions.commit(_local.value, at, target, zoneProvider(), source, pickedKey, pickLogged)
            _local.value = result.next
            roomWrites.trySend { persist(result) }
            if (source == "custom") {
                userPreferences.lastCustomTarget = target
                userPreferences.lastCustomAt = at
            }
            mirror()
            result.touchedPickKey?.let { bumpGlobalPick(it, 1) }
            result
        }

    suspend fun undo(result: CommitResult) = withContext(Dispatchers.Main.immediate) {
        loaded.await()
        currentUidOrNull()?.let { ensureOwner(it) }
        _local.value = SnoozeSuggestions.revert(_local.value, result)
        roomWrites.trySend { persistRevert(result) }
        userPreferences.lastCustomTarget = result.undoLastCustom?.target
        userPreferences.lastCustomAt = result.undoLastCustom?.at
        mirror()
        result.touchedPickKey?.let { bumpGlobalPick(it, -1) }
    }

    /** Separate from the partition write so a rules rejection can't cost the user's stats. */
    private fun bumpGlobalPick(key: String, delta: Long) {
        if (currentUidOrNull() == null || _pickLog.value != true) return
        val fs = firestore ?: return
        // `key` names the bumped field, since rules can't pull it out of the changed-field set.
        fs.collection("snoozePickLog").document("global")
            .set(mapOf(key to FieldValue.increment(delta), "key" to key), SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "global snooze pick log failed", it) }
    }

    /** Called only from sign-in. */
    suspend fun pushLocalPartition() = withContext(Dispatchers.Main.immediate) {
        loaded.await()
        val uid = currentUidOrNull() ?: return@withContext
        ensureOwner(uid)
        mirror()
    }

    /** Per user, so it covers every device; turning it off also clears this device's picks. */
    suspend fun setPickLog(enabled: Boolean) = withContext(Dispatchers.Main.immediate) {
        loaded.await()
        val uid = currentUidOrNull() ?: return@withContext
        val fs = firestore ?: return@withContext
        ensureOwner(uid)
        _pickLog.value = enabled
        val payload = mutableMapOf<String, Any>("snoozePickLog" to enabled)
        val fields = mutableListOf(FieldPath.of("snoozePickLog"))
        if (!enabled) {
            dropPicks()
            payload["snoozeStats"] = mapOf(deviceId to SnoozeStatsWire.toWire(_local.value))
            fields += FieldPath.of("snoozeStats", deviceId)
        }
        fs.collection("users").document(uid)
            .set(payload, SetOptions.mergeFieldPaths(fields))
            .addOnFailureListener { Log.w(TAG, "snoozePickLog write failed", it) }
    }

    private fun dropPicks() {
        if (_local.value.picks.isEmpty()) return
        _local.value = _local.value.copy(picks = emptyMap())
        roomWrites.trySend { dao.clearPicks() }
    }

    suspend fun deleteRemote(uid: String) {
        val fs = firestore ?: return
        fs.collection("users").document(uid).delete().await()
    }

    private suspend fun persist(result: CommitResult) {
        val id = result.touchedSetId
        val setUpsert = id?.let { result.next.sets[it] }?.let { SnoozeSetEntity(id, it.c, it.t) }
        val slot = result.touchedTodSlot
        val todUpsert = slot?.let { result.next.tod[it] }?.let { SnoozeTodEntity(slot, it.c, it.t) }
        val pick = result.touchedPickKey
        val pickUpsert = pick?.let { result.next.picks[it] }?.let { SnoozePickEntity(pick, it) }
        dao.applyCommitChanges(setUpsert, null, todUpsert, null, pickUpsert, null)
    }

    private suspend fun persistRevert(result: CommitResult) {
        val id = result.touchedSetId
        val setUpsert = if (id != null) result.undoSetSnapshot?.let { SnoozeSetEntity(id, it.c, it.t) } else null
        val setDelete = if (id != null && result.undoSetSnapshot == null) id else null
        val slot = result.touchedTodSlot
        val todUpsert = if (slot != null) result.undoTodSnapshot?.let { SnoozeTodEntity(slot, it.c, it.t) } else null
        val todDelete = if (slot != null && result.undoTodSnapshot == null) slot else null
        val pick = result.touchedPickKey
        val pickUpsert = if (pick != null) result.undoPickSnapshot?.let { SnoozePickEntity(pick, it) } else null
        val pickDelete = if (pick != null && result.undoPickSnapshot == null) pick else null
        dao.applyCommitChanges(setUpsert, setDelete, todUpsert, todDelete, pickUpsert, pickDelete)
    }

    private fun mirror() {
        val uid = currentUidOrNull() ?: return
        val fs = firestore ?: return
        if (_pickLog.value == false) dropPicks()
        val id = deviceId
        val payload = mapOf("snoozeStats" to mapOf(id to SnoozeStatsWire.toWire(_local.value)))
        fs.collection("users").document(uid)
            .set(payload, SetOptions.mergeFieldPaths(listOf(FieldPath.of("snoozeStats", id))))
            .addOnFailureListener { Log.w(TAG, "snoozeStats mirror failed", it) }
    }

    companion object {
        private const val TAG = "SnoozeStatsStore"
        private const val BASE_RETRY_DELAY_MS = 1_000L
        private const val MAX_RETRY_DELAY_MS = 30_000L
        private const val RETRY_SHIFT_CAP = 5
    }
}
