import { describe, expect, test } from "bun:test";
import { initialUi, uiReducer, undoWrites } from "./store";
import type { Todo } from "./todo";

function todo(id: string): Todo {
  return {
    id,
    text: "",
    state: "DONE",
    createdAt: 0,
    modifiedAt: 0,
    serverModifiedAt: null,
    snoozeUntil: null,
    pinned: false,
    position: null,
    deleted: false,
    repeat: null,
    repeatSpawned: false,
    occurrence: null,
  };
}

describe("uiReducer", () => {
  test("setFilter clears transient state (focus, undo buffer)", () => {
    const primed = {
      ...initialUi,
      focusId: "x",
      lastUndo: { kind: "delete" as const, todos: [todo("a")] },
      undoExpiresAt: 1,
    };
    const next = uiReducer(primed, { type: "setFilter", filter: "DONE" });
    expect(next.filter).toBe("DONE");
    expect(next.focusId).toBeNull();
    expect(next.lastUndo).toBeNull();
    expect(next.undoExpiresAt).toBeNull();
  });

  test("setFilter to the current filter returns the same state reference", () => {
    const next = uiReducer(initialUi, {
      type: "setFilter",
      filter: initialUi.filter,
    });
    expect(next).toBe(initialUi);
  });

  test("setSearch/setFocus are no-op when unchanged (same reference)", () => {
    expect(
      uiReducer(initialUi, { type: "setSearch", search: initialUi.search }),
    ).toBe(initialUi);
    expect(
      uiReducer(initialUi, { type: "setFocus", id: initialUi.focusId }),
    ).toBe(initialUi);
  });

  test("setUndo sets a future undoExpiresAt", () => {
    const before = Date.now();
    const next = uiReducer(initialUi, {
      type: "setUndo",
      entry: { kind: "delete", todos: [todo("a")] },
    });
    expect(next.lastUndo?.todos).toHaveLength(1);
    expect(next.undoExpiresAt ?? 0).toBeGreaterThanOrEqual(before);
  });

  test("setUndo records the snooze kind", () => {
    const next = uiReducer(initialUi, {
      type: "setUndo",
      entry: { kind: "snooze", todos: [todo("a")] },
    });
    expect(next.lastUndo?.kind).toBe("snooze");
  });

  test("clearUndo is no-op when already cleared", () => {
    expect(uiReducer(initialUi, { type: "clearUndo" })).toBe(initialUi);
  });

  test("clearUndo wipes the undo buffer", () => {
    const primed = {
      ...initialUi,
      lastUndo: { kind: "delete" as const, todos: [todo("a")] },
      undoExpiresAt: 1,
    };
    const next = uiReducer(primed, { type: "clearUndo" });
    expect(next.lastUndo).toBeNull();
    expect(next.undoExpiresAt).toBeNull();
  });
});

describe("undoWrites", () => {
  test("undoing a repeating complete restores the todo and deletes its copy", () => {
    const prior = todo("a");
    const copy = todo("b");
    expect(
      undoWrites({ kind: "complete", todos: [prior], spawned: copy }),
    ).toEqual({ put: [prior], remove: [copy], restore: [] });
  });

  test("a plain complete only restores", () => {
    const prior = todo("a");
    expect(undoWrites({ kind: "complete", todos: [prior] })).toEqual({
      put: [prior],
      remove: [],
      restore: [],
    });
  });

  test("a delete re-inserts", () => {
    const gone = todo("a");
    expect(undoWrites({ kind: "delete", todos: [gone] })).toEqual({
      put: [],
      remove: [],
      restore: [gone],
    });
  });
});
