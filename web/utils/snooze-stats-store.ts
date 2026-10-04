"use client";

import {
  deleteField,
  doc,
  increment,
  onSnapshot,
  runTransaction,
  setDoc,
} from "firebase/firestore";
import { db } from "./firebase";
import {
  foldBlocks,
  fromWire,
  negated,
  sharedFromWire,
  toWire,
  type VoteDeltas,
  voteDeltas,
} from "./snooze-stats-wire";
import {
  type CommitUndoSnapshot,
  commit as commitPure,
  type DevicePartition,
  emptyPartition,
  type SnoozeSource,
  undoCommit as undoCommitPure,
} from "./snooze-suggest";

const STORAGE_KEY = "latr:snooze-stats:v2";
const LEGACY_STORAGE_KEY = "latr:snooze-stats:v1";
// Left by builds that kept a block of counts per device.
const LEGACY_DEVICE_ID_KEY = "latr:device-id:v1";
const LEGACY_OWNER_UID_KEY = "latr:snooze-stats-owner:v1";
const BASE_RETRY_DELAY_MS = 1_000;
const MAX_RETRY_DELAY_MS = 30_000;

// `deltas` is what a signed-in commit added to the shared counts; null for a signed-out one.
export type SnoozeUndo = {
  snapshot: CommitUndoSnapshot;
  deltas: VoteDeltas | null;
  custom: boolean;
};

type UserDoc = {
  snoozeVotes?: { folded?: unknown; lastCustom?: unknown };
  snoozeStats?: unknown;
  snoozePickLog?: unknown;
};

function increments(deltas: Record<string, number>): Record<string, unknown> {
  return Object.fromEntries(
    Object.entries(deltas).map(([id, amount]) => [id, increment(amount)]),
  );
}

/** Signed in, the counts are the account's `snoozeVotes`, changed only by adding amounts; signed out, a copy in localStorage shared by every tab. */
export class SnoozeStatsStore {
  private local: DevicePartition = emptyPartition("local");
  private shared: DevicePartition = emptyPartition("shared");
  // Stable between changes: useSyncExternalStore loops forever on a fresh array.
  private partitionsCache: DevicePartition[] = [this.local];
  private listeners = new Set<() => void>();
  private unsubDoc: (() => void) | null = null;
  private uid: string | null = null;
  private retryAttempt = 0;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;
  private onStorage: ((e: StorageEvent) => void) | null = null;
  // `users/{uid}.snoozePickLog`; null while signed out or before the first snapshot.
  private pickLog: boolean | null = null;
  private folding = false;

  private rebuildPartitionsCache(): void {
    this.partitionsCache = [this.uid ? this.shared : this.local];
  }

  hydrate(): void {
    try {
      localStorage.removeItem(LEGACY_STORAGE_KEY);
      // Counts saved while signed in are that account's device block, which is folded into its shared counts.
      if (localStorage.getItem(LEGACY_OWNER_UID_KEY) !== null) {
        localStorage.removeItem(STORAGE_KEY);
        localStorage.removeItem(LEGACY_OWNER_UID_KEY);
      }
      localStorage.removeItem(LEGACY_DEVICE_ID_KEY);
    } catch {
      // best-effort
    }
    this.reloadFromStorage();
    if (typeof window !== "undefined" && this.onStorage === null) {
      this.onStorage = (e) => {
        if (e.key === null || e.key === STORAGE_KEY) {
          this.reloadFromStorage();
          this.rebuildPartitionsCache();
          this.emit();
        }
      };
      window.addEventListener("storage", this.onStorage);
    }
    this.rebuildPartitionsCache();
    this.emit();
  }

  getPartitions(): DevicePartition[] {
    return this.partitionsCache;
  }

  subscribe(listener: () => void): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  getPickLog(): boolean {
    return this.pickLog === true;
  }

  /** Per user, so it covers every device; turning it off also clears the saved picks. */
  setPickLog(enabled: boolean): void {
    if (!this.uid) return;
    this.pickLog = enabled;
    const fields: Record<string, unknown> = { snoozePickLog: enabled };
    if (!enabled) fields.snoozeVotes = { picks: deleteField() };
    void setDoc(doc(db(), "users", this.uid), fields, { merge: true }).catch(
      (e) => console.error("snooze pick log toggle failed", e),
    );
    this.emit();
  }

