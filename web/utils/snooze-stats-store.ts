"use client";

import { doc, onSnapshot, setDoc } from "firebase/firestore";
import { db } from "./firebase";
import { fromWire, toWire } from "./snooze-stats-wire";
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
const DEVICE_ID_KEY = "latr:device-id:v1";
const OWNER_UID_KEY = "latr:snooze-stats-owner:v1";
const BASE_RETRY_DELAY_MS = 1_000;
const MAX_RETRY_DELAY_MS = 30_000;

function getOrCreateDeviceId(): string {
  if (typeof localStorage === "undefined") return crypto.randomUUID();
  try {
    const existing = localStorage.getItem(DEVICE_ID_KEY);
    if (existing) return existing;
    const id = crypto.randomUUID();
    localStorage.setItem(DEVICE_ID_KEY, id);
    return id;
  } catch {
    return crypto.randomUUID();
  }
}

function readOwnerUid(): string | null {
  if (typeof localStorage === "undefined") return null;
  try {
    return localStorage.getItem(OWNER_UID_KEY);
  } catch {
    return null;
  }
}

function writeOwnerUid(uid: string): void {
  if (typeof localStorage === "undefined") return;
  try {
    localStorage.setItem(OWNER_UID_KEY, uid);
  } catch {
    // best-effort
  }
}

/** Tabs share one device id, so localStorage, not this object, holds the truth for the local partition. */
export class SnoozeStatsStore {
  private local: DevicePartition;
  private remote = new Map<string, DevicePartition>();
  // Stable between changes: useSyncExternalStore loops forever on a fresh array.
  private partitionsCache: DevicePartition[];
  private listeners = new Set<() => void>();
  private unsubDoc: (() => void) | null = null;
  private uid: string | null = null;
  private retryAttempt = 0;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;
  private onStorage: ((e: StorageEvent) => void) | null = null;

  constructor() {
    this.local = emptyPartition(getOrCreateDeviceId());
    this.partitionsCache = [this.local];
  }

  private rebuildPartitionsCache(): void {
    this.partitionsCache = [this.local, ...this.remote.values()];
  }

  hydrate(): void {
    try {
      localStorage.removeItem(LEGACY_STORAGE_KEY);
    } catch {
      // best-effort
    }
    this.reloadFromStorage();
    if (typeof window !== "undefined" && this.onStorage === null) {
      this.onStorage = (e) => {
        if (
          e.key === null ||
          e.key === STORAGE_KEY ||
          e.key === DEVICE_ID_KEY ||
          e.key === OWNER_UID_KEY
        ) {
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

  commit(
    at: number,
    target: number,
    source: SnoozeSource,
    pickedKey: string | null = null,
  ): CommitUndoSnapshot {
    this.syncLocal();
    const result = commitPure(this.local, at, target, source, pickedKey);
    this.local = result.next;
    this.afterLocalChange();
    return result.undoSnapshot;
  }

  undo(snapshot: CommitUndoSnapshot): void {
    this.syncLocal();
    this.local = undoCommitPure(this.local, snapshot);
    this.afterLocalChange();
  }

  /** Sign-in: push the whole local partition once (user-initiated, like mergeLocalIntoFirestore). */
  async pushToRemote(uid: string): Promise<void> {
    this.ensureOwner(uid);
    await setDoc(
      doc(db(), "users", uid),
      { snoozeStats: { [this.local.deviceId]: toWire(this.local) } },
      { mergeFields: [`snoozeStats.${this.local.deviceId}`] },
    );
  }

  attachRemote(uid: string): void {
    if (this.uid === uid && (this.unsubDoc || this.retryTimer)) return;
    this.detachRemote();
    this.ensureOwner(uid);
    this.uid = uid;
    this.retryAttempt = 0;
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
        const data = snap.data() as
          | { snoozeStats?: Record<string, unknown> }
          | undefined;
        const stats = data?.snoozeStats ?? {};
        // Our own partition comes from localStorage, which is always at least as new.
        this.remote = new Map(
          Object.entries(stats)
            .filter(([id]) => id !== this.local.deviceId)
            .map(([id, raw]) => [id, fromWire(id, raw)] as const),
        );
        this.rebuildPartitionsCache();
        this.emit();
      },
      (err) => {
        // A delivered error terminates the listener for good.
        console.error("snooze-stats listener error", err);
        this.unsubDoc = null;
        this.scheduleReattach(uid);
      },
    );
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

  /** A different account gets a fresh partition under a new id, so the old account's copy is never overwritten. */
  private ensureOwner(uid: string): void {
    this.reloadFromStorage();
    const owner = readOwnerUid();
    if (owner !== null && owner !== uid) {
      const deviceId = crypto.randomUUID();
      try {
        localStorage.setItem(DEVICE_ID_KEY, deviceId);
      } catch {
        // best-effort
      }
      this.local = emptyPartition(deviceId);
      this.persistNow();
      this.rebuildPartitionsCache();
      this.emit();
    }
    writeOwnerUid(uid);
  }

  private syncLocal(): void {
    if (this.uid) this.ensureOwner(this.uid);
    else this.reloadFromStorage();
  }

  private reloadFromStorage(): void {
    if (typeof localStorage === "undefined") return;
    try {
      const deviceId =
        localStorage.getItem(DEVICE_ID_KEY) ?? this.local.deviceId;
      const raw = localStorage.getItem(STORAGE_KEY);
      this.local = raw
        ? fromWire(deviceId, JSON.parse(raw))
        : emptyPartition(deviceId);
    } catch {
      // keep the in-memory partition
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
    this.remote = new Map();
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

  private afterLocalChange(): void {
    this.rebuildPartitionsCache();
    this.persistNow();
    this.mirror();
    this.emit();
  }

  private mirror(): void {
    if (!this.uid) return;
    void setDoc(
      doc(db(), "users", this.uid),
      { snoozeStats: { [this.local.deviceId]: toWire(this.local) } },
      { mergeFields: [`snoozeStats.${this.local.deviceId}`] },
    ).catch((e) => console.error("snooze-stats mirror failed", e));
  }

  private persistNow(): void {
    if (typeof localStorage === "undefined") return;
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(toWire(this.local)));
    } catch {
      // best-effort
    }
  }

  private emit(): void {
    for (const l of this.listeners) l();
  }
}
