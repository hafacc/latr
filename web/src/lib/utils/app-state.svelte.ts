import { createContext } from "svelte";
import type { DevicePartition, SnoozeSource } from "./snooze-suggest";
import {
  lastRow as computeLastRow,
  quickTimes as computeQuickTimes,
  pickLogKey,
  rankDeep,
  shownRows,
} from "./snooze-suggest";
import { initialUi, type UiAction, type UiState, uiReducer } from "./store";
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

const MAX_NOW_STALENESS_MS = 5 * 60 * 1000;

/** The todo list and everything the screens share about it; made once in the root layout. */
export class AppState {
  readonly holder = new TodoStoreHolder();
  hydrated = $state(false);
  // Nothing writes to a todo when its snooze lapses, so this has to advance itself.
  now = $state(Date.now());
  todos = $state.raw<Todo[]>([]);
  syncing = $state(false);
  snoozePickLog = $state(false);
  #ui = $state.raw<UiState>(initialUi);
  #partitions = $state.raw<DevicePartition[]>([]);
  #deepRows = $derived(rankDeep(this.#partitions, this.now));
  readonly snoozeRows = $derived(shownRows(this.#deepRows));
  readonly snoozeLastRow = $derived(
    computeLastRow(this.#partitions, this.now, this.snoozeRows),
  );
  readonly snoozeQuickTimes = $derived(
    computeQuickTimes(this.#partitions, this.now),
  );

  constructor() {
    const { holder } = this;

    $effect(() => {
      const readTodos = () => {
        this.todos = holder.getStore().getTodos();
        this.syncing = holder.getStore().isSyncing();
      };
      const readStats = () => {
        this.#partitions = holder.snoozeStats.getPartitions();
        this.snoozePickLog = holder.snoozeStats.getPickLog();
      };
      holder.setup();
      const unsubTodos = holder.subscribe(readTodos);
      const unsubStats = holder.snoozeStats.subscribe(readStats);
      holder.hydrate();
      readTodos();
      readStats();
      this.hydrated = true;
      return () => {
        unsubTodos();
        unsubStats();
        holder.dispose();
      };
    });

    // Auto-expire the undo window.
    $effect(() => {
      const expiresAt = this.undoExpiresAt;
      if (!expiresAt) return;
      const id = setTimeout(
        () => this.#dispatch({ type: "clearUndo" }),
        Math.max(0, expiresAt - Date.now()),
      );
      return () => clearTimeout(id);
    });

    // Wake at the soonest snooze so its row moves itself out of Snoozed.
    $effect(() => {
      let nextExpiry = Number.POSITIVE_INFINITY;
      for (const t of this.todos) {
        if (t.state === "DONE" || !t.snoozeUntil) continue;
        const at = isoToEpoch(t.snoozeUntil);
        if (at > this.now && at < nextExpiry) nextExpiry = at;
      }
      const wakeAt = Math.min(nextExpiry, Date.now() + MAX_NOW_STALENESS_MS);
      const id = setTimeout(
        () => this.refreshNow(),
        Math.max(100, wakeAt - Date.now()),
      );
      return () => clearTimeout(id);
    });
  }

  get filter(): Filter {
    return this.#ui.filter;
  }
  get search(): string {
    return this.#ui.search;
  }
  get focusId(): string | null {
    return this.#ui.focusId;
  }
  get lastUndo(): UiState["lastUndo"] {
    return this.#ui.lastUndo;
  }
  get undoExpiresAt(): number | null {
    return this.#ui.undoExpiresAt;
  }

  #dispatch(action: UiAction): void {
    this.#ui = uiReducer(this.#ui, action);
  }

  #find(id: string): Todo | undefined {
    return this.holder
      .getStore()
      .getTodos()
      .find((t) => t.id === id);
  }

  refreshNow = (): void => {
    this.now = Date.now();
  };

  setSnoozePickLog = (enabled: boolean): void => {
    this.holder.snoozeStats.setPickLog(enabled);
  };

  create = (text = ""): Todo => {
    const todo = { ...newTodo(), text };
    void this.holder.getStore().insert(todo);
    this.#dispatch({ type: "setFocus", id: todo.id });
    this.#dispatch({ type: "clearUndo" });
    return todo;
  };

  edit = (id: string, text: string): void => {
    const existing = this.#find(id);
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
    void this.holder.getStore().update(updated);
  };

  markDone = (id: string): void => {
    const existing = this.#find(id);
    if (!existing) return;
    void this.holder.getStore().update({
      ...existing,
      state: "DONE",
      snoozeUntil: null,
      modifiedAt: Date.now(),
    });
    this.#dispatch({ type: "setFocus", id: null });
    this.#dispatch({
      type: "setUndo",
      entry: { kind: "complete", todos: [existing] },
    });
  };