  commit(
    at: number,
    target: number,
    source: SnoozeSource,
    pickedKey: string | null = null,
    pickLogKey: string | null = null,
  ): SnoozeUndo {
    const custom = source === "custom";
    if (this.uid) {
      const result = commitPure(
        this.shared,
        at,
        target,
        source,
        pickedKey,
        this.pickLog === true ? pickLogKey : null,
      );
      const deltas = voteDeltas(this.shared, result.next);
      const pick = result.undoSnapshot.pick;
      this.setShared(result.next);
      this.addVotes(this.uid, deltas, {
        ...(custom ? { lastCustom: result.next.lastCustom } : {}),
        ...(pick ? { picks: { [pick.key]: increment(1) } } : {}),
      });
      if (pick) this.bumpGlobalPick(pick.key, 1);
      return { snapshot: result.undoSnapshot, deltas, custom };
    } else {
      this.reloadFromStorage();
      const result = commitPure(this.local, at, target, source, pickedKey);
      this.setLocal(result.next);
      return { snapshot: result.undoSnapshot, deltas: null, custom };
    }
  }

  undo(undo: SnoozeUndo): void {
    const { snapshot, deltas, custom } = undo;
    if (deltas === null) {
      if (this.uid) return;
      this.reloadFromStorage();
      this.setLocal(undoCommitPure(this.local, snapshot));
    } else if (this.uid) {
      const logged = snapshot.pick !== null && this.pickLog === true;
      this.setShared(undoCommitPure(this.shared, snapshot));
      this.addVotes(this.uid, negated(deltas), {
        ...(custom ? { lastCustom: snapshot.prevLastCustom } : {}),
        ...(snapshot.pick && logged
          ? { picks: { [snapshot.pick.key]: increment(-1) } }
          : {}),
      });
      if (snapshot.pick) this.bumpGlobalPick(snapshot.pick.key, -1);
    }
  }

  /** Sign-in: add the counts learned while signed out to the account's, once (user-initiated, like mergeLocalIntoFirestore). */
  async pushToRemote(uid: string): Promise<void> {
    this.reloadFromStorage();
    const deltas = voteDeltas(emptyPartition("local"), this.local);
    this.setLocal(emptyPartition("local"));
    await this.writeVotes(uid, deltas, {});
  }

  getShared(): DevicePartition {
    return this.shared;
  }

  /** Makes [counts], a copy of a deleted account's, this device's own. */
  keepLocally(counts: DevicePartition): void {
    this.setLocal({ ...counts, deviceId: "local" });
  }

  attachRemote(uid: string): void {
    if (this.uid === uid && (this.unsubDoc || this.retryTimer)) return;
    this.detachRemote();
    this.uid = uid;
    this.retryAttempt = 0;
    this.rebuildPartitionsCache();
    this.startListening(uid);
  }

  /** Retry now instead of waiting out backoff — mirrors FirestoreTodoStore.reattach(). */
  reattach(): void {
    if (this.retryTimer === null || this.uid === null) return;
    clearTimeout(this.retryTimer);
    this.retryTimer = null;
    this.startListening(this.uid);
  }

  private startListening(uid: string): void {
    this.unsubDoc = onSnapshot(
      doc(db(), "users", uid),
      (snap) => {
        // A server-confirmed emission means the listener is healthy again.
        if (!snap.metadata.fromCache) this.retryAttempt = 0;
        const data = snap.data() as UserDoc | undefined;
        this.pickLog = data?.snoozePickLog === true;
        this.setShared(sharedFromWire(data?.snoozeVotes));
        if (foldBlocks(data?.snoozeStats, data?.snoozeVotes?.folded).ids.length)
          this.foldDeviceBlocks(uid);
      },
      (err) => {
        // A delivered error terminates the listener for good.
        console.error("snooze-stats listener error", err);
        this.unsubDoc = null;
        this.scheduleReattach(uid);
      },
    );
  }

