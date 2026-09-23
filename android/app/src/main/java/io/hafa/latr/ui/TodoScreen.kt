package io.hafa.latr.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.hafa.latr.data.Todo
import io.hafa.latr.data.TodoState
import io.hafa.latr.ui.auth.AccountBottomSheet
import io.hafa.latr.ui.auth.AuthManager
import io.hafa.latr.ui.auth.AuthState
import io.hafa.latr.ui.auth.ProfilePhoto
import io.hafa.latr.ui.auth.SignInBottomSheet
import io.hafa.latr.ui.auth.isSignedIn
import io.hafa.latr.ui.auth.photoUrl
import io.hafa.latr.ui.auth.rememberAuthState
import io.hafa.latr.ui.theme.LatrTheme
import io.hafa.latr.util.LocalDateTimeUtil
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

private fun StatusFilter.displayName(): String =
    name.lowercase().replaceFirstChar { it.uppercase() }

private fun StatusFilter.icon(): ImageVector = when (this) {
    StatusFilter.ALL -> Icons.AutoMirrored.Rounded.List
    StatusFilter.ACTIVE -> Icons.Rounded.RadioButtonUnchecked
    StatusFilter.SNOOZED -> Icons.Rounded.Schedule
    StatusFilter.DONE -> Icons.Rounded.CheckCircle
}

private sealed class ListEntry {
    data class Header(val label: String, val count: Int) : ListEntry()
    data class Row(val todo: Todo) : ListEntry()
}

private fun entriesFor(todos: List<Todo>, filter: StatusFilter, searching: Boolean, nowMillis: Long): List<ListEntry> =
    if (searching) {
        todos.map { ListEntry.Row(it) }
    } else {
        groupForFilter(todos, filter, nowMillis).flatMap { group ->
            listOf(ListEntry.Header(group.label, group.todos.size)) + group.todos.map { ListEntry.Row(it) }
        }
    }

/** Horizontal drag pages [pagerState] to flip the filter; drags within [EDGE_REJECT_DP] of a screen edge defer to the OS back-gesture. */
@Composable
private fun Modifier.filterSwipe(
    pagerState: PagerState,
    scope: CoroutineScope
): Modifier {
    var dragStartPage by remember { mutableIntStateOf(0) }
    var leftInWindowPx by remember { mutableFloatStateOf(0f) }
    var rejectGesture by remember { mutableStateOf(false) }
    val edgePx = with(LocalDensity.current) { EDGE_REJECT_DP.dp.toPx() }
    val windowWidthPx = LocalWindowInfo.current.containerSize.width
    return this
        .onGloballyPositioned { leftInWindowPx = it.positionInWindow().x }
        .draggable(
            state = rememberDraggableState { delta ->
                if (!rejectGesture) {
                    scope.launch { pagerState.scrollBy(-delta * 1.5f) }
                }
            },
            orientation = Orientation.Horizontal,
            onDragStarted = { startedPosition ->
                val x = leftInWindowPx + startedPosition.x
                rejectGesture = x < edgePx || x > windowWidthPx - edgePx
                if (!rejectGesture) dragStartPage = pagerState.currentPage
            },
            onDragStopped = { velocity ->
                if (!rejectGesture) {
                    val target = when {
                        pagerState.currentPage > dragStartPage ->
                            (dragStartPage + 1).coerceAtMost(TAB_ORDER.size - 1)
                        pagerState.currentPage < dragStartPage ->
                            (dragStartPage - 1).coerceAtLeast(0)
                        velocity < -800f ->
                            (dragStartPage + 1).coerceAtMost(TAB_ORDER.size - 1)
                        velocity > 800f ->
                            (dragStartPage - 1).coerceAtLeast(0)
                        else -> dragStartPage
                    }
                    scope.launch { pagerState.animateScrollToPage(target) }
                }
                rejectGesture = false
            }
        )
}

private const val EDGE_REJECT_DP = 24

/** How far item [index]'s bottom sits below the list's unobscured area (0 if clear or not laid out). */
private fun overflowPastBar(listState: LazyListState, index: Int): Float {
    val layout = listState.layoutInfo
    val item = layout.visibleItemsInfo.firstOrNull { it.index == index } ?: return 0f
    val visibleEnd = layout.viewportEndOffset - layout.afterContentPadding
    return (item.offset + item.size - visibleEnd).coerceAtLeast(0).toFloat()
}

// The horizontal swipe (row dismiss and filter pager) needs this multiple of the
// base touch-slop, so a drag must be clearly horizontal to beat the list's scroll.
private const val SWIPE_SLOP_MULTIPLIER = 2f

private fun inflatedSlop(base: ViewConfiguration): ViewConfiguration =
    object : ViewConfiguration by base {
        override val touchSlop: Float get() = base.touchSlop * SWIPE_SLOP_MULTIPLIER
    }

