import { beforeEach, describe, expect, mock, test } from "bun:test";

type SetDocCall = { path: string; data: Record<string, unknown> };
const setDocCalls: SetDocCall[] = [];
type UserData = Record<string, unknown> | undefined;
let serverDoc: UserData;
let onSnap: ((snap: unknown) => void) | null = null;

// bun:test module mocks are process-wide, so keep every real export for other spec files.
const realFirestore = await import("firebase/firestore");
const realFirebase = await import("./firebase");
mock.module("firebase/firestore", () => ({
  ...realFirestore,
  doc: (_db: unknown, ...path: string[]) => ({ path: path.join("/") }),
  setDoc: (ref: { path: string }, data: Record<string, unknown>) => {
    setDocCalls.push({ path: ref.path, data });
    return Promise.resolve();
  },
  increment: (amount: number) => ({ add: amount }),
  deleteField: () => "DELETE",
  onSnapshot: (_ref: unknown, next: (snap: unknown) => void) => {
    onSnap = next;
    return () => {};
  },
  runTransaction: async (
    _db: unknown,
    body: (tx: unknown) => Promise<void>,
  ) => {
    await body({
      get: async () => ({ data: () => serverDoc }),
      set: (ref: { path: string }, data: Record<string, unknown>) => {
        setDocCalls.push({ path: ref.path, data });
      },
    });
  },
}));
mock.module("./firebase", () => ({ ...realFirebase, db: () => ({}) }));

const { SnoozeStatsStore } = await import("./snooze-stats-store");
const { toWire, sharedFromWire, VOTE_EPOCH } = await import(
  "./snooze-stats-wire"
);
const { emptyPartition, commit: commitPure } = await import("./snooze-suggest");

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

type Added = { add: number };
type Votes = {
  sets?: Record<string, Added>;
  tod?: Record<string, Added>;
  lastCustom?: unknown;
  picks?: unknown;
  folded?: Record<string, boolean>;
};

function votesOf(call: SetDocCall): Votes {
  return call.data.snoozeVotes as Votes;
}

function emitDoc(data: UserData): void {
  serverDoc = data;
  onSnap?.({ metadata: { fromCache: false }, data: () => data });
}

/** The saved counts after applying every write so far, as a server would. */
function applied(): { sets: Record<string, number> } {
  const sets: Record<string, number> = {};
  for (const call of setDocCalls) {
    for (const [id, { add }] of Object.entries(votesOf(call)?.sets ?? {})) {
      sets[id] = (sets[id] ?? 0) + add;
    }
  }
  return { sets };
}

beforeEach(() => {
  storage.clear();
  setDocCalls.length = 0;
  serverDoc = undefined;
  onSnap = null;
});

