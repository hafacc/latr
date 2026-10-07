import type { SnoozeUndo } from "./snooze-stats-store";
import type { Filter, Todo } from "./todo";

// Most-recent-wins undo: "delete" restores via re-insert, "snooze"/"complete" via update.
export const UNDO_MS = 5000;

export type UndoKind = "delete" | "snooze" | "complete";
export type UndoEntry = {
  kind: UndoKind;
  todos: Todo[];
  // Only set for "snooze": the learned-suggestion credit this pick added, so undo can retract it.
  snoozeUndo?: SnoozeUndo;
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
        undoExpiresAt: Date.now() + UNDO_MS,
      };
    case "clearUndo":
      if (state.lastUndo === null && state.undoExpiresAt === null) return state;
      return { ...state, lastUndo: null, undoExpiresAt: null };
  }
}
