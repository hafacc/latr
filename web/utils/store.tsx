"use client";

import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useReducer,
  useRef,
  useState,
  useSyncExternalStore,
} from "react";
import type {
  CommitUndoSnapshot,
  DevicePartition,
  QuickTime,
  Row,
  SnoozeSource,
} from "./snooze-suggest";
import {
  lastRow as computeLastRow,
  quickTimes as computeQuickTimes,
  rank as rankSnoozeRows,
} from "./snooze-suggest";
import { TodoStoreHolder } from "./store-holder";
import {
  epochToIso,
  type Filter,
  isoToEpoch,
  matchesFilter,
  newTodo,
  type Todo,
  withPinToggled,
} from "./todo";

// Most-recent-wins undo: "delete" restores via re-insert, "snooze"/"complete" via update.
export type UndoKind = "delete" | "snooze" | "complete";
export type UndoEntry = {
  kind: UndoKind;
  todos: Todo[];
  // Only set for "snooze": the learned-suggestion credit this pick added, so undo can retract it.
  snoozeUndo?: CommitUndoSnapshot;
};

export type UiState = {
  filter: Filter;
  search: string;
  focusId: string | null;
  lastUndo: UndoEntry | null;
  undoExpiresAt: number | null;
};

export type UiAction =
  | { type: "setFilter"; filter: Filter }
  | { type: "setSearch"; search: string }
  | { type: "setFocus"; id: string | null }
  | { type: "setUndo"; entry: UndoEntry }
  | { type: "clearUndo" };

export const initialUi: UiState = {
  filter: "ACTIVE",
  search: "",
  focusId: null,
  lastUndo: null,
  undoExpiresAt: null,
};

export function uiReducer(state: UiState, action: UiAction): UiState {
  switch (action.type) {
    case "setFilter":
      if (state.filter === action.filter) return state;
      return {
        ...state,
        filter: action.filter,
        focusId: null,
        lastUndo: null,
        undoExpiresAt: null,
      };
    case "setSearch":
      if (state.search === action.search) return state;
      return { ...state, search: action.search };
    case "setFocus":
      if (state.focusId === action.id) return state;
      return { ...state, focusId: action.id };
    case "setUndo":
      return {
        ...state,
        lastUndo: action.entry,
        undoExpiresAt: Date.now() + 5000,
      };
    case "clearUndo":
      if (state.lastUndo === null && state.undoExpiresAt === null) return state;
      return { ...state, lastUndo: null, undoExpiresAt: null };
  }
}

type ContextShape = UiState & {
  todos: Todo[];
  hydrated: boolean;
  syncing: boolean;
  now: number;
  holder: TodoStoreHolder;
  snoozeRows: Row[];
  snoozeLastRow: { time: number; label: string } | null;
  snoozeQuickTimes: QuickTime[];
  refreshNow: () => void;
  create: (text?: string) => Todo;
  edit: (id: string, text: string) => void;
  markDone: (id: string) => void;
  reactivate: (id: string) => void;
  // False (and nothing written) when `epoch` is no longer in the future.
  snooze: (
    id: string,
    epoch: number,
    source: SnoozeSource,
    pickedKey?: string,
  ) => boolean;
  togglePinned: (id: string) => void;
  remove: (id: string) => void;
  removeUndoable: (id: string) => void;
  clearAllDone: () => void;
  undo: () => void;
  setFilter: (f: Filter) => void;
  setSearch: (s: string) => void;
  setFocus: (id: string | null) => void;
  dropEmpty: () => void;
};

const Ctx = createContext<ContextShape | null>(null);
const MAX_NOW_STALENESS_MS = 5 * 60 * 1000;

const EMPTY_TODOS: Todo[] = [];
const EMPTY_PARTITIONS: DevicePartition[] = [];

