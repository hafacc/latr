"use client";

import {
  type ReactElement,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { LuPlus } from "react-icons/lu";
import { AuthProvider } from "../components/auth-menu";
import ComposeRow from "../components/compose-row";
import { DOCK_ORDER, FILTER_META } from "../components/filters";
import { ListHeader, MobileAppBar } from "../components/list-header";
import Sidebar from "../components/sidebar";
import TodoList, { ListSkeleton } from "../components/todo-list";
import UndoSnackbar from "../components/undo-snackbar";
import { isEditableTarget } from "../utils/keyboard";
import { useTodos } from "../utils/store";
import {
  FILTERS,
  type Filter,
  matchesFilter,
  rankBySearch,
} from "../utils/todo";

const SIDEBAR_COLLAPSED_KEY = "latr:sidebar:v1";

export default function Page(): ReactElement {
  const {
    hydrated,
    todos,
    now,
    filter,
    search,
    focusId,
    lastUndo,
    setFilter,
    setSearch,
    setFocus,
    undo,
    clearAllDone,
  } = useTodos();
  const [collapsed, setCollapsed] = useState(false);
  const [searching, setSearching] = useState(false);
  const composeRef = useRef<HTMLInputElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);

  const counts = useMemo(
    () => ({
      ACTIVE: todos.filter((t) => matchesFilter(t, "ACTIVE", now)).length,
      SNOOZED: todos.filter((t) => matchesFilter(t, "SNOOZED", now)).length,
    }),
    [todos, now],
  );
  const visibleCount = useMemo(() => {
    const filtered = todos.filter((t) => matchesFilter(t, filter, now));
    return search.trim().length > 0
      ? rankBySearch(filtered, search).length
      : filtered.length;
  }, [todos, filter, search, now]);
  const hasDone = todos.some((t) => t.state === "DONE");
  const clearAllCb = useCallback(() => clearAllDone(), [clearAllDone]);
  const clearAll = filter === "DONE" && hasDone ? clearAllCb : null;

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key !== "/") return;
      if (isEditableTarget(e.target)) return;
      e.preventDefault();
      searchRef.current?.focus();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  useEffect(() => {
    if (localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === "1") setCollapsed(true);
  }, []);

  useEffect(() => {
    localStorage.setItem(SIDEBAR_COLLAPSED_KEY, collapsed ? "1" : "0");
  }, [collapsed]);

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (isEditableTarget(e.target)) return;
      const idx = Number.parseInt(e.key, 10);
      if (idx >= 1 && idx <= FILTERS.length) {
        e.preventDefault();
        setFilter(FILTERS[idx - 1]);
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [setFilter]);

  useEffect(() => {
    // ⌘/Ctrl+key dispatches the focused (or hovered) row's data-action on keydown; e.repeat filters auto-repeat.
    // Backspace (not X) so ⌘+X stays as cut.
    const SHORTCUTS: Record<string, string> = {
      d: "primary",
      s: "snooze",
      u: "unsnooze",
      backspace: "delete",
    };
    function matchShortcut(e: KeyboardEvent): string | null {
      if (!(e.metaKey || e.ctrlKey) || e.altKey || e.shiftKey) return null;
      return SHORTCUTS[e.key.toLowerCase()] ?? null;
    }
    function onKeyDown(e: KeyboardEvent) {
      const action = matchShortcut(e);
      if (!action) return;
      e.preventDefault();
      e.stopPropagation();
      if (e.repeat) return;
      const row = focusId
        ? document.querySelector(`[data-todo-id="${focusId}"]`)
        : document.querySelector("[data-todo-id]:hover");
      const btn = row?.querySelector<HTMLButtonElement>(
        `[data-action="${action}"]`,
      );
      btn?.click();
    }
    // Capture phase so we run before descendant listeners and before the
    // browser's default action fires.
    window.addEventListener("keydown", onKeyDown, { capture: true });
    return () => {
      window.removeEventListener("keydown", onKeyDown, { capture: true });
    };
  }, [focusId]);

  useEffect(() => {
    // ⌘/Ctrl+Z reverts the last action (delete, snooze, or complete) while the undo chip
    // is visible. Captured even inside text inputs so a freshly-deleted row
    // beats the browser's native undo of an unrelated edit.
    if (!lastUndo) return;
    function onKey(e: KeyboardEvent) {
      if (!(e.metaKey || e.ctrlKey) || e.altKey || e.shiftKey) return;
      if (e.key.toLowerCase() !== "z") return;
      e.preventDefault();
      e.stopPropagation();
      undo();
    }
    window.addEventListener("keydown", onKey, { capture: true });
    return () =>
      window.removeEventListener("keydown", onKey, { capture: true });
  }, [lastUndo, undo]);

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key !== "ArrowUp" && e.key !== "ArrowDown") return;
      if (e.metaKey || e.ctrlKey || e.altKey) return;
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
        if (id) setFocus(id);
        e.preventDefault();
        return;
      }

      // Inside a todo row's textarea: only hand off to row-nav when the
      // caret has no logical newline in the direction of travel; otherwise
      // let the browser move the caret within the multi-line textarea.
      const ta = target as HTMLTextAreaElement;
      if (ta.tagName !== "TEXTAREA") return;
      const value = ta.value;
      if (e.key === "ArrowUp") {
        const before = value.slice(0, ta.selectionStart ?? 0);
        if (before.includes("\n")) return;
      } else {
        const after = value.slice(ta.selectionEnd ?? value.length);
        if (after.includes("\n")) return;
      }
      const idx = rows.indexOf(row);
      const nextIdx = e.key === "ArrowUp" ? idx - 1 : idx + 1;
      if (nextIdx < 0 || nextIdx >= rows.length) return;
      const id = rows[nextIdx].getAttribute("data-todo-id");
      if (id) {
        setFocus(id);
        e.preventDefault();
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [setFocus]);

  return (
    <AuthProvider>
      <div className="min-h-dvh">
        <Sidebar
          filter={filter}
          onFilter={setFilter}
          counts={counts}
          collapsed={collapsed}
          onToggleCollapsed={() => setCollapsed((v) => !v)}
        />
        <main
          style={
            {
              "--sidebar-reserved": collapsed ? "3.5rem" : "15rem",
            } as React.CSSProperties
          }
          className="flex flex-col min-h-dvh min-w-0 md:pl-[var(--sidebar-reserved)] transition-[padding-left] duration-200 ease-out"
        >
          <MobileAppBar
            title={FILTER_META[filter].label}
            count={visibleCount}
            search={search}
            onSearch={setSearch}
            searching={searching}
            onSearching={setSearching}
            onClearAll={clearAll}
          />
          <div className="flex-1 w-full max-w-[680px] mx-auto max-md:pl-[max(0.5rem,env(safe-area-inset-left))] max-md:pr-[max(0.5rem,env(safe-area-inset-right))] md:box-content md:px-12 pb-40 md:pb-32">
            <ListHeader
              title={FILTER_META[filter].label}
              count={visibleCount}
              search={search}
              onSearch={setSearch}
              searchRef={searchRef}
              onClearAll={clearAll}
            />
            <div className="space-y-5 pt-1 md:pt-2">
              <ComposeRow
                inputRef={composeRef}
                onCreated={() => setSearching(false)}
              />
              {hydrated ? <TodoList /> : <ListSkeleton />}
            </div>
          </div>
          <UndoSnackbar />
          <BottomDock
            filter={filter}
            onFilter={setFilter}
            onCompose={() => {
              setFilter("ACTIVE");
              setSearch("");
              setSearching(false);
              window.scrollTo({ top: 0 });
              composeRef.current?.focus();
            }}
          />
        </main>
      </div>
    </AuthProvider>
  );
}

