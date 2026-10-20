import { afterAll, beforeEach, describe, expect, test } from "bun:test";
import type { Repeat } from "./repeat";
import { newTodo, type Todo, withDone } from "./todo";
import { LocalTodoStore, upsert } from "./todo-store";

const DAILY: Repeat = {
  every: 1,
  unit: "day",
  weekday: 1,
  monthDay: 1,
  at: "09:00",
  anchor: "2026-10-20",
};

const saved = new Map<string, string>();

beforeEach(() => {
  saved.clear();
  Object.assign(globalThis, {
    window: globalThis,
    localStorage: {
      getItem: (key: string) => saved.get(key) ?? null,
      setItem: (key: string, value: string) => saved.set(key, value),
      removeItem: (key: string) => saved.delete(key),
    },
  });
});

afterAll(() => {
  Reflect.deleteProperty(globalThis, "window");
  Reflect.deleteProperty(globalThis, "localStorage");
});

function reload(): LocalTodoStore {
  const store = new LocalTodoStore();
  store.hydrate();
  return store;
}

describe("LocalTodoStore", () => {
  test("keeps the repeat fields across a reload", async () => {
    const store = reload();
    const todo: Todo = {
      ...newTodo("Water the plants"),
      repeat: DAILY,
      repeatSpawned: true,
      occurrence: "2026-10-21T09:00:00",
    };
    await store.insert(todo);
    store.flush();
    const [back] = reload().getTodos();
    expect(back.repeat).toEqual(DAILY);
    expect(back.repeatSpawned).toBe(true);
    expect(back.occurrence).toBe("2026-10-21T09:00:00");
  });

  test("a row saved by an older build reads as a one-off", () => {
    saved.set(
      "latr:todos:v1",
      JSON.stringify([{ id: "a", text: "x", state: "ACTIVE", deleted: false }]),
    );
    const [back] = reload().getTodos();
    expect(back.repeat).toBeNull();
    expect(back.repeatSpawned).toBe(false);
    expect(back.occurrence).toBeNull();
  });

  test("completing and undoing a repeating todo", async () => {
    const store = reload();
    const todo: Todo = { ...newTodo("Water the plants"), repeat: DAILY };
    await store.insert(todo);
    const { done, spawned } = withDone(todo, Date.now(), "copy");
    if (!spawned) throw new Error("expected a copy");
    await store.apply([done, spawned], []);
    expect(store.getTodos().map((t) => t.id)).toEqual(["copy", todo.id]);
    await store.apply([todo], [spawned]);
    expect(store.getTodos()).toEqual([todo]);
    expect(
      store.getWithTombstones().find((t) => t.id === "copy")?.deleted,
    ).toBe(true);
  });
});

describe("upsert", () => {
  test("replaces known rows and puts new ones on top", () => {
    const first = newTodo("a");
    const second = newTodo("b");
    const changed = { ...first, text: "A" };
    expect(upsert([first], [changed, second])).toEqual([second, changed]);
  });
});