export function TodoProvider({ children }: { children: ReactNode }) {
  const holderRef = useRef<TodoStoreHolder | null>(null);
  if (holderRef.current === null) holderRef.current = new TodoStoreHolder();
  const holder = holderRef.current;

  const [hydrated, setHydrated] = useState(false);
  // Nothing writes to a todo when its snooze lapses, so this has to advance itself.
  const [now, setNow] = useState(() => Date.now());
  const [ui, dispatch] = useReducer(uiReducer, initialUi);

  useEffect(() => {
    holder.setup();
    holder.hydrate();
    setHydrated(true);
    return () => {
      holder.dispose();
    };
  }, [holder]);

  const subscribe = useCallback(
    (cb: () => void) => holder.subscribe(cb),
    [holder],
  );
  const getSnapshot = useCallback(() => holder.getStore().getTodos(), [holder]);
  const getServerSnapshot = useCallback(() => EMPTY_TODOS, []);
  const todos = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
  const getSyncing = useCallback(() => holder.getStore().isSyncing(), [holder]);
  const getSyncingServer = useCallback(() => false, []);
  const syncing = useSyncExternalStore(subscribe, getSyncing, getSyncingServer);

  const subscribeStats = useCallback(
    (cb: () => void) => holder.snoozeStats.subscribe(cb),
    [holder],
  );
  const getStatsSnapshot = useCallback(
    () => holder.snoozeStats.getPartitions(),
    [holder],
  );
  const getStatsServerSnapshot = useCallback(() => EMPTY_PARTITIONS, []);
  const snoozePartitions = useSyncExternalStore(
    subscribeStats,
    getStatsSnapshot,
    getStatsServerSnapshot,
  );
  const snoozeRows = useMemo(
    () => rankSnoozeRows(snoozePartitions, now),
    [snoozePartitions, now],
  );
  const snoozeLastRow = useMemo(
    () => computeLastRow(snoozePartitions, now, snoozeRows),
    [snoozePartitions, now, snoozeRows],
  );
  const snoozeQuickTimes = useMemo(
    () => computeQuickTimes(snoozePartitions, now),
    [snoozePartitions, now],
  );
  const refreshNow = useCallback(() => setNow(Date.now()), []);

  // Auto-expire the undo window.
  useEffect(() => {
    if (!ui.undoExpiresAt) return;
    const delta = ui.undoExpiresAt - Date.now();
    if (delta <= 0) {
      dispatch({ type: "clearUndo" });
      return;
    }
    const id = setTimeout(() => dispatch({ type: "clearUndo" }), delta);
    return () => clearTimeout(id);
  }, [ui.undoExpiresAt]);

  // Wake at the soonest snooze so its row moves itself out of Snoozed.
  useEffect(() => {
    let nextExpiry = Number.POSITIVE_INFINITY;
    for (const t of todos) {
      if (t.state === "DONE" || !t.snoozeUntil) continue;
      const at = isoToEpoch(t.snoozeUntil);
      if (at > now && at < nextExpiry) nextExpiry = at;
    }
    const wakeAt = Math.min(nextExpiry, Date.now() + MAX_NOW_STALENESS_MS);
    const id = setTimeout(
      () => setNow(Date.now()),
      Math.max(100, wakeAt - Date.now()),
    );
    return () => clearTimeout(id);
  }, [todos, now]);

  const create = useCallback(
    (text = ""): Todo => {
      const todo = { ...newTodo(), text };
      const store = holder.getStore();
      void store.insert(todo);
      dispatch({ type: "setFocus", id: todo.id });
      dispatch({ type: "clearUndo" });
      return todo;
    },
    [holder],
  );

  const edit = useCallback(
    (id: string, text: string) => {
      const store = holder.getStore();
      const existing = store.getTodos().find((t) => t.id === id);
      if (!existing || existing.text === text) return;
      // An unsnoozed row drops its was-snoozed marker; pin modifiedAt to the
      // old unsnooze time so dropping snoozeUntil doesn't sink the row to a
      // stale modifiedAt. A snoozed or done one keeps the marker.
      const updated: Todo =
        matchesFilter(existing, "ACTIVE", Date.now()) && existing.snoozeUntil
          ? {
              ...existing,
              text,
              snoozeUntil: null,
              modifiedAt: isoToEpoch(existing.snoozeUntil),
            }
          : { ...existing, text };
      void store.update(updated);
    },
    [holder],
  );

  const markDone = useCallback(
    (id: string) => {
      const store = holder.getStore();
      const existing = store.getTodos().find((t) => t.id === id);
      if (!existing) return;
      void store.update({
        ...existing,
        state: "DONE",
        snoozeUntil: null,
        modifiedAt: Date.now(),
      });
      dispatch({
        type: "setUndo",
        entry: { kind: "complete", todos: [existing] },
      });
    },
    [holder],
  );

  // Clears snoozeUntil too — it's the only marker, so a lingering one re-snoozes the row.
  const reactivate = useCallback(
    (id: string) => {
      const store = holder.getStore();
      const existing = store.getTodos().find((t) => t.id === id);
      if (!existing) return;
      void store.update({
        ...existing,
        state: "ACTIVE",
        snoozeUntil: null,
        modifiedAt: Date.now(),
      });
    },
    [holder],
  );

  const snooze = useCallback(
    (
      id: string,
      epoch: number,
      source: SnoozeSource,
      pickedKey?: string,
    ): boolean => {
      const store = holder.getStore();
      const existing = store.getTodos().find((t) => t.id === id);
      if (!existing) return false;
      // Rows are computed against a `now` that can lag; refresh it so the stale row re-resolves.
      if (epoch <= Date.now()) {
        setNow(Date.now());
        return false;
      }
      void store.update({
        ...existing,
        snoozeUntil: epochToIso(epoch),
        modifiedAt: Date.now(),
      });
      const snoozeUndo = holder.snoozeStats.commit(
        Date.now(),
        epoch,
        source,
        pickedKey ?? null,
      );
      // Buffer the pre-snooze snapshot so undo restores its prior sort position.
      dispatch({
        type: "setUndo",
        entry: { kind: "snooze", todos: [existing], snoozeUndo },
      });
      return true;
    },
    [holder],
  );

  const togglePinned = useCallback(
    (id: string) => {
      const store = holder.getStore();
      const existing = store.getTodos().find((t) => t.id === id);
      if (!existing) return;
      void store.update(withPinToggled(existing, Date.now()));
    },
    [holder],
  );

  const remove = useCallback(
    (id: string) => {
      const store = holder.getStore();
      const existing = store.getTodos().find((t) => t.id === id);
      if (!existing) return;
      void store.delete(existing);
      dispatch({ type: "setFocus", id: null });
    },
    [holder],
  );

  const removeUndoable = useCallback(
    (id: string) => {
      const store = holder.getStore();
      const existing = store.getTodos().find((t) => t.id === id);
      if (!existing) return;
      void store.delete(existing);
      dispatch({ type: "setFocus", id: null });
      dispatch({
        type: "setUndo",
        entry: { kind: "delete", todos: [existing] },
      });
    },
    [holder],
  );

  const clearAllDone = useCallback(() => {
    void (async () => {
      const cleared = await holder.getStore().clearAllDone();
      if (cleared.length > 0) {
        dispatch({
          type: "setUndo",
          entry: { kind: "delete", todos: cleared },
        });
      }
    })();
  }, [holder]);

  const undo = useCallback(() => {
    const entry = ui.lastUndo;
    if (!entry || entry.todos.length === 0) return;
    const store = holder.getStore();
    if (entry.kind === "delete") {
      void store.restoreMany(entry.todos);
    } else {
      // Rows still exist (unlike delete), so re-apply the prior snapshot via update.
      for (const prior of entry.todos) void store.update(prior);
      if (entry.kind === "snooze" && entry.snoozeUndo) {
        holder.snoozeStats.undo(entry.snoozeUndo);
      }
    }
    dispatch({ type: "clearUndo" });
  }, [holder, ui.lastUndo]);

  const setFilter = useCallback((f: Filter) => {
    dispatch({ type: "setFilter", filter: f });
  }, []);
  const setSearch = useCallback((s: string) => {
    dispatch({ type: "setSearch", search: s });
  }, []);
  const setFocus = useCallback((id: string | null) => {
    dispatch({ type: "setFocus", id });
  }, []);
  const dropEmpty = useCallback(() => {
    const focusId = ui.focusId;
    void holder.getStore().deleteEmptyTodosExcept(focusId ?? "");
  }, [holder, ui.focusId]);

  const value = useMemo<ContextShape>(
    () => ({
      ...ui,
      todos,
      hydrated,
      syncing,
      now,
      holder,
      snoozeRows,
      snoozeLastRow,
      snoozeQuickTimes,
      refreshNow,
      create,
      edit,
      markDone,
      reactivate,
      snooze,
      togglePinned,
      remove,
      removeUndoable,
      clearAllDone,
      undo,
      setFilter,
      setSearch,
      setFocus,
      dropEmpty,
    }),
    [
      ui,
      todos,
      hydrated,
      syncing,
      now,
      holder,
      snoozeRows,
      snoozeLastRow,
      snoozeQuickTimes,
      refreshNow,
      create,
      edit,
      markDone,
      reactivate,
      snooze,
      togglePinned,
      remove,
      removeUndoable,
      clearAllDone,
      undo,
      setFilter,
      setSearch,
      setFocus,
      dropEmpty,
    ],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useTodos(): ContextShape {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useTodos must be used inside <TodoProvider>");
  return ctx;
}
