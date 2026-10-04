package cc.hafa.latr.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cc.hafa.latr.data.SignOutResult
import cc.hafa.latr.data.SnoozeStatsStore
import cc.hafa.latr.data.SnoozeUndo
import cc.hafa.latr.data.Todo
import cc.hafa.latr.data.TodoState
import cc.hafa.latr.data.TodoStoreHolder
import cc.hafa.latr.util.LocalDateTimeUtil
import cc.hafa.latr.util.SnoozeStatsSnapshot
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TodoViewModel(
    private val storeHolder: TodoStoreHolder,
    private val snoozeStatsStore: SnoozeStatsStore,
) : ViewModel() {
    val snoozePartitions: StateFlow<List<SnoozeStatsSnapshot>> = snoozeStatsStore.partitions
    val snoozePickLog: StateFlow<Boolean?> = snoozeStatsStore.pickLog
    // null until the first snapshot; lets the UI show a spinner, not a false empty state.
    val todos: StateFlow<List<Todo>?> = storeHolder.store
        .flatMapLatest { it.observeAll() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), null)

    private val _focusId = MutableStateFlow<String?>(null)
    val focusId: StateFlow<String?> = _focusId

    private val _fastCreationId = MutableStateFlow<String?>(null)
    val fastCreationId: StateFlow<String?> = _fastCreationId

    // Most-recent-wins undo: Delete restores via re-insert, Snooze/Complete via update.
    private sealed interface UndoableAction {
        data class Delete(val todos: List<Todo>) : UndoableAction
        data class Snooze(val previous: Todo, val statsUndo: SnoozeUndo) : UndoableAction
        data class Complete(val previous: Todo) : UndoableAction
    }

    private var _lastAction: UndoableAction? = null

    // VM owns the undo buffer + its 5s lifetime; the UI just renders undoVisible.
    private val _undoVisible = MutableStateFlow(false)
    val undoVisible: StateFlow<Boolean> = _undoVisible

    private val _undoLabel = MutableStateFlow("")
    val undoLabel: StateFlow<String> = _undoLabel

    private var undoExpiryJob: Job? = null

    private fun armUndo() {
        _undoLabel.value = when (val action = _lastAction) {
            is UndoableAction.Delete -> if (action.todos.size == 1) "Deleted" else "Deleted ${action.todos.size} todos"
            is UndoableAction.Snooze -> "Snoozed"
            is UndoableAction.Complete -> "Completed"
            null -> ""
        }
        _undoVisible.value = true
        undoExpiryJob?.cancel()
        undoExpiryJob = viewModelScope.launch {
            delay(UNDO_TIMEOUT)
            _undoVisible.value = false
            _lastAction = null
        }
    }

    /** Drops the undo buffer and hides the chip (filter change, new todo). */
    fun dismissUndo() {
        undoExpiryJob?.cancel()
        undoExpiryJob = null
        _undoVisible.value = false
        _lastAction = null
    }

    private fun currentStore() = storeHolder.store.value

    fun createTodo() {
        dismissUndo()
        // Insert + focus first; the empty-cleanup query runs in the background so a cold-boot get can't block focus.
        val todo = Todo()
        viewModelScope.launch {
            val store = currentStore()
            store.insert(todo)
            _fastCreationId.value = todo.id
            _focusId.value = todo.id
            store.deleteEmptyTodosExcept(todo.id)
        }
    }

    fun updateTodo(todo: Todo, touchModifiedAt: Boolean = true) {
        viewModelScope.launch {
            val updated = if (touchModifiedAt) {
                todo.copy(modifiedAt = System.currentTimeMillis())
            } else {
                todo
            }
            currentStore().update(updated)
        }
    }

    fun deleteTodo(todo: Todo) {
        viewModelScope.launch {
            currentStore().delete(todo)
        }
    }

    fun deleteTodoUndoable(todo: Todo) {
        _lastAction = UndoableAction.Delete(listOf(todo))
        armUndo()
        viewModelScope.launch {
            currentStore().delete(todo)
        }
    }

    fun completeUndoable(todo: Todo) {
        _lastAction = UndoableAction.Complete(todo)
        armUndo()
        updateTodo(
            todo.copy(state = TodoState.DONE, snoozeUntil = null),
            touchModifiedAt = true
        )
    }

    /** [source] is "suggestion", "last", or "custom"; [pickedKey] is the suggestion row's key. False if [snoozeUntil] has passed. */
    fun snoozeUndoable(todo: Todo, snoozeUntil: String, source: String, pickedKey: String?, pickLogKey: String?): Boolean {
        val target = LocalDateTimeUtil.toEpochMillis(snoozeUntil)
        if (target <= System.currentTimeMillis()) return false
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val statsUndo = snoozeStatsStore.commit(now, target, source, pickedKey, pickLogKey)
            // Buffer the pre-snooze snapshot so undo restores its prior sort position.
            _lastAction = UndoableAction.Snooze(todo, statsUndo)
            armUndo()
            updateTodo(todo.copy(snoozeUntil = snoozeUntil), touchModifiedAt = true)
        }
        return true
    }

    fun setFocusId(todoId: String) {
        _focusId.value = todoId
        if (_fastCreationId.value != null && _fastCreationId.value != todoId) {
            _fastCreationId.value = null
        }
        viewModelScope.launch {
            currentStore().deleteEmptyTodosExcept(todoId)
        }
    }

    fun clearFocusId(expectedId: String) {
        if (_focusId.value == expectedId) {
            _focusId.value = null
            _fastCreationId.value = null
        }
    }

    fun clearFocus() {
        _focusId.value = null
        _fastCreationId.value = null
        viewModelScope.launch {
            currentStore().deleteEmptyTodosExcept("")
        }
    }

    fun clearAllDone() {
        val doneTodos = todos.value?.filter { it.state == TodoState.DONE }.orEmpty()
        if (doneTodos.isEmpty()) return
        viewModelScope.launch {
            // Enqueue the delete before arming undo so an early Undo lands after it.
            currentStore().clearAllDone(doneTodos)
            _lastAction = UndoableAction.Delete(doneTodos)
            armUndo()
        }
    }

    fun signIn(onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = storeHolder.signIn()
            // Even if the todo merge failed: the counts learned while signed out still join the account's.
            if (snoozeStatsStore.currentUidOrNull() != null) snoozeStatsStore.pushLocalPartition()
            onResult(result)
        }
    }

    fun setSnoozePickLog(enabled: Boolean) {
        viewModelScope.launch { snoozeStatsStore.setPickLog(enabled) }
    }

    suspend fun loadGlobalPicks(): List<Long>? = snoozeStatsStore.fetchGlobalPicks()

    // True while a sign-out is held back by changes that haven't reached the account.
    private val _signOutPending = MutableStateFlow(false)
    val signOutPending: StateFlow<Boolean> = _signOutPending

    fun dismissSignOutPending() {
        _signOutPending.value = false
    }

    fun signOut(force: Boolean = false) {
        _signOutPending.value = false
        viewModelScope.launch {
            val result = storeHolder.signOut(force, beforeTerminate = { snoozeStatsStore.detach() })
            _signOutPending.value = result == SignOutResult.PENDING
        }
    }

    /** [keep] leaves the account's todos and learned snooze counts on this device. */
    fun deleteAccount(keep: Boolean, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val uid = snoozeStatsStore.currentUidOrNull()
            // Counts add up, so they are only kept once the account is gone; a failed delete must not leave a second copy.
            val counts = if (keep) snoozeStatsStore.sharedCounts() else null
            val result = storeHolder.deleteAccount(
                keep = keep,
                extraRemoteWipe = { if (uid != null) snoozeStatsStore.deleteRemote(uid) },
                beforeTerminate = { snoozeStatsStore.detach() },
            )
            if (counts != null && result.isSuccess) snoozeStatsStore.keepCounts(counts)
            onResult(result)
        }
    }

    fun undoLastAction() {
        when (val action = _lastAction) {
            is UndoableAction.Delete ->
                if (action.todos.isNotEmpty()) {
                    viewModelScope.launch {
                        currentStore().restoreMany(action.todos)
                    }
                }
            is UndoableAction.Snooze ->
                // Re-apply the snapshot verbatim (no modifiedAt touch) to keep sort position; also un-teach the stats store.
                viewModelScope.launch {
                    currentStore().update(action.previous)
                    snoozeStatsStore.undo(action.statsUndo)
                }
            is UndoableAction.Complete ->
                viewModelScope.launch {
                    currentStore().update(action.previous)
                }
            null -> {}
        }
        dismissUndo()
    }

    companion object {
        private val UNDO_TIMEOUT = 5.seconds
    }
}

class TodoViewModelFactory(
    private val storeHolder: TodoStoreHolder,
    private val snoozeStatsStore: SnoozeStatsStore,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TodoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TodoViewModel(storeHolder, snoozeStatsStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