describe("SnoozeStatsStore", () => {
  test("signed in, a commit adds a vote scaled by its time and saves no block", () => {
    const store = newStore();
    store.attachRemote("uid-a");
    store.commit(AT, TOMORROW_9, "custom");
    expect(setDocCalls).toHaveLength(1);
    expect(setDocCalls[0].path).toBe("users/uid-a");
    expect(setDocCalls[0].data.snoozeStats).toBeUndefined();
    const added = Object.values(votesOf(setDocCalls[0]).sets ?? {});
    expect(added).toHaveLength(1);
    expect(added[0].add).toBeCloseTo(
      2 ** ((AT - VOTE_EPOCH) / (21 * 24 * HOUR)),
      6,
    );
    expect(votesOf(setDocCalls[0]).lastCustom).toEqual({
      target: TOMORROW_9,
      at: AT,
    });
    expect(storage.getItem("latr:snooze-stats:v2")).toBeNull();
  });

  test("a snooze takes votes from a nearby time another device saved", () => {
    const other = commitPure(emptyPartition("x"), AT, TOMORROW_9, "custom");
    const [setId] = Object.keys(other.next.sets);
    const saved = 2 ** ((AT - VOTE_EPOCH) / (21 * 24 * HOUR));
    const store = newStore();
    store.attachRemote("uid-a");
    emitDoc({ snoozeVotes: { sets: { [setId]: saved } } });
    store.commit(AT, TOMORROW_9 + HOUR / 2, "custom");
    expect(votesOf(setDocCalls[0]).sets?.[setId].add).toBeCloseTo(
      -saved / 2,
      6,
    );
  });

  test("undo adds back exactly what the commit added", () => {
    const store = newStore();
    store.attachRemote("uid-a");
    store.commit(AT, TOMORROW_9, "custom");
    const undo = store.commit(AT + HOUR, TOMORROW_9 + HOUR / 2, "custom");
    const before = setDocCalls.slice(0, 1);
    store.undo(undo);
    const after = applied().sets;
    setDocCalls.length = 0;
    setDocCalls.push(...before);
    const expected = applied().sets;
    for (const [id, count] of Object.entries(after)) {
      expect(count).toBeCloseTo(expected[id] ?? 0, 6);
    }
    expect(store.getPartitions()[0].lastCustom).toEqual({
      target: TOMORROW_9,
      at: AT,
    });
  });

  test("sign-in adds the signed-out counts to the account once and clears them", async () => {
    const store = newStore();
    store.commit(AT, TOMORROW_9, "custom");
    const [before] = store.getPartitions();
    expect(Object.keys(before.sets)).toHaveLength(1);
    await store.pushToRemote("uid-a");
    expect(Object.keys(votesOf(setDocCalls[0]).sets ?? {})).toEqual(
      Object.keys(before.sets),
    );
    expect(store.getPartitions()[0].sets).toEqual({});
    await store.pushToRemote("uid-a");
    expect(setDocCalls).toHaveLength(1);
  });

  test("the counts shown are the account's while signed in, the local ones after", () => {
    const store = newStore();
    store.commit(AT, TOMORROW_9, "custom");
    const [local] = store.getPartitions();
    store.attachRemote("uid-a");
    emitDoc({ snoozeVotes: { tod: { "0900": 3 } } });
    expect(store.getPartitions()).toEqual([
      sharedFromWire({ tod: { "0900": 3 } }),
    ]);
    store.detachRemote();
    expect(store.getPartitions()).toEqual([local]);
  });

  test("counts an older build saved while signed in are dropped from this device", () => {
    const old = commitPure(emptyPartition("d"), AT, TOMORROW_9, "custom");
    storage.setItem("latr:snooze-stats:v2", JSON.stringify(toWire(old.next)));
    storage.setItem("latr:snooze-stats-owner:v1", "uid-a");
    expect(newStore().getPartitions()[0].sets).toEqual({});
  });

  test("per-device blocks are added to the shared counts once and removed", async () => {
    const block = toWire(
      commitPure(emptyPartition("d"), AT, TOMORROW_9, "custom").next,
    );
    const store = newStore();
    store.attachRemote("uid-a");
    emitDoc({ snoozeStats: { one: block, two: block } });
    await Promise.resolve();
    await Promise.resolve();
    expect(setDocCalls).toHaveLength(1);
    const votes = votesOf(setDocCalls[0]);
    expect(votes.folded).toEqual({ one: true, two: true });
    expect(setDocCalls[0].data.snoozeStats).toBe("DELETE");
    const [added] = Object.values(votes.sets ?? {});
    expect(added.add).toBeCloseTo(
      2 * 2 ** ((AT - VOTE_EPOCH) / (21 * 24 * HOUR)),
      6,
    );
    expect(votes.picks).toBeUndefined();

    emitDoc({
      snoozeStats: { one: block },
      snoozeVotes: { folded: { one: true, two: true } },
    });
    await Promise.resolve();
    await Promise.resolve();
    expect(setDocCalls).toHaveLength(1);
  });

  test("two tabs sharing storage keep each other's signed-out commits", () => {
    const tabA = newStore();
    const tabB = newStore();
    tabA.commit(AT, TOMORROW_9, "custom");
    tabB.commit(AT, FRIDAY_9, "custom");
    expect(Object.keys(tabB.getPartitions()[0].sets)).toHaveLength(2);
    tabA.commit(AT + HOUR, FRIDAY_9, "custom");
    expect(Object.keys(tabA.getPartitions()[0].sets)).toHaveLength(2);
  });

  test("an hours-offset pick leaves the quick-time slots untouched", () => {
    const store = newStore();
    store.commit(AT, AT + 26 * HOUR, "suggestion", "D1_h2");
    expect(store.getPartitions()[0].tod).toEqual({});
  });
});
