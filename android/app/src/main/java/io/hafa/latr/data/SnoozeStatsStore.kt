package io.hafa.latr.data

import android.util.Log
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

/** [deltas] is what a signed-in commit added to the shared counts; null for a signed-out one. */
data class SnoozeUndo(val result: CommitResult, val deltas: VoteDeltas?, val custom: Boolean)

/** Signed in, the counts are the account's `users/{uid}.snoozeVotes`, changed only by adding amounts; signed out, this device's own in Room. In-memory state is main-thread only. */
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

    fun currentUidOrNull(): String? = authManager?.currentUser?.value?.uid

    private val _local = MutableStateFlow(SnoozeStatsSnapshot())
    private val _shared = MutableStateFlow(SnoozeStatsSnapshot())
    private val _signedIn = MutableStateFlow(false)
    private var remoteListener: ListenerRegistration? = null
    private var folding = false

    // `users/{uid}.snoozePickLog`; null while signed out or before the first snapshot.
    private val _pickLog = MutableStateFlow<Boolean?>(null)
    val pickLog: StateFlow<Boolean?> = _pickLog

    /** The counts to feed [SnoozeSuggestions.rank]: the account's while signed in, else this device's. */
    val partitions: StateFlow<List<SnoozeStatsSnapshot>> =
        combine(_local, _shared, _signedIn) { local, shared, signedIn -> listOf(if (signedIn) shared else local) }
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
        // Counts restored from a backup, or saved while signed in by a build that kept a block per device, are already in an account's counts.
        if (userPreferences.snoozeIdentityWasMissing || userPreferences.snoozeStatsOwnerUid != null) {
            dao.clearAll()
            userPreferences.lastCustomTarget = null
            userPreferences.lastCustomAt = null
            userPreferences.snoozeStatsOwnerUid = null
            userPreferences.snoozeSetsVersion = SnoozeStatsWire.PARTITION_VERSION
            // Creates the identity whose absence marks a restore.
            userPreferences.deviceId
            return SnoozeStatsSnapshot()
        }
        var sets = dao.getAllSets()
            .filter { SnoozeSuggestions.isValidSetId(it.id) }
            .associate { it.id to SetStat(it.c, it.t) }
        if (userPreferences.snoozeSetsVersion < SnoozeStatsWire.PARTITION_VERSION) {
            sets = SnoozeSuggestions.upgradeLegacySets(sets, zoneProvider())
            dao.replaceSets(sets.map { (id, s) -> SnoozeSetEntity(id, s.c, s.t) })
            userPreferences.snoozeSetsVersion = SnoozeStatsWire.PARTITION_VERSION
        }
        val tod = dao.getAllTod()
            .filter { SnoozeSuggestions.SLOT_RE.matches(it.slot) }
            .associate { it.slot to SetStat(it.c, it.t) }
        val lastCustom = userPreferences.lastCustomTarget?.let { target ->
            userPreferences.lastCustomAt?.let { at -> LastCustom(target, at) }
        }
        return SnoozeStatsSnapshot(sets, tod, lastCustom)
    }

    private var retryAttempt = 0
    private var retryJob: Job? = null

    private fun attachRemote(uid: String) {
        if (firestore == null) return
        detachRemote()
        _signedIn.value = true
        retryAttempt = 0
        startListening(uid)
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
            val votes = snap?.get("snoozeVotes") as? Map<*, *>
            _shared.value = SnoozeStatsWire.sharedFromWire(votes)
            val blocks = snap?.get("snoozeStats") as? Map<*, *>
            if (SnoozeStatsWire.foldBlocks(blocks, votes?.get("folded") as? Map<*, *>, zoneProvider()).ids.isNotEmpty()) {
                foldDeviceBlocks(uid)
            }
        }
    }

    /** Adds the blocks older builds saved per device to the shared counts, once each. A transaction, so two devices can't both add one; a block an old build saves again afterwards is ignored. */
    private fun foldDeviceBlocks(uid: String) {
        val fs = firestore ?: return
        if (folding) return
        folding = true
        val doc = fs.collection("users").document(uid)
        val zone = zoneProvider()
        fs.runTransaction { tx ->
            val snap = tx.get(doc)
            val votes = snap.get("snoozeVotes") as? Map<*, *>
            val fold = SnoozeStatsWire.foldBlocks(snap.get("snoozeStats") as? Map<*, *>, votes?.get("folded") as? Map<*, *>, zone)
            if (fold.ids.isNotEmpty()) {
                val current = SnoozeStatsWire.sharedFromWire(votes).lastCustom
                val newer = fold.lastCustom?.takeIf { current == null || it.at > current.at }
                val payload = mutableMapOf<String, Any?>(
                    "sets" to increments(fold.deltas.sets),
                    "tod" to increments(fold.deltas.tod),
                    "folded" to fold.ids.associateWith { true },
                )
                if (newer != null) payload["lastCustom"] = lastCustomWire(newer)
                if (snap.get("snoozePickLog") == true) {
                    payload["picks"] = fold.picks.mapValues { FieldValue.increment(it.value) }
                }
                tx.set(doc, mapOf("snoozeVotes" to payload, "snoozeStats" to FieldValue.delete()), SetOptions.merge())
            }
            null
        }.addOnCompleteListener { task ->
            folding = false
            task.exception?.let { Log.w(TAG, "snoozeStats fold failed", it) }
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
        _signedIn.value = false
        _shared.value = SnoozeStatsSnapshot()
        _pickLog.value = null
    }

    /** [pickLogKey] is counted only while signed in and opted in. */
    suspend fun commit(at: Long, target: Long, source: String, pickedKey: String?, pickLogKey: String?): SnoozeUndo =
        withContext(Dispatchers.Main.immediate) {
            loaded.await()
            val uid = currentUidOrNull()
            val custom = source == "custom"
            if (uid != null) {
                val before = _shared.value
                val pickLogged = if (_pickLog.value == true) pickLogKey else null
                val result = SnoozeSuggestions.commit(before, at, target, zoneProvider(), source, pickedKey, pickLogged)
                val deltas = SnoozeStatsWire.voteDeltas(before, result.next)
                _shared.value = result.next
                val extra = mutableMapOf<String, Any?>()
                if (custom) extra["lastCustom"] = lastCustomWire(result.next.lastCustom)
                result.touchedPickKey?.let { extra["picks"] = mapOf(it to FieldValue.increment(1L)) }
                addVotes(uid, deltas, extra)
                result.touchedPickKey?.let { bumpGlobalPick(it, 1) }
                SnoozeUndo(result, deltas, custom)
            } else {
                val result = SnoozeSuggestions.commit(_local.value, at, target, zoneProvider(), source, pickedKey, null)
                _local.value = result.next
                roomWrites.trySend { persist(result) }
                if (custom) {
                    userPreferences.lastCustomTarget = target
                    userPreferences.lastCustomAt = at
                }
                SnoozeUndo(result, null, custom)
            }
        }

    suspend fun undo(undo: SnoozeUndo) = withContext(Dispatchers.Main.immediate) {
        loaded.await()
        val result = undo.result
        val uid = currentUidOrNull()
        if (undo.deltas == null) {
            if (uid != null) return@withContext
            _local.value = SnoozeSuggestions.revert(_local.value, result)
            roomWrites.trySend { persistRevert(result) }
            userPreferences.lastCustomTarget = result.undoLastCustom?.target
            userPreferences.lastCustomAt = result.undoLastCustom?.at
        } else if (uid != null) {
            _shared.value = SnoozeSuggestions.revert(_shared.value, result)
            val extra = mutableMapOf<String, Any?>()
            if (undo.custom) extra["lastCustom"] = lastCustomWire(result.undoLastCustom)
            val pick = result.touchedPickKey
            if (pick != null && _pickLog.value == true) extra["picks"] = mapOf(pick to FieldValue.increment(-1L))
            addVotes(uid, undo.deltas.negated(), extra)
            pick?.let { bumpGlobalPick(it, -1) }
        }
    }

    /** Separate from the votes write so a rules rejection can't cost the user's stats. */
    private fun bumpGlobalPick(key: String, delta: Long) {
        if (currentUidOrNull() == null || _pickLog.value != true) return
        val fs = firestore ?: return
        // `key` names the bumped field, since rules can't pull it out of the changed-field set.
        fs.collection("snoozePickLog").document("global")
            .set(mapOf(key to FieldValue.increment(delta), "key" to key), SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "global snooze pick log failed", it) }
    }

    /** One read of `snoozePickLog/global`; null if it fails (only admins may read it). */
    suspend fun fetchGlobalPicks(): List<Long>? {
        val fs = firestore ?: return null
        return runCatching {
            SnoozeStatsWire.globalPickCounts(fs.collection("snoozePickLog").document("global").get().await().data)
        }.onFailure { Log.w(TAG, "global snooze pick log read failed", it) }.getOrNull()
    }

    /** Called only from sign-in: adds the counts learned while signed out to the account's, once. */
    suspend fun pushLocalPartition() = withContext(Dispatchers.Main.immediate) {
        loaded.await()
        val uid = currentUidOrNull() ?: return@withContext
        val deltas = SnoozeStatsWire.voteDeltas(SnoozeStatsSnapshot(), _local.value)
        _local.value = SnoozeStatsSnapshot()
        userPreferences.lastCustomTarget = null
        userPreferences.lastCustomAt = null
        roomWrites.trySend { dao.clearAll() }
        addVotes(uid, deltas, emptyMap())
    }

    /** Per user, so it covers every device; turning it off also clears the saved picks. */
    suspend fun setPickLog(enabled: Boolean) = withContext(Dispatchers.Main.immediate) {
        loaded.await()
        val uid = currentUidOrNull() ?: return@withContext
        val fs = firestore ?: return@withContext
        _pickLog.value = enabled
        val payload = mutableMapOf<String, Any>("snoozePickLog" to enabled)
        if (!enabled) payload["snoozeVotes"] = mapOf("picks" to FieldValue.delete())
        fs.collection("users").document(uid)
            .set(payload, SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "snoozePickLog write failed", it) }
    }

    suspend fun deleteRemote(uid: String) {
        val fs = firestore ?: return
        fs.collection("users").document(uid).delete().await()
    }

    private suspend fun persist(result: CommitResult) {
        val setIds = result.undoNearbySets.keys + listOfNotNull(result.touchedSetId)
        val setUpserts = setIds.mapNotNull { id -> result.next.sets[id]?.let { SnoozeSetEntity(id, it.c, it.t) } }
        val slots = result.undoNearbyTod.keys + listOfNotNull(result.touchedTodSlot)
        val todUpserts = slots.mapNotNull { slot -> result.next.tod[slot]?.let { SnoozeTodEntity(slot, it.c, it.t) } }
        dao.applyCommitChanges(setUpserts, null, todUpserts, null, null, null)
    }

    private suspend fun persistRevert(result: CommitResult) {
        val id = result.touchedSetId
        val setUpserts = (result.undoNearbySets + listOfNotNull(result.undoSetSnapshot?.let { id?.to(it) }))
            .map { (setId, stat) -> SnoozeSetEntity(setId, stat.c, stat.t) }
        val setDelete = if (id != null && result.undoSetSnapshot == null) id else null
        val slot = result.touchedTodSlot
        val todUpserts = (result.undoNearbyTod + listOfNotNull(result.undoTodSnapshot?.let { slot?.to(it) }))
            .map { (todSlot, stat) -> SnoozeTodEntity(todSlot, stat.c, stat.t) }
        val todDelete = if (slot != null && result.undoTodSnapshot == null) slot else null
        dao.applyCommitChanges(setUpserts, setDelete, todUpserts, todDelete, null, null)
    }

    private fun increments(deltas: Map<String, Double>): Map<String, FieldValue> =
        deltas.mapValues { FieldValue.increment(it.value) }

    private fun lastCustomWire(lastCustom: LastCustom?): Map<String, Long>? =
        lastCustom?.let { mapOf("target" to it.target, "at" to it.at) }

    /** Amounts are added on the server (and to the cached copy while offline), so nothing another device wrote is overwritten. */
    private fun addVotes(uid: String, deltas: VoteDeltas, extra: Map<String, Any?>) {
        val fs = firestore ?: return
        val votes = extra.toMutableMap()
        if (deltas.sets.isNotEmpty()) votes["sets"] = increments(deltas.sets)
        if (deltas.tod.isNotEmpty()) votes["tod"] = increments(deltas.tod)
        if (votes.isEmpty()) return
        fs.collection("users").document(uid)
            .set(mapOf("snoozeVotes" to votes), SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "snoozeVotes write failed", it) }
    }

    companion object {
        private const val TAG = "SnoozeStatsStore"
        private const val BASE_RETRY_DELAY_MS = 1_000L
        private const val MAX_RETRY_DELAY_MS = 30_000L
        private const val RETRY_SHIFT_CAP = 5
    }
}
