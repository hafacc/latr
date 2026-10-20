import { describe, expect, test } from "bun:test";
import { groupForFilter } from "./group";
import { keyTime } from "./position";
import { type DropSlot, planMove } from "./reorder";
import { epochToIso, type Filter, sortForFilter, type Todo } from "./todo";

const DAY = 24 * 60 * 60 * 1000;
const HOUR = 60 * 60 * 1000;
// Fixed local midday so "today" is unambiguous.
const NOW = new Date(2026, 3, 15, 12, 0, 0).getTime();
const MIDNIGHT = new Date(2026, 3, 15).getTime();

function todo(overrides: Partial<Todo>): Todo {
  return {
    id: overrides.id ?? "id",
    text: "",
    state: overrides.state ?? "ACTIVE",
    createdAt: NOW,
    modifiedAt: overrides.modifiedAt ?? NOW,
    serverModifiedAt: null,
    snoozeUntil: overrides.snoozeUntil ?? null,
    pinned: overrides.pinned ?? false,
    position: overrides.position ?? null,
    deleted: false,
    repeat: null,
    repeatSpawned: false,
    occurrence: null,
  };
}

function shown(todos: Todo[], filter: Filter) {
  return groupForFilter(sortForFilter(todos, filter), filter, NOW);
}

function order(todos: Todo[], filter: Filter): string[] {
  return shown(todos, filter).flatMap((bucket) => [
    `[${bucket.label}]`,
    ...bucket.todos.map((t) => t.id),
  ]);
}

/** Drops `id` at `slot` and returns the list as then shown. */
function drop(
  todos: Todo[],
  filter: Filter,
  id: string,
  slot: DropSlot,
): { todos: Todo[]; moved: Todo | null } {
  const dragged = todos.find((t) => t.id === id);
  if (!dragged) throw new Error(`no todo ${id}`);
  const moved = planMove(dragged, filter, shown(todos, filter), slot, NOW);
  return {
    todos: moved ? todos.map((t) => (t.id === id ? moved : t)) : todos,
    moved,
  };
}