function BottomDock({
  filter,
  onFilter,
  onCompose,
}: {
  filter: Filter;
  onFilter: (f: Filter) => void;
  onCompose: () => void;
}): ReactElement {
  return (
    <nav
      aria-label="Filters"
      className="md:hidden fixed inset-x-0 bottom-0 z-20 flex items-center justify-center gap-3 pl-[max(1rem,env(safe-area-inset-left))] pr-[max(1rem,env(safe-area-inset-right))] pt-2 pb-[max(env(safe-area-inset-bottom),16px)] pointer-events-none"
    >
      <div className="pointer-events-auto flex items-center gap-1 p-1.5 rounded-full bg-surface-raised shadow-dock">
        {DOCK_ORDER.map((f) => {
          const { label, icon: Icon } = FILTER_META[f];
          const active = f === filter;
          return (
            <button
              key={f}
              type="button"
              onClick={() => onFilter(f)}
              aria-pressed={active}
              aria-label={label}
              className={`h-11 flex items-center justify-center gap-2 rounded-full transition-all ${
                active
                  ? "px-4 bg-accent-soft text-accent-strong font-medium text-sm"
                  : "w-11 text-text-secondary"
              }`}
            >
              <Icon className="w-5 h-5 shrink-0" />
              {active && <span>{label}</span>}
            </button>
          );
        })}
      </div>
      <button
        type="button"
        onClick={onCompose}
        aria-label="Add a todo"
        className="pointer-events-auto w-14 h-14 flex items-center justify-center rounded-2xl bg-accent text-on-accent shadow-dock active:scale-95 transition-transform"
      >
        <LuPlus className="w-6 h-6" />
      </button>
    </nav>
  );
}