@Composable
private fun TopBar(
    title: String,
    count: Int?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchOpen: Boolean,
    onSearchOpenChange: (Boolean) -> Unit,
    isSignedIn: Boolean,
    profilePhotoUrl: String?,
    onAccountClick: () -> Unit,
    onSignInClick: () -> Unit,
    onClearAll: (() -> Unit)?,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = if (searchOpen) 4.dp else 20.dp, end = 8.dp, top = 8.dp)
    ) {
        AnimatedContent(targetState = searchOpen, label = "topBar", modifier = Modifier.weight(1f)) { open ->
            if (open) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        onSearchQueryChange("")
                        onSearchOpenChange(false)
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close search")
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                "Search",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                        }
                    }
                }
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    runCatching { focusRequester.requestFocus() }
                }
            } else {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    if (count != null && count > 0) {
                        Text(
                            count.toString(),
                            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }
            }
        }
        if (!searchOpen) {
            if (onClearAll != null) {
                TextButton(onClick = onClearAll) { Text("Clear all") }
            }
            IconButton(onClick = { onSearchOpenChange(true) }) {
                Icon(Icons.Rounded.Search, contentDescription = "Search")
            }
        }
        IconButton(onClick = if (isSignedIn) onAccountClick else onSignInClick) {
            if (isSignedIn) {
                ProfilePhoto(url = profilePhotoUrl, size = 32.dp)
            } else {
                Icon(
                    Icons.Rounded.AccountCircle,
                    contentDescription = "Sign in",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FilterToolbar(
    selected: StatusFilter,
    onSelect: (StatusFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        modifier = modifier
            .height(64.dp)
            .focusProperties { canFocus = false }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            TAB_ORDER.forEach { filter ->
                val isSelected = filter == selected
                val background by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    label = "filterBackground",
                )
                val content = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .clip(CircleShape)
                        .background(background)
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(filter) })
                        .animateContentSize()
                        .padding(horizontal = if (isSelected) 16.dp else 12.dp)
                ) {
                    Icon(
                        filter.icon(),
                        contentDescription = if (isSelected) null else filter.displayName(),
                        tint = content,
                    )
                    if (isSelected) {
                        Text(
                            filter.displayName(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = content,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UndoSnackbar(label: String, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(start = 16.dp, end = 8.dp)
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
            TextButton(onClick = onUndo) {
                Text("Undo", color = MaterialTheme.colorScheme.inversePrimary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun EmptyState(filter: StatusFilter, searchQuery: String) {
    val (title, hint) = when {
        searchQuery.isNotBlank() -> "No matches" to "Nothing matches \"$searchQuery\""
        filter == StatusFilter.ACTIVE -> "Nothing to do" to "Tap + to add a todo"
        filter == StatusFilter.SNOOZED -> "Nothing snoozed" to "Swipe a todo right to snooze it"
        filter == StatusFilter.DONE -> "Nothing done yet" to "Swipe a todo left to complete it"
        else -> "No todos yet" to "Tap + to add one"
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            filter.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GroupHeader(label: String, count: Int, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { heading() }
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(
    viewModel: TodoViewModel,
    authManager: AuthManager? = null,
    modifier: Modifier = Modifier
) {
    val todos by viewModel.todos.collectAsState()
    val focusId by viewModel.focusId.collectAsState()
    val fastCreationId by viewModel.fastCreationId.collectAsState()
    val snoozePartitions by viewModel.snoozePartitions.collectAsState()

    val undoVisible by viewModel.undoVisible.collectAsState()
    val undoLabel by viewModel.undoLabel.collectAsState()

    var showSnoozeSheet by remember { mutableStateOf(false) }
    var todoToSnooze by remember { mutableStateOf<Todo?>(null) }
    val authState = rememberAuthState(authManager)
    var showSignInSheet by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    TodoScreenContent(
        todos = todos,
        focusId = focusId,
        fastCreationId = fastCreationId,
        onCreateTodo = { viewModel.createTodo() },
        onUpdateTodo = { todo, touch -> viewModel.updateTodo(todo, touch) },
        onDeleteTodo = { viewModel.deleteTodo(it) },
        onSwipeDeleteTodo = { viewModel.deleteTodoUndoable(it) },
        onCompleteTodo = { viewModel.completeUndoable(it) },
        onTodoFocused = { todoId -> viewModel.setFocusId(todoId) },
        onTodoBlurred = { todoId -> viewModel.clearFocusId(todoId) },
        onClearFocus = { viewModel.clearFocus() },
        onRequestSnooze = { todo ->
            todoToSnooze = todo
            showSnoozeSheet = true
        },
        onClearAllDone = { viewModel.clearAllDone() },
        onUndoLastDelete = { viewModel.undoLastAction() },
        undoVisible = undoVisible,
        undoLabel = undoLabel,
        onDismissUndo = { viewModel.dismissUndo() },
        isSignedIn = authState.isSignedIn,
        profilePhotoUrl = authState.photoUrl,
        onSignInClick = { showSignInSheet = true },
        onAccountClick = { showAccountSheet = true },
        modifier = modifier
    )

    if (showSnoozeSheet) {
        SnoozeBottomSheet(
            onDismiss = {
                showSnoozeSheet = false
                todoToSnooze = null
            },
            onSnoozeSelected = { isoDateTime, source, pickedKey ->
                todoToSnooze?.let { todo -> viewModel.snoozeUndoable(todo, isoDateTime, source, pickedKey) } ?: false
            },
            partitions = snoozePartitions,
            todoText = todoToSnooze?.text.orEmpty(),
        )
    }

    if (showSignInSheet) {
        SignInBottomSheet(
            onSignIn = {
                viewModel.signIn { result ->
                    if (result.isFailure) {
                        Toast.makeText(
                            context,
                            "Sign-in didn't complete, or syncing this device's edits " +
                                "failed — nothing was lost, but edits and deletes made " +
                                "while signed out may not have synced. Sign out and back " +
                                "in to retry.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            },
            onDismiss = { showSignInSheet = false }
        )
    }

    if (showAccountSheet) {
        val signedIn = authState as? AuthState.SignedIn
        if (signedIn != null) {
            AccountBottomSheet(
                authState = signedIn,
                onSignOut = { viewModel.signOut() },
                onDeleteAccount = {
                    viewModel.deleteAccount { result ->
                        if (result.isFailure) {
                            Toast.makeText(
                                context,
                                "Account deletion didn't finish — some remote data " +
                                    "may already be gone. Please try again.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                onDismiss = { showAccountSheet = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun TodoScreenContent(
    // null = pre-first-snapshot (show spinner); a non-null empty list is a genuine empty state.
    todos: List<Todo>?,
    focusId: String? = null,
    fastCreationId: String? = null,
    onCreateTodo: () -> Unit,
    onUpdateTodo: (Todo, Boolean) -> Unit,
    onDeleteTodo: (Todo) -> Unit,
    onSwipeDeleteTodo: (Todo) -> Unit = onDeleteTodo,
    onCompleteTodo: (Todo) -> Unit = {
        onUpdateTodo(it.copy(state = TodoState.DONE, snoozeUntil = null), true)
    },
    onTodoFocused: (String) -> Unit,
    onTodoBlurred: (String) -> Unit = {},
    onClearFocus: () -> Unit = {},
    onRequestSnooze: (Todo) -> Unit,
    onClearAllDone: (() -> Unit)? = null,
    onUndoLastDelete: (() -> Unit)? = null,
    undoVisible: Boolean = false,
    undoLabel: String = "",
    onDismissUndo: () -> Unit = {},
    isSignedIn: Boolean = false,
    profilePhotoUrl: String? = null,
    onSignInClick: () -> Unit = {},
    onAccountClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    initialStatusFilter: StatusFilter = StatusFilter.ACTIVE
) {
    var isRefreshing by remember { mutableStateOf(false) }
    // Nothing writes to a todo when its snooze lapses, so this has to advance itself.
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val hapticFeedback = LocalHapticFeedback.current
    val pullToRefreshState = rememberPullToRefreshState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var savedPage by rememberSaveable { mutableIntStateOf(TAB_ORDER.indexOf(initialStatusFilter)) }
    val pagerState = rememberPagerState(initialPage = savedPage) { TAB_ORDER.size }
    val statusFilter = TAB_ORDER[pagerState.settledPage]
    val cacheWindow = remember { LazyLayoutCacheWindow(ahead = 300.dp, behind = 150.dp) }
    val listStates = TAB_ORDER.map { rememberLazyListState(cacheWindow) }

    LaunchedEffect(pagerState.settledPage) {
        savedPage = pagerState.settledPage
    }

    val filteredTodosByFilter = remember(todos, searchQuery, nowMillis) {
        val visible = todos ?: emptyList()
        TAB_ORDER.associateWith { filter ->
            visible.filterAndSort(filter, searchQuery, nowMillis)
        }
    }
    val entriesByFilter = remember(filteredTodosByFilter, nowMillis) {
        filteredTodosByFilter.mapValues { (filter, list) ->
            entriesFor(list, filter, searchQuery.isNotBlank(), nowMillis)
        }
    }
    val filteredTodos = filteredTodosByFilter[statusFilter] ?: emptyList()

    // Wake at the soonest snooze so its row moves itself out of Snoozed.
    LaunchedEffect(todos, nowMillis) {
        val nextExpiry = todos.orEmpty()
            .filter { it.state != TodoState.DONE }
            .mapNotNull { it.snoozeUntil?.let(LocalDateTimeUtil::toEpochMillis) }
            .filter { it > nowMillis }
            .minOrNull() ?: return@LaunchedEffect
        delay((nextExpiry - System.currentTimeMillis()).coerceAtLeast(100L))
        nowMillis = System.currentTimeMillis()
    }

    // Clear focus when filter changes
    // Track previous filter to only clear on actual changes, not initial composition
    var previousFilter by remember { mutableStateOf<StatusFilter?>(null) }
    LaunchedEffect(statusFilter) {
        if (previousFilter != null) {
            focusManager.clearFocus()
            onClearFocus()
            onDismissUndo()
        }
        previousFilter = statusFilter
    }

    // Clear focus when app is backgrounded
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        focusManager.clearFocus()
        onClearFocus()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        nowMillis = System.currentTimeMillis()
    }

    // Clear focus on back press
    BackHandler(enabled = focusId != null) {
        focusManager.clearFocus()
        onClearFocus()
    }

    BackHandler(enabled = focusId == null && searchOpen) {
        searchQuery = ""
        searchOpen = false
    }

    // Clear focus when keyboard is dismissed (e.g. swipe down)
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (!imeVisible && focusId != null) {
            focusManager.clearFocus()
            onClearFocus()
        }
    }

    // Use rememberUpdatedState so snapshotFlow reads current list, not a stale capture
    val currentEntries by rememberUpdatedState(entriesByFilter[statusFilter] ?: emptyList())

    // Scroll to focused todo, keeping it clear of the floating bar that overlays the list's bottom padding
    LaunchedEffect(focusId, pagerState.settledPage) {
        if (focusId != null) {
            val index = snapshotFlow {
                currentEntries.indexOfFirst { it is ListEntry.Row && it.todo.id == focusId }
            }.first { it >= 0 }
            val listState = listStates[pagerState.settledPage]
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == index }
            if (item == null || item.offset < info.viewportStartOffset) {
                listState.scrollToItem(index)
            } else {
                listState.scrollBy(overflowPastBar(listState, index))
            }
            // The keyboard shrinking the viewport, or the row growing, can push it under the bar.
            snapshotFlow {
                val layout = listState.layoutInfo
                layout.viewportEndOffset - layout.afterContentPadding to
                    layout.visibleItemsInfo.firstOrNull { it.index == index }?.size
            }.drop(1).collect { listState.scrollBy(overflowPastBar(listState, index)) }
        }
    }

    LaunchedEffect(pullToRefreshState) {
        var wasAtThreshold = false
        snapshotFlow { pullToRefreshState.distanceFraction }
            .collect { fraction ->
                val isAtThreshold = fraction >= 1f
                if (isAtThreshold && !wasAtThreshold) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                wasAtThreshold = isAtThreshold
            }
    }

    val clearAll: (() -> Unit)? =
        if (statusFilter == StatusFilter.DONE && filteredTodos.isNotEmpty() && onClearAllDone != null) {
            {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                focusManager.clearFocus()
                onClearFocus()
                onClearAllDone()
            }
        } else {
            null
        }

    Scaffold(
        modifier = modifier.imePadding(),
        topBar = {
            Box(modifier = Modifier.statusBarsPadding()) {
                TopBar(
                    title = statusFilter.displayName(),
                    count = if (todos == null) null else filteredTodos.size,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    searchOpen = searchOpen,
                    onSearchOpenChange = { searchOpen = it },
                    isSignedIn = isSignedIn,
                    profilePhotoUrl = profilePhotoUrl,
                    onAccountClick = onAccountClick,
                    onSignInClick = onSignInClick,
                    onClearAll = clearAll,
                )
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                AnimatedVisibility(
                    visible = undoVisible,
                    enter = slideInVertically { it / 2 } + fadeIn(),
                    exit = slideOutVertically { it / 2 } + fadeOut(),
                ) {
                    UndoSnackbar(
                        label = undoLabel,
                        onUndo = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onUndoLastDelete?.invoke()
                        },
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val toolbarBaseSlop = LocalViewConfiguration.current
                    val toolbarSlop = remember(toolbarBaseSlop) { inflatedSlop(toolbarBaseSlop) }
                    CompositionLocalProvider(LocalViewConfiguration provides toolbarSlop) {
                        FilterToolbar(
                            selected = statusFilter,
                            onSelect = { filter ->
                                scope.launch { pagerState.animateScrollToPage(TAB_ORDER.indexOf(filter)) }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .filterSwipe(pagerState, scope)
                        )
                    }
                    FloatingActionButton(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            searchQuery = ""
                            searchOpen = false
                            scope.launch { pagerState.animateScrollToPage(DEFAULT_TAB) }
                            onCreateTodo()
                        },
                        shape = RoundedCornerShape(16.dp),
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
                        modifier = Modifier
                            .size(64.dp)
                            .focusProperties { canFocus = false }
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add todo")
                    }
                }
            }
        }
    ) { innerPadding ->
        // filterSwipe reads the inflated slop; the pager's list content is restored to base
        // below, so vertical scroll stays responsive while paging needs a clearly horizontal drag.
        val baseSlop = LocalViewConfiguration.current
        val pagerSlop = remember(baseSlop) { inflatedSlop(baseSlop) }
        CompositionLocalProvider(LocalViewConfiguration provides pagerSlop) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .filterSwipe(pagerState, scope)
        ) {
            CompositionLocalProvider(LocalViewConfiguration provides baseSlop) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier.fillMaxSize()
            ) { pageIndex ->
                val pageFilter = TAB_ORDER[pageIndex]
                val pageEntries = entriesByFilter[pageFilter] ?: emptyList()

                PullToRefreshBox(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch {
                            isRefreshing = true
                            nowMillis = System.currentTimeMillis()
                            delay(500)
                            isRefreshing = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            focusManager.clearFocus()
                        }
                ) {
                    if (todos == null) {
                        // Pre-first-snapshot: spinner, not a false "no todos".
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    } else if (pageEntries.isEmpty()) {
                        Box(Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
                            EmptyState(filter = pageFilter, searchQuery = searchQuery)
                        }
                    } else {
                        LazyColumn(
                            state = listStates[pageIndex],
                            contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                pageEntries,
                                key = { entry ->
                                    when (entry) {
                                        is ListEntry.Header -> "header:${entry.label}"
                                        is ListEntry.Row -> entry.todo.id
                                    }
                                },
                                contentType = { entry -> if (entry is ListEntry.Header) "header" else "todo" },
                            ) { entry ->
                                when (entry) {
                                    is ListEntry.Header -> GroupHeader(entry.label, entry.count, Modifier.animateItem())
                                    is ListEntry.Row -> {
                                        val todo = entry.todo
                                        val snoozed = todo.isSnoozed(nowMillis)
                                        TodoItem(
                                            todo = todo,
                                            shouldRequestFocus = todo.id == focusId,
                                            snoozed = snoozed,
                                            isInFastComposeMode = fastCreationId != null && todo.id == focusId,
                                            onFocused = onTodoFocused,
                                            onBlurred = onTodoBlurred,
                                            onUpdate = { updatedTodo, touchModifiedAt ->
                                                // Snoozed-ness, not raw snoozeUntil: a swipe-reactivate
                                                // only moves that, but so does editing an unsnoozed row —
                                                // and that must not kick you out of the editor.
                                                if (updatedTodo.state != todo.state ||
                                                    updatedTodo.isSnoozed(nowMillis) != snoozed
                                                ) {
                                                    focusManager.clearFocus()
                                                    onClearFocus()
                                                }
                                                onUpdateTodo(updatedTodo, touchModifiedAt)
                                            },
                                            onDelete = {
                                                focusManager.clearFocus()
                                                onClearFocus()
                                                onDeleteTodo(todo)
                                            },
                                            onSwipeDelete = {
                                                focusManager.clearFocus()
                                                onClearFocus()
                                                onSwipeDeleteTodo(todo)
                                            },
                                            onComplete = {
                                                focusManager.clearFocus()
                                                onClearFocus()
                                                onCompleteTodo(todo)
                                            },
                                            onSnooze = {
                                                focusManager.clearFocus()
                                                onClearFocus()
                                                onRequestSnooze(todo)
                                            },
                                            onTogglePin = if (todo.state == TodoState.DONE) {
                                                null
                                            } else {
                                                {
                                                    // Only an active row drops its was-snoozed marker.
                                                    val snoozeUntil =
                                                        if (snoozed) todo.snoozeUntil else null
                                                    onUpdateTodo(
                                                        todo.copy(pinned = !todo.pinned, snoozeUntil = snoozeUntil),
                                                        true,
                                                    )
                                                }
                                            },
                                            onCreateNewTodo = onCreateTodo,
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
        }
        }
    }
}

/** A swipe background's soft and armed (past the threshold) fills, icon and label. */
private data class SwipeLook(
    val soft: Color,
    val onSoft: Color,
    val armed: Color,
    val onArmed: Color,
    val icon: ImageVector,
    val label: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoItem(
    todo: Todo,
    shouldRequestFocus: Boolean,
    // Derived by the caller from its ticking clock.
    snoozed: Boolean = false,
    isInFastComposeMode: Boolean = false,
    onFocused: (String) -> Unit,
    onBlurred: (String) -> Unit = {},
    onUpdate: (Todo, Boolean) -> Unit,
    onDelete: () -> Unit,
    onSwipeDelete: () -> Unit = onDelete,
    onComplete: () -> Unit = { onUpdate(todo.copy(state = TodoState.DONE, snoozeUntil = null), true) },
    onSnooze: () -> Unit,
    // Null disables pinning (passed for every row but a done one).
    onTogglePin: (() -> Unit)? = null,
    onCreateNewTodo: () -> Unit,
    modifier: Modifier = Modifier
) {
    // At rest the row is a plain Text; tapping swaps in the editor (`editing` gates it).
    var editing by remember(todo.id) { mutableStateOf(false) }
    var fieldValue by remember(todo.id) { mutableStateOf(TextFieldValue(todo.text)) }
    // Tap offset into the text, or null if the tap missed it (→ caret to end).
    var caretFromTap by remember(todo.id) { mutableStateOf<Int?>(null) }
    var textLayoutResult by remember(todo.id) { mutableStateOf<TextLayoutResult?>(null) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    var hasFocused by remember { mutableStateOf(false) }
    // Reset each time the editor opens; gates the blur handler so the field's initial unfocused callback isn't read as a blur.
    var focusedThisSession by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // rememberUpdatedState: the gesture blocks are keyed on todo.id, so they'd otherwise capture stale closures.
    val currentTodo by rememberUpdatedState(todo)
    val currentSnoozed by rememberUpdatedState(snoozed)
    val currentOnUpdate by rememberUpdatedState(onUpdate)
    val currentOnDelete by rememberUpdatedState(onDelete)
    val currentOnSwipeDelete by rememberUpdatedState(onSwipeDelete)
    val currentOnComplete by rememberUpdatedState(onComplete)
    val currentFieldText by rememberUpdatedState(fieldValue.text)
    val currentOnSnooze by rememberUpdatedState(onSnooze)
    // Long-press pin: haptic + toggle, no-op when onTogglePin is null.
    val currentOnPinLongPress by rememberUpdatedState<() -> Unit>({
        onTogglePin?.let { toggle ->
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            toggle()
        }
    })

    // Enter edit mode with the caret at [caret] (clamped).
    val enterEdit: (Int) -> Unit = { caret ->
        val full = currentTodo.text
        fieldValue = TextFieldValue(full, TextRange(caret.coerceIn(0, full.length)))
        focusedThisSession = false
        editing = true
    }

    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.4f },
    )
    // The row swipe reads this inflated slop; base is restored inside the row for taps/text.
    val baseViewConfig = LocalViewConfiguration.current
    val swipeViewConfig = remember(baseViewConfig) { inflatedSlop(baseViewConfig) }

    // Programmatic focus (new todo, scroll-to-focus): open the editor, caret at end.
    // !editing so it never clobbers a tap-opened editor's caret with end.
    LaunchedEffect(shouldRequestFocus) {
        if (shouldRequestFocus && !hasFocused && !editing) {
            fieldValue = TextFieldValue(todo.text, TextRange(todo.text.length))
            focusedThisSession = false
            editing = true
        }
    }
    LaunchedEffect(editing) {
        if (editing) {
            // Wait a frame so the just-composed field's FocusRequester is attached.
            withFrameNanos { }
            runCatching { focusRequester.requestFocus() }
        }
    }

    CompositionLocalProvider(LocalViewConfiguration provides swipeViewConfig) {
    SwipeToDismissBox(
        state = dismissState,
        // Consume the whole gesture in the edge strip so a row-dismiss never starts there (leaves the OS back gesture room).
        modifier = modifier.pointerInput(Unit) {
            val edgePx = EDGE_REJECT_DP.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial
                )
                val nearEdge = down.position.x < edgePx ||
                    down.position.x > size.width - edgePx
                if (nearEdge) {
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.none { it.pressed }) break
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        },
        onDismiss = { direction ->
            val done = currentTodo.state == TodoState.DONE
            when (direction) {
                SwipeToDismissBoxValue.EndToStart ->
                    if (done) currentOnSwipeDelete() else currentOnComplete()

                SwipeToDismissBoxValue.StartToEnd ->
                    if (done || currentSnoozed) {
                        currentOnUpdate(
                            currentTodo.copy(state = TodoState.ACTIVE, snoozeUntil = null),
                            true,
                        )
                    } else {
                        currentOnSnooze()
                    }

                else -> Unit
            }
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            scope.launch { dismissState.snapTo(SwipeToDismissBoxValue.Settled) }
        },
        backgroundContent = {
            val direction by remember { derivedStateOf { dismissState.dismissDirection } }
            if (direction != SwipeToDismissBoxValue.Settled) {
                val colorScheme = MaterialTheme.colorScheme
                val done = todo.state == TodoState.DONE
                val look = when (direction) {
                    SwipeToDismissBoxValue.EndToStart ->
                        if (done) {
                            SwipeLook(
                                colorScheme.errorContainer, colorScheme.onErrorContainer,
                                colorScheme.error, colorScheme.onError, Icons.Rounded.Delete, "Delete",
                            )
                        } else {
                            SwipeLook(
                                colorScheme.primaryContainer, colorScheme.onPrimaryContainer,
                                colorScheme.primary, colorScheme.onPrimary, Icons.Rounded.Check, "Done",
                            )
                        }

                    else ->
                        if (done || snoozed) {
                            SwipeLook(
                                colorScheme.secondaryContainer, colorScheme.onSecondaryContainer,
                                colorScheme.secondary, colorScheme.onSecondary, Icons.Rounded.Restore, "Restore",
                            )
                        } else {
                            SwipeLook(
                                colorScheme.tertiaryContainer, colorScheme.onTertiaryContainer,
                                colorScheme.tertiary, colorScheme.onTertiary, Icons.Rounded.Schedule, "Snooze",
                            )
                        }
                }
                val armed by remember { derivedStateOf { dismissState.targetValue != SwipeToDismissBoxValue.Settled } }
                LaunchedEffect(armed) {
                    if (armed) hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
                val background by animateColorAsState(if (armed) look.armed else look.soft, label = "swipeFill")
                val content by animateColorAsState(if (armed) look.onArmed else look.onSoft, label = "swipeInk")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (direction == SwipeToDismissBoxValue.EndToStart) {
                        Arrangement.spacedBy(8.dp, Alignment.End)
                    } else {
                        Arrangement.spacedBy(8.dp, Alignment.Start)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(background)
                        .padding(horizontal = 24.dp)
                ) {
                    if (direction == SwipeToDismissBoxValue.EndToStart) {
                        Text(look.label, color = content, style = MaterialTheme.typography.labelLarge)
                        Icon(look.icon, contentDescription = null, tint = content)
                    } else {
                        Icon(look.icon, contentDescription = null, tint = content)
                        Text(look.label, color = content, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
        enableDismissFromStartToEnd = true
    ) {
        // Restore base slop inside the row so taps/long-press aren't dulled by the swipe's inflated slop.
        CompositionLocalProvider(LocalViewConfiguration provides baseViewConfig) {
        val displaced by remember { derivedStateOf { dismissState.dismissDirection != SwipeToDismissBoxValue.Settled } }
        val cornerRadius = animateDpAsState(if (displaced) 16.dp else 0.dp, tween(150), label = "rowCorner")
        val rowColor = MaterialTheme.colorScheme.surfaceContainerLowest
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .drawBehind {
                    val radius = cornerRadius.value.toPx()
                    drawRoundRect(rowColor, cornerRadius = CornerRadius(radius, radius))
                }
                .semantics {
                    val done = todo.state == TodoState.DONE
                    val restore = CustomAccessibilityAction("Restore") {
                        currentOnUpdate(currentTodo.copy(state = TodoState.ACTIVE, snoozeUntil = null), true)
                        true
                    }
                    customActions = if (done) {
                        listOf(restore, CustomAccessibilityAction("Delete") { currentOnSwipeDelete(); true })
                    } else {
                        listOf(
                            CustomAccessibilityAction("Complete") { currentOnComplete(); true },
                            if (snoozed) restore else CustomAccessibilityAction("Snooze") { currentOnSnooze(); true },
                        )
                    }
                }
                // combinedClickable (not detectTapGestures) so taps arbitrate with the swipe/scroll parents.
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (!editing) enterEdit(caretFromTap ?: currentTodo.text.length)
                        caretFromTap = null
                    },
                    onLongClick = {
                        currentOnPinLongPress()
                        caretFromTap = null
                    },
                )
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            val colorScheme = MaterialTheme.colorScheme
            // Pinned displaces the state dot only on an active row; elsewhere it just
            // tints it. A done row is already primary, so it needs no branch.
            val showsPin = todo.pinned && todo.state != TodoState.DONE && !snoozed
            val (stateIcon, stateIconTint) = when {
                todo.state == TodoState.DONE -> Icons.Rounded.CheckCircle to colorScheme.primary
                snoozed -> Icons.Rounded.Schedule to
                    if (todo.pinned) colorScheme.primary else colorScheme.tertiary

                showsPin -> Icons.Outlined.PushPin to colorScheme.primary
                todo.snoozeUntil != null -> Icons.Outlined.Schedule to colorScheme.tertiary  // Was snoozed, now active
                else -> Icons.Rounded.RadioButtonUnchecked to colorScheme.onSurfaceVariant
            }
            // Shared by the display Text and editor so entering edit doesn't shift the text.
            val editorTextStyle = MaterialTheme.typography.bodyLarge.copy(
                color = if (todo.state == TodoState.DONE) colorScheme.onSurfaceVariant
                else colorScheme.onSurface,
                textDecoration = if (todo.state == TodoState.DONE)
                    TextDecoration.LineThrough else TextDecoration.None,
            )
            val placeholderTextStyle = MaterialTheme.typography.bodyLarge.copy(
                color = colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = stateIcon,
                contentDescription = if (todo.pinned) "Pinned" else null,
                tint = stateIconTint,
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                if (editing) {
                    BasicTextField(
                        value = fieldValue,
                        onValueChange = { newValue ->
                            if (!newValue.text.contains('\n')) {
                                fieldValue = newValue
                                // Only a plainly-active row drops its was-snoozed marker;
                                // clearing it on a snoozed row would unsnooze it.
                                val plainlyActive =
                                    currentTodo.state != TodoState.DONE && !currentSnoozed
                                val wasSnoozeUntil = currentTodo.snoozeUntil
                                val edited = if (plainlyActive && wasSnoozeUntil != null) {
                                    // Pin modifiedAt to the old unsnooze time so dropping
                                    // snoozeUntil doesn't sink the row to a stale modifiedAt.
                                    currentTodo.copy(
                                        text = newValue.text,
                                        snoozeUntil = null,
                                        modifiedAt = LocalDateTimeUtil.toEpochMillis(wasSnoozeUntil)
                                    )
                                } else {
                                    currentTodo.copy(text = newValue.text)
                                }
                                currentOnUpdate(edited, false)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    hasFocused = true
                                    focusedThisSession = true
                                    onFocused(todo.id)
                                } else if (focusedThisSession) {
                                    editing = false
                                    onBlurred(todo.id)
                                    if (currentFieldText.isEmpty()) {
                                        currentOnDelete()
                                    }
                                }
                            },
                        textStyle = editorTextStyle,
                        cursorBrush = SolidColor(colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            imeAction = if (isInFastComposeMode && fieldValue.text.isNotEmpty())
                                ImeAction.Next else ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = {
                                if (fieldValue.text.isNotEmpty()) {
                                    onCreateNewTodo()
                                }
                            },
                            onDone = {
                                focusManager.clearFocus()
                            }
                        ),
                        decorationBox = { innerTextField ->
                            if (fieldValue.text.isEmpty()) {
                                Text(text = "Enter todo...", style = placeholderTextStyle)
                            }
                            innerTextField()
                        }
                    )
                } else {
                    // Passively record the tap's caret offset (Initial pass, no consume) so onClick opens there.
                    Text(
                        text = todo.text.ifEmpty { "Enter todo..." },
                        style = if (todo.text.isEmpty()) placeholderTextStyle else editorTextStyle,
                        onTextLayout = { textLayoutResult = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(todo.id) {
                                // Passive Initial-pass observer: never reads isConsumed (the parent
                                // clickable consumes the down, which would falsely read as cancel),
                                // only drops the caret if the gesture turns into a drag (slop exceeded).
                                awaitEachGesture {
                                    val down = awaitFirstDown(
                                        requireUnconsumed = false,
                                        pass = PointerEventPass.Initial,
                                    )
                                    caretFromTap =
                                        textLayoutResult?.getOffsetForPosition(down.position)
                                    val slop = viewConfiguration.touchSlop
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change =
                                            event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break
                                        if ((change.position - down.position).getDistance() > slop) {
                                            caretFromTap = null
                                            break
                                        }
                                    }
                                }
                            }
                    )
                }
                if (todo.snoozeUntil != null) {
                    val snoozeMillis = LocalDateTimeUtil.toEpochMillis(todo.snoozeUntil)
                    // Re-key on the 24h boundary (where formatSnoozeTime's format flips) so an aged row re-formats; still memoized for scroll.
                    val within24h =
                        abs(snoozeMillis - System.currentTimeMillis()) < DAY_MILLIS
                    val snoozeLabel = remember(snoozeMillis, within24h, snoozed) {
                        val time = LocalDateTimeUtil.formatSnoozeTime(snoozeMillis, context)
                        if (snoozed) time else "Unsnoozed · $time"
                    }
                    Text(
                        text = snoozeLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (snoozed) colorScheme.tertiary else colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        }
    }
    }
}

@Preview(showBackground = true)
@Composable
private fun TodoScreenEmptyPreview() {
    LatrTheme(dynamicColor = false) {
        TodoScreenContent(
            todos = emptyList(),
            onCreateTodo = {},
            onUpdateTodo = { _, _ -> },
            onDeleteTodo = {},
            onTodoFocused = {},
            onRequestSnooze = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TodoScreenWithItemsPreview() {
    LatrTheme(dynamicColor = false) {
        TodoScreenContent(
            todos = listOf(
                Todo(id = "1", text = "Buy groceries", pinned = true),
                Todo(id = "2", text = "Walk the dog", snoozeUntil = "2024-01-15T10:00:00"),
                Todo(id = "3", text = "Finish project report")
            ),
            onCreateTodo = {},
            onUpdateTodo = { _, _ -> },
            onDeleteTodo = {},
            onTodoFocused = {},
            onRequestSnooze = {},
            undoVisible = true,
            undoLabel = "Completed",
        )
    }
}

@Preview(showBackground = true, name = "Toolbar")
@Composable
private fun FilterToolbarPreview() {
    LatrTheme(dynamicColor = false) {
        FilterToolbar(selected = StatusFilter.ACTIVE, onSelect = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun TodoItemPreview() {
    LatrTheme(dynamicColor = false) {
        TodoItem(
            todo = Todo(id = "1", text = "Sample todo item"),
            shouldRequestFocus = false,
            onFocused = {},
            onUpdate = { _, _ -> },
            onDelete = {},
            onSnooze = {},
            onCreateNewTodo = {}
        )
    }
}

@Preview(showBackground = true, name = "Todo - Done")
@Composable
private fun TodoItemDonePreview() {
    LatrTheme(dynamicColor = false) {
        TodoItem(
            todo = Todo(id = "1", text = "Completed task", state = TodoState.DONE),
            shouldRequestFocus = false,
            onFocused = {},
            onUpdate = { _, _ -> },
            onDelete = {},
            onSnooze = {},
            onCreateNewTodo = {}
        )
    }
}

@Preview(showBackground = true, name = "Todo - Snoozed")
@Composable
private fun TodoItemSnoozedPreview() {
    LatrTheme(dynamicColor = false) {
        TodoItem(
            todo = Todo(
                id = "1",
                text = "Snoozed task",
                snoozeUntil = LocalDateTimeUtil.fromEpochMillis(
                    System.currentTimeMillis() + 2 * 60 * 60 * 1000
                )
            ),
            shouldRequestFocus = false,
            snoozed = true,
            onFocused = {},
            onUpdate = { _, _ -> },
            onDelete = {},
            onSnooze = {},
            onCreateNewTodo = {}
        )
    }
}

@Preview(showBackground = true, name = "Todo - Was Snoozed (now active)")
@Composable
private fun TodoItemWasSnoozedPreview() {
    LatrTheme(dynamicColor = false) {
        TodoItem(
            todo = Todo(
                id = "1",
                text = "Unsnoozed task",
                state = TodoState.ACTIVE,
                snoozeUntil = LocalDateTimeUtil.fromEpochMillis(
                    System.currentTimeMillis() - 60 * 60 * 1000
                )
            ),
            shouldRequestFocus = false,
            onFocused = {},
            onUpdate = { _, _ -> },
            onDelete = {},
            onSnooze = {},
            onCreateNewTodo = {}
        )
    }
}

@Preview(showBackground = true, name = "Screen - Snoozed Filter")
@Composable
private fun TodoScreenSnoozedPreview() {
    LatrTheme(dynamicColor = false) {
        TodoScreenContent(
            todos = listOf(
                Todo(id = "1", text = "Call mom", snoozeUntil = "2030-01-20T09:00:00"),
                Todo(id = "2", text = "Review PR", snoozeUntil = "2030-01-16T14:00:00"),
            ),
            onCreateTodo = {},
            onUpdateTodo = { _, _ -> },
            onDeleteTodo = {},
            onTodoFocused = {},
            onRequestSnooze = {},
            initialStatusFilter = StatusFilter.SNOOZED
        )
    }
}

@Preview(showBackground = true, name = "Screen - Done Filter")
@Composable
private fun TodoScreenDonePreview() {
    LatrTheme(dynamicColor = false) {
        TodoScreenContent(
            todos = listOf(
                Todo(id = "1", text = "Buy groceries", state = TodoState.DONE),
                Todo(id = "2", text = "Send email", state = TodoState.DONE),
            ),
            onCreateTodo = {},
            onUpdateTodo = { _, _ -> },
            onDeleteTodo = {},
            onTodoFocused = {},
            onRequestSnooze = {},
            onClearAllDone = {},
            initialStatusFilter = StatusFilter.DONE
        )
    }
}