  /** Adds the blocks older builds saved per device to the shared counts, once each. A transaction, so two devices can't both add one; a block an old build saves again afterwards is ignored. */
  private foldDeviceBlocks(uid: string): void {
    if (this.folding) return;
    this.folding = true;
    const ref = doc(db(), "users", uid);
    void runTransaction(db(), async (tx) => {
      const data = (await tx.get(ref)).data() as UserDoc | undefined;
      const fold = foldBlocks(data?.snoozeStats, data?.snoozeVotes?.folded);
      if (fold.ids.length === 0) return;
      const current = sharedFromWire(data?.snoozeVotes).lastCustom;
      const newer =
        fold.lastCustom && (!current || fold.lastCustom.at > current.at);
      tx.set(
        ref,
        {
          snoozeVotes: {
            sets: increments(fold.deltas.sets),
            tod: increments(fold.deltas.tod),
            folded: Object.fromEntries(fold.ids.map((id) => [id, true])),
            ...(newer ? { lastCustom: fold.lastCustom } : {}),
            ...(data?.snoozePickLog === true
              ? { picks: increments(fold.picks) }
              : {}),
          },
          snoozeStats: deleteField(),
        },
        { merge: true },
      );
    })
      .catch((e) => console.error("snooze-stats fold failed", e))
      .finally(() => {
        this.folding = false;
      });
  }

  private scheduleReattach(uid: string): void {
    if (this.retryTimer !== null) return;
    const backoff = Math.min(
      MAX_RETRY_DELAY_MS,
      BASE_RETRY_DELAY_MS * 2 ** this.retryAttempt,
    );
    this.retryAttempt++;
    this.retryTimer = setTimeout(() => {
      this.retryTimer = null;
      this.startListening(uid);
    }, backoff);
  }

  private reloadFromStorage(): void {
    if (typeof localStorage === "undefined") return;
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      this.local = raw
        ? fromWire("local", JSON.parse(raw))
        : emptyPartition("local");
    } catch {
      // keep the in-memory copy
    }
  }

  detachRemote(): void {
    if (this.unsubDoc) {
      this.unsubDoc();
      this.unsubDoc = null;
    }
    if (this.retryTimer !== null) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
    this.uid = null;
    this.pickLog = null;
    this.shared = emptyPartition("shared");
    this.rebuildPartitionsCache();
  }

  dispose(): void {
    this.detachRemote();
    if (this.onStorage !== null) {
      window.removeEventListener("storage", this.onStorage);
      this.onStorage = null;
    }
    this.listeners.clear();
  }

  private setShared(next: DevicePartition): void {
    this.shared = next;
    this.rebuildPartitionsCache();
    this.emit();
  }

  private setLocal(next: DevicePartition): void {
    this.local = { ...next, picks: {} };
    this.rebuildPartitionsCache();
    if (typeof localStorage !== "undefined") {
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(toWire(this.local)));
      } catch {
        // best-effort
      }
    }
    this.emit();
  }

  private addVotes(
    uid: string,
    deltas: VoteDeltas,
    extra: Record<string, unknown>,
  ): void {
    void this.writeVotes(uid, deltas, extra).catch((e) =>
      console.error("snooze-stats write failed", e),
    );
  }

  /** Amounts are added on the server (and to the cached copy while offline), so nothing another device wrote is overwritten. */
  private async writeVotes(
    uid: string,
    deltas: VoteDeltas,
    extra: Record<string, unknown>,
  ): Promise<void> {
    const votes: Record<string, unknown> = { ...extra };
    if (Object.keys(deltas.sets).length) votes.sets = increments(deltas.sets);
    if (Object.keys(deltas.tod).length) votes.tod = increments(deltas.tod);
    if (Object.keys(votes).length === 0) return;
    await setDoc(
      doc(db(), "users", uid),
      { snoozeVotes: votes },
      { merge: true },
    );
  }

  /** Separate from the votes write so a rules rejection can't cost the user's counts. */
  private bumpGlobalPick(key: string, delta: 1 | -1): void {
    if (!this.uid || this.pickLog !== true) return;
    // `key` names the bumped field, since rules can't pull it out of the changed-field set.
    void setDoc(
      doc(db(), "snoozePickLog", "global"),
      { [key]: increment(delta), key },
      { merge: true },
    ).catch((e) => console.error("global snooze pick log failed", e));
  }

  private emit(): void {
    for (const l of this.listeners) l();
  }
}