describe("planMove", () => {
  const rows = [
    todo({ id: "a", modifiedAt: NOW - 1 * HOUR }),
    todo({ id: "b", modifiedAt: NOW - 2 * HOUR }),
    todo({ id: "c", modifiedAt: NOW - 3 * HOUR }),
    todo({ id: "y1", modifiedAt: NOW - 26 * HOUR }),
    todo({ id: "y2", modifiedAt: NOW - 27 * HOUR }),
  ];

  test("moves a row between two others and writes only that row", () => {
    const { todos, moved } = drop(rows, "ACTIVE", "c", { bucket: 0, index: 1 });
    expect(order(todos, "ACTIVE")).toEqual([
      "[Today]",
      "a",
      "c",
      "b",
      "[Yesterday]",
      "y1",
      "y2",
    ]);
    expect(moved?.modifiedAt).toBe(NOW);
    expect(todos.filter((t) => t.position !== null).map((t) => t.id)).toEqual([
      "c",
    ]);
  });

  test("the top of Today is halfway between its first row and now", () => {
    const { todos, moved } = drop(rows, "ACTIVE", "y2", {
      bucket: 0,
      index: 0,
    });
    expect(order(todos, "ACTIVE").slice(0, 5)).toEqual([
      "[Today]",
      "y2",
      "a",
      "b",
      "c",
    ]);
    expect(keyTime(moved?.position ?? "")).toBe(NOW - HOUR / 2);
  });

  test("dropping where the row already is changes nothing", () => {
    const { moved } = drop(rows, "ACTIVE", "b", { bucket: 0, index: 1 });
    expect(moved).toBe(rows[1]);
  });

  test("the end of one heading and the start of the next are different places", () => {
    const endOfToday = drop(rows, "ACTIVE", "y2", { bucket: 0, index: 3 });
    expect(order(endOfToday.todos, "ACTIVE")).toEqual([
      "[Today]",
      "a",
      "b",
      "c",
      "y2",
      "[Yesterday]",
      "y1",
    ]);
    // Halfway between midnight and the 09:00 row.
    expect(keyTime(endOfToday.moved?.position ?? "")).toBe(
      MIDNIGHT + 4.5 * HOUR,
    );

    const startOfYesterday = drop(rows, "ACTIVE", "c", { bucket: 1, index: 0 });
    expect(order(startOfYesterday.todos, "ACTIVE")).toEqual([
      "[Today]",
      "a",
      "b",
      "[Yesterday]",
      "c",
      "y1",
      "y2",
    ]);
    // Halfway between yesterday's 10:00 row and midnight.
    expect(keyTime(startOfYesterday.moved?.position ?? "")).toBe(
      MIDNIGHT - 7 * HOUR,
    );
  });

  test("the end of a heading is halfway down to its start", () => {
    const { todos, moved } = drop(rows, "ACTIVE", "a", { bucket: 1, index: 2 });
    expect(order(todos, "ACTIVE").slice(-3)).toEqual(["y1", "y2", "a"]);
    expect(keyTime(moved?.position ?? "")).toBe(MIDNIGHT - 19.5 * HOUR);
  });

  test("a row can be moved between two rows changed in the same millisecond", () => {
    const tied = [
      todo({ id: "a", modifiedAt: NOW - HOUR }),
      todo({ id: "b", modifiedAt: NOW - HOUR }),
      todo({ id: "c", modifiedAt: NOW - 2 * HOUR }),
    ];
    const before = order(tied, "ALL");
    const { todos } = drop(tied, "ALL", "c", { bucket: 0, index: 1 });
    expect(order(todos, "ALL")).toEqual([before[0], before[1], "c", before[2]]);
  });

  test("moves stack: each lands between its neighbors at that moment", () => {
    let todos = rows;
    for (let move = 0; move < 60; move++) {
      todos = drop(todos, "ACTIVE", move % 2 === 0 ? "c" : "b", {
        bucket: 0,
        index: 1,
      }).todos;
    }
    expect(order(todos, "ACTIVE").slice(0, 4)).toEqual([
      "[Today]",
      "a",
      "b",
      "c",
    ]);
  });

  test("a moved unsnoozed row becomes a normal row in place", () => {
    const woke = todo({
      id: "woke",
      modifiedAt: NOW - 30 * DAY,
      snoozeUntil: epochToIso(NOW - 90 * 60 * 1000),
    });
    const { todos, moved } = drop([...rows, woke], "ACTIVE", "woke", {
      bucket: 0,
      index: 2,
    });
    expect(moved?.snoozeUntil).toBeNull();
    expect(order(todos, "ACTIVE").slice(0, 5)).toEqual([
      "[Today]",
      "a",
      "b",
      "woke",
      "c",
    ]);
  });

  test("a row can be moved next to unsnoozed rows", () => {
    const sameTime = epochToIso(NOW - 90 * 60 * 1000);
    const woke = [
      todo({ id: "w1", modifiedAt: NOW - 5 * DAY, snoozeUntil: sameTime }),
      todo({ id: "w2", modifiedAt: NOW - 6 * DAY, snoozeUntil: sameTime }),
    ];
    const { todos } = drop([...rows, ...woke], "ACTIVE", "c", {
      bucket: 0,
      index: 2,
    });
    expect(order(todos, "ACTIVE").slice(0, 6)).toEqual([
      "[Today]",
      "a",
      "w1",
      "c",
      "w2",
      "b",
    ]);
  });

  test("ACTIVE: pinned rows move among pinned rows only", () => {
    const pinned = [
      todo({ id: "p1", pinned: true, modifiedAt: NOW - 1 * HOUR }),
      todo({ id: "p2", pinned: true, modifiedAt: NOW - 2 * DAY }),
    ];
    const all = [...pinned, ...rows];
    expect(drop(all, "ACTIVE", "a", { bucket: 0, index: 1 }).moved).toBeNull();
    expect(drop(all, "ACTIVE", "p1", { bucket: 1, index: 0 }).moved).toBeNull();
    const { todos, moved } = drop(all, "ACTIVE", "p2", { bucket: 0, index: 0 });
    expect(order(todos, "ACTIVE").slice(0, 3)).toEqual([
      "[Pinned]",
      "p2",
      "p1",
    ]);
    // Pinned has no date edge, so the row lands beside its neighbor.
    expect(keyTime(moved?.position ?? "")).toBe(pinned[0].modifiedAt);
  });

  test("ALL: a snoozed row moves and stays snoozed", () => {
    const snoozed = todo({
      id: "s",
      modifiedAt: NOW - 3 * DAY,
      snoozeUntil: epochToIso(NOW + DAY),
    });
    const { todos, moved } = drop([...rows, snoozed], "ALL", "s", {
      bucket: 0,
      index: 1,
    });
    expect(moved?.snoozeUntil).toBe(snoozed.snoozeUntil);
    expect(order(todos, "ALL").slice(0, 4)).toEqual(["[Today]", "a", "s", "b"]);
  });

  test("DONE: done rows reorder too", () => {
    const done = rows.map((t) => ({ ...t, state: "DONE" as const }));
    const { todos } = drop(done, "DONE", "a", { bucket: 0, index: 2 });
    expect(order(todos, "DONE").slice(0, 4)).toEqual([
      "[Today]",
      "b",
      "c",
      "a",
    ]);
  });

  describe("SNOOZED", () => {
    const sameTime = epochToIso(NOW + 2 * HOUR);
    const snoozed = [
      todo({ id: "soon", snoozeUntil: epochToIso(NOW + HOUR) }),
      todo({ id: "t1", modifiedAt: NOW - 1 * HOUR, snoozeUntil: sameTime }),
      todo({ id: "t2", modifiedAt: NOW - 2 * HOUR, snoozeUntil: sameTime }),
      todo({ id: "t3", modifiedAt: NOW - 3 * HOUR, snoozeUntil: sameTime }),
      todo({ id: "late", snoozeUntil: epochToIso(NOW + 3 * HOUR) }),
    ];

    test("rows snoozed to one time reorder among themselves", () => {
      expect(order(snoozed, "SNOOZED")).toEqual([
        "[Later today]",
        "soon",
        "t1",
        "t2",
        "t3",
        "late",
      ]);
      const toTop = drop(snoozed, "SNOOZED", "t3", { bucket: 0, index: 1 });
      expect(order(toTop.todos, "SNOOZED").slice(2, 5)).toEqual([
        "t3",
        "t1",
        "t2",
      ]);
      expect(toTop.moved?.snoozeUntil).toBe(sameTime);
      const toMiddle = drop(snoozed, "SNOOZED", "t1", { bucket: 0, index: 2 });
      expect(order(toMiddle.todos, "SNOOZED").slice(2, 5)).toEqual([
        "t2",
        "t1",
        "t3",
      ]);
      const toBottom = drop(snoozed, "SNOOZED", "t1", { bucket: 0, index: 3 });
      expect(order(toBottom.todos, "SNOOZED").slice(2, 5)).toEqual([
        "t2",
        "t3",
        "t1",
      ]);
    });

    test("a row can't leave the rows snoozed to its time", () => {
      expect(
        drop(snoozed, "SNOOZED", "t1", { bucket: 0, index: 0 }).moved,
      ).toBeNull();
      expect(
        drop(snoozed, "SNOOZED", "t1", { bucket: 0, index: 4 }).moved,
      ).toBeNull();
      expect(
        drop(snoozed, "SNOOZED", "soon", { bucket: 0, index: 2 }).moved,
      ).toBeNull();
    });
  });
});
