package cc.hafa.latr.data

import android.util.Log
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import cc.hafa.latr.ui.auth.AuthManager
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class SignOutResult { DONE, PENDING }

/** Owns the live [TodoStore]; swaps it at the auth boundary. Room holds todos only while signed out: [signIn] moves them into the account, and [signOut]/[deleteAccount] clear Firestore's on-device cache. */
class TodoStoreHolder(
    private val dao: TodoDao,
    private val userPreferences: UserPreferences,
    private val authManager: AuthManager?,
    private val scope: CoroutineScope,
) {
    private val roomStore = RoomTodoStore(dao)
    private val firestore: FirebaseFirestore?
        get() = firestoreOrNull()

    private val _store = MutableStateFlow<TodoStore>(roomStore)
    val store: StateFlow<TodoStore> = _store

    private var currentFirestoreStore: FirestoreTodoStore? = null

    // Which user's store is live; [leaveFirestore] swaps ahead of the auth flow, which then sees no change.
    @Volatile
    private var attachedUid: String? = null

    init {
        if (!userPreferences.localTodosMoved) {
            val signedIn = authManager?.currentUser?.value != null
            scope.launch {
                // Builds before sign-in moved todos left a stale copy in Room.
                if (signedIn) dao.deleteAll()
                userPreferences.localTodosMoved = true
            }
        }
        if (authManager != null && firestore != null) {
            scope.launch {
                authManager.currentUser.collect { user ->
                    val newUid = user?.uid
                    if (newUid != attachedUid) {
                        if (newUid != null) swapToFirestore(newUid)
                        else swapToRoom()
                    }
                }
            }
        }
    }

    private fun swapToFirestore(uid: String) {
        val fs = firestore ?: return
        val store = FirestoreTodoStore(fs, uid)
        currentFirestoreStore = store
        _store.value = store
        attachedUid = uid
    }

    private fun swapToRoom() {
        currentFirestoreStore = null
        _store.value = roomStore
        attachedUid = null
    }

    /** Sign in via Google, then move this device's todos into the account. A failed move leaves them in Room for the next sign-in. */
    suspend fun signIn(): Result<Unit> {
        val am = authManager ?: return Result.success(Unit)
        val user = am.signInWithGoogle().getOrElse { cause ->
            // A dismissed prompt is a no-op success; any real failure surfaces.
            return if (cause is GetCredentialCancellationException) Result.success(Unit)
            else Result.failure(cause)
        }
        return runCatching { mergeRoomIntoFirestore(user.uid) }
            .onFailure { Log.e(TAG, "sign-in merge failed", it) }
            .map { }
    }

    /** [SignOutResult.PENDING] means nothing changed: some writes haven't reached the account, and [force] wasn't set. [beforeTerminate] detaches other Firestore listeners. */
    suspend fun signOut(force: Boolean = false, beforeTerminate: suspend () -> Unit = {}): SignOutResult {
        val am = authManager ?: return SignOutResult.DONE
        return if (!force && !pendingWritesSent()) {
            SignOutResult.PENDING
        } else {
            withContext(NonCancellable) {
                am.signOut()
                leaveFirestore(beforeTerminate)
            }
            SignOutResult.DONE
        }
    }

    private suspend fun pendingWritesSent(): Boolean {
        val fs = firestore ?: return true
        return try {
            withTimeoutOrNull(PENDING_WRITES_TIMEOUT) {
                fs.waitForPendingWrites().await()
                true
            } ?: false
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "waiting for pending writes failed", e)
            // The wait also fails when the user changes, and then there is nothing left to wait for.
            authManager?.uid == null
        }
    }

    /** Swaps to Room, then ends the Firestore instance and clears its on-device cache. Call once signed out. */
    private suspend fun leaveFirestore(beforeTerminate: suspend () -> Unit) {
        // Before terminate: a write or listen on a terminated instance throws. Removing a listener afterwards is a no-op.
        swapToRoom()
        try {
            beforeTerminate()
            val fs = firestore ?: return
            fs.terminate().await()
            fs.clearPersistence().await()
        } catch (e: Exception) {
            Log.e(TAG, "clearing the Firestore cache failed", e)
        }
    }

    /** Keep or drop the account's todos on this device, wipe remote, then delete the auth user (triggers sign-out). [extraRemoteWipe] runs while still authorized; [beforeTerminate] as in [signOut]. */
    suspend fun deleteAccount(
        keep: Boolean,
        extraRemoteWipe: suspend () -> Unit = {},
        beforeTerminate: suspend () -> Unit = {},
    ): Result<Unit> {
        val am = authManager ?: return Result.success(Unit)
        // Reauth before wiping: a delete() that fails post-wipe could snapshot the empty remote over Room.
        val reauth = am.reauthenticateWithGoogle()
        if (reauth.isFailure) return reauth
        if (keep) snapshotFirestoreIntoRoom() else dao.deleteAll()
        // Wrap so a throw becomes a failed Result instead of crashing the launch.
        val wipe = runCatching { currentFirestoreStore?.deleteAll() }
        if (wipe.isFailure) {
            Log.e(TAG, "delete-account remote wipe failed", wipe.exceptionOrNull())
            return wipe.map { }
        }
        val extraWipe = runCatching { extraRemoteWipe() }
        if (extraWipe.isFailure) {
            Log.e(TAG, "delete-account remote wipe failed", extraWipe.exceptionOrNull())
            return extraWipe
        }
        val deleted = am.deleteCurrentUser()
        if (deleted.isSuccess) withContext(NonCancellable) { leaveFirestore(beforeTerminate) }
        return deleted
    }

    /** Copy Firestore state into Room; must run while still signed in. */
    private suspend fun snapshotFirestoreIntoRoom() {
        val leaving = currentFirestoreStore ?: return
        try {
            val remote = leaving.snapshot()
            dao.replaceAll(remote)
        } catch (e: Exception) {
            Log.e(TAG, "snapshot-to-room failed", e)
        }
    }

    /** Publish local edits and pending deletes (see [planMerge]), then empty Room. */
    private suspend fun mergeRoomIntoFirestore(uid: String) {
        val fs = firestore ?: return
        val collection = fs.collection("users").document(uid).collection("todos")
        val local = dao.getAllSnapshot()
        // Unfiltered, unlike the live listener: the plan needs to see the tombstones.
        val remoteSnap = collection.get().await()
        val remoteById = HashMap<String, Todo>(remoteSnap.size())
        for (doc in remoteSnap.documents) {
            remoteById[doc.id] = Todo.fromMap(doc.id, doc.data ?: emptyMap())
        }
        val (toPush, toDropLocalIds) = planMerge(local, remoteById)
        if (toPush.isNotEmpty()) {
            val batch = fs.batch()
            for (t in toPush) {
                batch.set(collection.document(t.id), t.toMap(), SetOptions.merge())
            }
            batch.commit().await()
        }
        dao.deleteAll()
        Log.d(TAG, "Sign-in merge: pushed=${toPush.size} dropped=${toDropLocalIds.size}")
    }

    companion object {
        private const val TAG = "TodoStoreHolder"
        private val PENDING_WRITES_TIMEOUT = 3.seconds
    }
}