  // Clears snoozeUntil too — it's the only marker, so a lingering one re-snoozes the row.
  reactivate = (id: string): void => {
    const existing = this.#find(id);
    if (!existing) return;
    void this.holder.getStore().update({
      ...existing,
      state: "ACTIVE",
      snoozeUntil: null,
      modifiedAt: Date.now(),
    });
  };

  /** False (and nothing written) when `epoch` is no longer in the future. */
  snooze = (
    id: string,
    epoch: number,
    source: SnoozeSource,
    pickedKey?: string,
  ): boolean => {
    const existing = this.#find(id);
    if (!existing) return false;
    // Rows are computed against a `now` that can lag; refresh it so the stale row re-resolves.
    if (epoch <= Date.now()) {
      this.refreshNow();
      return false;
    }
    const deepRows = this.#deepRows;
    void this.holder.getStore().update({
      ...existing,
      snoozeUntil: epochToIso(epoch),
      modifiedAt: Date.now(),
    });
    const snoozeUndo = this.holder.snoozeStats.commit(
      Date.now(),
      epoch,
      source,
      pickedKey ?? null,
      pickLogKey(deepRows, epoch),
    );
    // Buffer the pre-snooze snapshot so undo restores its prior sort position.
    this.#dispatch({
      type: "setUndo",
      entry: { kind: "snooze", todos: [existing], snoozeUndo },
    });
    return true;
  };

  togglePinned = (id: string): void => {
    const existing = this.#find(id);
    if (!existing) return;
    void this.holder.getStore().update(withPinToggled(existing, Date.now()));
  };

  remove = (id: string): void => {
    const existing = this.#find(id);
    if (!existing) return;
    void this.holder.getStore().delete(existing);
    this.#dispatch({ type: "setFocus", id: null });
  };

  removeUndoable = (id: string): void => {
    const existing = this.#find(id);
    if (!existing) return;
    void this.holder.getStore().delete(existing);
    this.#dispatch({ type: "setFocus", id: null });
    this.#dispatch({
      type: "setUndo",
      entry: { kind: "delete", todos: [existing] },
    });
  };

  clearAllDone = (): void => {
    void (async () => {
      const cleared = await this.holder.getStore().clearAllDone();
      if (cleared.length > 0) {
        this.#dispatch({
          type: "setUndo",
          entry: { kind: "delete", todos: cleared },
        });
      }
    })();
  };

  undo = (): void => {
    const entry = this.lastUndo;
    if (!entry || entry.todos.length === 0) return;
    const store = this.holder.getStore();
    if (entry.kind === "delete") {
      void store.restoreMany(entry.todos);
    } else {
      // Rows still exist (unlike delete), so re-apply the prior snapshot via update.
      for (const prior of entry.todos) void store.update(prior);
      if (entry.kind === "snooze" && entry.snoozeUndo) {
        this.holder.snoozeStats.undo(entry.snoozeUndo);
      }
    }
    this.#dispatch({ type: "clearUndo" });
  };

  setFilter = (filter: Filter): void => {
    this.#dispatch({ type: "setFilter", filter });
  };

  setSearch = (search: string): void => {
    this.#dispatch({ type: "setSearch", search });
  };

  setFocus = (id: string | null): void => {
    this.#dispatch({ type: "setFocus", id });
  };

  dropEmpty = (): void => {
    void this.holder.getStore().deleteEmptyTodosExcept(this.focusId ?? "");
  };
}

export const [getAppState, setAppState] = createContext<AppState>();
