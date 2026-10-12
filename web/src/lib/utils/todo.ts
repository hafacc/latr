import { Timestamp } from "firebase/firestore";
import { plainText } from "./inline-style";
import { idKey, readPosition, timeKey } from "./position";

export type TodoState = "ACTIVE" | "DONE";

export type Todo = {
  id: string;
  text: string;
  state: TodoState;
  createdAt: number;
  modifiedAt: number;
  serverModifiedAt: Timestamp | null;
  snoozeUntil: string | null;
  pinned: boolean;
  /** Where a move put the row; null orders it by its time. */
  position: string | null;
  deleted: boolean;
};

export const FILTERS = ["ACTIVE", "SNOOZED", "DONE", "ALL"] as const;
export type Filter = (typeof FILTERS)[number];

export function newTodo(text = ""): Todo {
  const now = Date.now();
  return {
    id: crypto.randomUUID(),
    text,
    state: "ACTIVE",
    createdAt: now,
    modifiedAt: now,
    serverModifiedAt: null,
    snoozeUntil: null,
    pinned: false,
    position: null,
    deleted: false,
  };
}

export function toFirestoreFields(t: Todo): Record<string, unknown> {
  return {
    text: t.text,
    createdAt: t.createdAt,
    modifiedAt: t.modifiedAt,
    state: t.state,
    snoozeUntil: t.snoozeUntil,
    pinned: t.pinned,
    position: t.position,
    deleted: t.deleted,
  };
}

function readServerTimestamp(val: unknown): Timestamp | null {
  // null is the SDK's representation of an unresolved serverTimestamp() (the
  // local-cache tick before the server acks); treat it the same as missing.
  if (val === undefined || val === null) return null;
  else if (val instanceof Timestamp) return val;
  else {
    console.error("serverModifiedAt is not a Timestamp", val);
    return null;
  }
}

export function fromFirestore(id: string, data: Record<string, unknown>): Todo {
  const modifiedAt =
    typeof data.modifiedAt === "number" ? data.modifiedAt : Date.now();
  return {
    id,
    text: typeof data.text === "string" ? data.text : "",
    state: readState(data.state),
    createdAt: typeof data.createdAt === "number" ? data.createdAt : Date.now(),
    modifiedAt,
    serverModifiedAt: readServerTimestamp(data.serverModifiedAt),
    snoozeUntil: typeof data.snoozeUntil === "string" ? data.snoozeUntil : null,
    pinned: data.pinned === true,
    position: readPosition(data.position),
    deleted: data.deleted === true,
  };
}

/** Legacy rows carry state "SNOOZED"; snoozeUntil is the only snooze marker now. */
export function readState(v: unknown): TodoState {
  return v === "DONE" ? "DONE" : "ACTIVE";
}

export function isoToEpoch(iso: string): number {
  return new Date(iso).getTime();
}

export function epochToIso(epoch: number): string {
  const d = new Date(epoch);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

/** Snoozed-ness is derived, not stored: a live todo whose snooze time is still ahead. */
export function isSnoozed(t: Todo, now: number): boolean {
  return (
    t.state !== "DONE" &&
    t.snoozeUntil !== null &&
    isoToEpoch(t.snoozeUntil) > now
  );
}

export function matchesFilter(t: Todo, filter: Filter, now: number): boolean {
  switch (filter) {
    case "ALL":
      return true;
    case "DONE":
      return t.state === "DONE";
    case "SNOOZED":
      return isSnoozed(t, now);
    case "ACTIVE":
      return t.state !== "DONE" && !isSnoozed(t, now);
  }
}

/** A row's own place: where a move put it, else its modify time. */
export function ownKey(t: Todo): string {
  return t.position ?? timeKey(t.modifiedAt) + idKey(t.id);
}

/** A row's place in Active: an unsnoozed row sits at its unsnooze time. */
export function activeKey(t: Todo): string {
  if (t.snoozeUntil) return timeKey(isoToEpoch(t.snoozeUntil)) + ownKey(t);
  else return ownKey(t);
}

/** The key a filter's list orders by, newest first (Snoozed orders by snooze time before it). */
export function listKey(t: Todo, filter: Filter): string {
  return filter === "ACTIVE" ? activeKey(t) : ownKey(t);
}

/** Flips `pinned`; only an active row also drops its was-snoozed marker. */
export function withPinToggled(t: Todo, now: number): Todo {
  const pinned = !t.pinned;
  if (!matchesFilter(t, "ACTIVE", now)) {
    return { ...t, pinned, position: null, modifiedAt: now };
  } else {
    return { ...t, pinned, position: null, snoozeUntil: null, modifiedAt: now };
  }
}

function compareText(a: string, b: string): number {
  if (a < b) return -1;
  else if (a > b) return 1;
  else return 0;
}

export function sortForFilter(todos: readonly Todo[], filter: Filter): Todo[] {
  const keyed = todos.map((todo) => ({
    todo,
    key: listKey(todo, filter),
    snoozeAt:
      filter === "SNOOZED" && todo.snoozeUntil
        ? isoToEpoch(todo.snoozeUntil)
        : 0,
  }));
  keyed.sort(
    (a, b) =>
      a.snoozeAt - b.snoozeAt ||
      compareText(b.key, a.key) ||
      compareText(a.todo.id, b.todo.id),
  );
  return keyed.map((entry) => entry.todo);
}

export function rankBySearch(todos: readonly Todo[], query: string): Todo[] {
  const words = query
    .trim()
    .toLowerCase()
    .split(/\s+/)
    .filter((w) => w.length > 0);
  if (words.length === 0) return todos.slice();
  const patterns = words.map((w) => new RegExp(`\\b${escapeRegex(w)}`));
  const scored: { t: Todo; score: number }[] = [];
  for (const t of todos) {
    const hay = plainText(t.text).toLowerCase();
    let score = 0;
    for (const p of patterns) {
      if (p.test(hay)) score++;
    }
    if (score > 0) scored.push({ t, score });
  }
  scored.sort((a, b) => b.score - a.score);
  return scored.map((s) => s.t);
}

function escapeRegex(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
