<script lang="ts">
import { EditorView } from "@codemirror/view";
import { onMount } from "svelte";
import BottomDock from "../lib/components/bottom-dock.svelte";
import ComposeRow from "../lib/components/compose-row.svelte";
import { FILTER_META } from "../lib/components/filters";
import ListHeader from "../lib/components/list-header.svelte";
import ListSkeleton from "../lib/components/list-skeleton.svelte";
import MobileAppBar from "../lib/components/mobile-app-bar.svelte";
import Sidebar from "../lib/components/sidebar.svelte";
import TodoList from "../lib/components/todo-list.svelte";
import UndoSnackbar from "../lib/components/undo-snackbar.svelte";
import { getAppState } from "../lib/utils/app-state.svelte";
import { AuthState, setAuthState } from "../lib/utils/auth.svelte";
import {
  hasModifier,
  isCommandChord,
  isEditableTarget,
} from "../lib/utils/keyboard";
import { FILTERS, matchesFilter, rankBySearch } from "../lib/utils/todo";

const SIDEBAR_COLLAPSED_KEY = "latr:sidebar:v1";

// ⌘/Ctrl+key clicks the focused (or hovered) row's data-action button.
// Actions are tried in order, so S unsnoozes a snoozed row and snoozes any other.
const SHORTCUTS: Record<string, string[]> = {
  d: ["primary"],
  s: ["unsnooze", "snooze"],
  k: ["pin"],
  backspace: ["delete"],
};

const app = getAppState();
setAuthState(new AuthState(app.holder));

let collapsed = $state(false);
let searching = $state(false);
let compose: ComposeRow;
let listHeader: ListHeader;

const counts = $derived({
  ACTIVE: app.todos.filter((t) => matchesFilter(t, "ACTIVE", app.now)).length,
  SNOOZED: app.todos.filter((t) => matchesFilter(t, "SNOOZED", app.now)).length,
});
const visibleCount = $derived.by(() => {
  const filtered = app.todos.filter((t) =>
    matchesFilter(t, app.filter, app.now),
  );
  return app.search.trim().length > 0
    ? rankBySearch(filtered, app.search).length
    : filtered.length;
});
const clearAll = $derived(
  app.filter === "DONE" && app.todos.some((t) => t.state === "DONE")
    ? app.clearAllDone
    : null,
);
const title = $derived(FILTER_META[app.filter].label);

onMount(() => {
  if (localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === "1") collapsed = true;
});

function toggleCollapsed() {
  collapsed = !collapsed;
  localStorage.setItem(SIDEBAR_COLLAPSED_KEY, collapsed ? "1" : "0");
}

function onSearchKey(e: KeyboardEvent) {
  if (e.key !== "/" || hasModifier(e)) return;
  if (isEditableTarget(e.target)) return;
  e.preventDefault();
  listHeader.focusSearch();
}

function onFilterKey(e: KeyboardEvent) {
  if (isEditableTarget(e.target) || hasModifier(e)) return;
  const index = Number.parseInt(e.key, 10);
  if (index >= 1 && index <= FILTERS.length) {
    e.preventDefault();
    app.setFilter(FILTERS[index - 1]);
  }
}

function onArrowKey(e: KeyboardEvent) {
  if (e.key !== "ArrowUp" && e.key !== "ArrowDown") return;
  if (hasModifier(e)) return;
  const target = e.target as HTMLElement | null;
  const row = target?.closest<HTMLElement>("[data-todo-id]");
  const rows = Array.from(
    document.querySelectorAll<HTMLElement>("[data-todo-id]"),
  );
  if (rows.length === 0) return;

  // Not inside a todo row: wake from compose/search into the first/last
  // row if the current target isn't something that owns arrow keys.
  if (!row) {
    if (isEditableTarget(target)) return;
    const pick = e.key === "ArrowDown" ? rows[0] : rows[rows.length - 1];
    const id = pick.getAttribute("data-todo-id");
    if (id) app.setFocus(id);
    e.preventDefault();
    return;
  }

  // Inside a todo row's editor: only hand off to row-nav when the caret
  // has no logical newline in the direction of travel.
  if (!target) return;
  const view = EditorView.findFromDOM(target);
  if (!view) return;
  const { from, to } = view.state.selection.main;
  if (e.key === "ArrowUp") {
    if (view.state.doc.sliceString(0, from).includes("\n")) return;
  } else if (view.state.doc.sliceString(to).includes("\n")) {
    return;
  }
  const index = rows.indexOf(row);
  const nextIndex = e.key === "ArrowUp" ? index - 1 : index + 1;
  if (nextIndex < 0 || nextIndex >= rows.length) return;
  const id = rows[nextIndex].getAttribute("data-todo-id");
  if (id) {
    app.setFocus(id);
    e.preventDefault();
  }
}

