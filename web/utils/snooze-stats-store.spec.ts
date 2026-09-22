import { beforeEach, describe, expect, mock, test } from "bun:test";

type SetDocCall = { path: string; data: Record<string, unknown> };
const setDocCalls: SetDocCall[] = [];

mock.module("firebase/firestore", () => ({
  doc: (_db: unknown, ...path: string[]) => ({ path: path.join("/") }),
  setDoc: (ref: { path: string }, data: Record<string, unknown>) => {
    setDocCalls.push({ path: ref.path, data });
    return Promise.resolve();
  },
  onSnapshot: () => () => {},
}));
mock.module("./firebase", () => ({ db: () => ({}) }));

const { SnoozeStatsStore } = await import("./snooze-stats-store");
const { toWire } = await import("./snooze-stats-wire");

class MemoryStorage {
  private items = new Map<string, string>();
  getItem(key: string): string | null {
    return this.items.get(key) ?? null;
  }
  setItem(key: string, value: string): void {
    this.items.set(key, value);
  }
  removeItem(key: string): void {
    this.items.delete(key);
  }
  clear(): void {
    this.items.clear();
  }
}

const storage = new MemoryStorage();
(globalThis as { localStorage?: unknown }).localStorage = storage;

const HOUR = 60 * 60 * 1000;
const AT = new Date(2026, 8, 22, 10, 0).getTime();
const TOMORROW_9 = new Date(2026, 8, 23, 9, 0).getTime();
const FRIDAY_9 = new Date(2026, 8, 25, 9, 0).getTime();

function newStore(): InstanceType<typeof SnoozeStatsStore> {
  const store = new SnoozeStatsStore();
  store.hydrate();
  return store;
}

function lastPayload(): { deviceId: string; wire: unknown } {
  const call = setDocCalls[setDocCalls.length - 1];
  const stats = call.data.snoozeStats as Record<string, unknown>;
  const [deviceId] = Object.keys(stats);
  return { deviceId, wire: stats[deviceId] };
}

beforeEach(() => {
  storage.clear();
  setDocCalls.length = 0;
});

describe("SnoozeStatsStore", () => {
  test("first sign-in adopts the signed-out history under the same device id", async () => {
    const store = newStore();
    store.commit(AT, TOMORROW_9, "custom");
    const [before] = store.getPartitions();
    await store.pushToRemote("uid-a");
    const { deviceId, wire } = lastPayload();
    expect(deviceId).toBe(before.deviceId);
    expect(wire).toEqual(toWire(before));
  });

  test("another account rotates the device id and starts empty; the old id is never written again", async () => {
    const store = newStore();
    store.attachRemote("uid-a");
    store.commit(AT, TOMORROW_9, "custom");
    const oldId = store.getPartitions()[0].deviceId;
    store.detachRemote();

    store.attachRemote("uid-b");
    const [fresh] = store.getPartitions();
    expect(fresh.deviceId).not.toBe(oldId);
    expect(fresh.sets).toEqual({});
    expect(fresh.tod).toEqual({});
    expect(fresh.lastCustom).toBeNull();

    setDocCalls.length = 0;
    store.commit(AT, FRIDAY_9, "custom");
    await store.pushToRemote("uid-b");
    expect(setDocCalls.every((c) => c.path === "users/uid-b")).toBe(true);
    expect(setDocCalls.map((c) => lastIdOf(c))).not.toContain(oldId);
  });

  test("the same account signing in again keeps local history", () => {
    const store = newStore();
    store.attachRemote("uid-a");
    store.commit(AT, TOMORROW_9, "custom");
    const [before] = store.getPartitions();
    store.detachRemote();
    store.attachRemote("uid-a");
    expect(store.getPartitions()[0]).toEqual(before);
  });

  test("commit then undo mirrors exactly the pre-commit partition", () => {
    const store = newStore();
    store.attachRemote("uid-a");
    store.commit(AT, TOMORROW_9, "custom");
    const preCommit = toWire(store.getPartitions()[0]);
    const snap = store.commit(AT + HOUR, FRIDAY_9, "suggestion", "D3@0900");
    expect(lastPayload().wire).not.toEqual(preCommit);
    store.undo(snap);
    expect(lastPayload().wire).toEqual(preCommit);
  });

  test("two tabs sharing storage keep each other's commits", () => {
    const tabA = newStore();
    const tabB = newStore();
    tabA.attachRemote("uid-a");
    tabB.attachRemote("uid-a");
    tabA.commit(AT, TOMORROW_9, "custom");
    tabB.commit(AT, FRIDAY_9, "custom");
    const { wire } = lastPayload();
    const sets = Object.keys((wire as { sets: Record<string, unknown> }).sets);
    expect(sets).toHaveLength(2);
    expect(Object.keys(tabA.getPartitions()[0].sets)).toHaveLength(1);
    tabA.commit(AT + HOUR, FRIDAY_9, "custom");
    expect(Object.keys(tabA.getPartitions()[0].sets)).toHaveLength(2);
  });

  test("an hours-offset pick leaves the quick-time slots untouched", () => {
    const store = newStore();
    store.commit(AT, AT + 26 * HOUR, "suggestion", "D1_h2");
    expect(store.getPartitions()[0].tod).toEqual({});
  });
});

function lastIdOf(call: SetDocCall): string {
  return Object.keys(call.data.snoozeStats as Record<string, unknown>)[0];
}