function onShortcutKey(e: KeyboardEvent) {
  if (!isCommandChord(e) || e.shiftKey || e.altKey) return;
  const actions = SHORTCUTS[e.key.toLowerCase()];
  if (!actions) return;
  // In a text box ⌘⌫ deletes the line, so delete only reaches a hovered row.
  if (actions.includes("delete") && isEditableTarget(e.target)) return;
  const row = app.focusId
    ? document.querySelector(`[data-todo-id="${app.focusId}"]`)
    : document.querySelector("[data-todo-id]:hover");
  const button = actions
    .map((action) =>
      row?.querySelector<HTMLButtonElement>(`[data-action="${action}"]`),
    )
    .find(Boolean);
  if (!button) return;
  e.preventDefault();
  e.stopPropagation();
  // Auto-repeat is swallowed, not acted on.
  if (e.repeat) return;
  button.click();
}

// ⌘/Ctrl+Z reverts the last action (delete, snooze, or complete) while the undo chip
// is visible. Captured even inside text inputs so a freshly-deleted row
// beats the browser's native undo of an unrelated edit.
function onUndoKey(e: KeyboardEvent) {
  if (!app.lastUndo) return;
  if (!isCommandChord(e) || e.shiftKey || e.altKey) return;
  if (e.key.toLowerCase() !== "z") return;
  e.preventDefault();
  e.stopPropagation();
  app.undo();
}

function onKey(e: KeyboardEvent) {
  onSearchKey(e);
  onFilterKey(e);
  onArrowKey(e);
}

function onCaptureKey(e: KeyboardEvent) {
  onShortcutKey(e);
  onUndoKey(e);
}

function composeNew() {
  app.setFilter("ACTIVE");
  app.setSearch("");
  searching = false;
  window.scrollTo({ top: 0 });
  compose.focus();
}
</script>

<svelte:head>
  <title>Latr</title>
</svelte:head>

<!-- Capture phase so the shortcuts run before descendant listeners and before the browser's default action. -->
<svelte:window onkeydown={onKey} onkeydowncapture={onCaptureKey} />

<div class="min-h-dvh">
  <Sidebar
    filter={app.filter}
    onFilter={app.setFilter}
    {counts}
    {collapsed}
    onToggleCollapsed={toggleCollapsed}
  />
  <main
    style:--sidebar-reserved={collapsed ? "3.5rem" : "15rem"}
    class="flex flex-col min-h-dvh min-w-0 md:pl-[var(--sidebar-reserved)] transition-[padding-left] duration-200 ease-out"
  >
    <MobileAppBar
      {title}
      count={visibleCount}
      search={app.search}
      onSearch={app.setSearch}
      {searching}
      onSearching={(value) => {
        searching = value;
      }}
      onClearAll={clearAll}
    />
    <div
      class="flex-1 w-full max-w-[680px] mx-auto max-md:pl-[max(0.5rem,env(safe-area-inset-left))] max-md:pr-[max(0.5rem,env(safe-area-inset-right))] md:box-content md:px-12 pb-40 md:pb-32"
    >
      <ListHeader
        bind:this={listHeader}
        {title}
        count={visibleCount}
        search={app.search}
        onSearch={app.setSearch}
        onClearAll={clearAll}
      />
      <div class="space-y-5 pt-1 md:pt-2">
        <ComposeRow
          bind:this={compose}
          onCreated={() => {
            searching = false;
          }}
        />
        {#if app.hydrated}
          <TodoList />
        {:else}
          <ListSkeleton />
        {/if}
      </div>
    </div>
    <UndoSnackbar />
    <BottomDock
      filter={app.filter}
      onFilter={app.setFilter}
      onCompose={composeNew}
    />
  </main>
</div>
