import {
  type DevicePartition,
  KEY_RE,
  PICK_RE,
  type SetEntry,
  SLOT_RE,
  setHalfLifeDays,
  splitSetId,
  TOD_HALF_LIFE_DAYS,
  upgradeLegacySets,
} from "./snooze-suggest";

const DAY_MS = 24 * 60 * 60 * 1000;

// A user's shared counts are saved scaled up by how late each vote was cast, doubling every half-life from this instant, so a vote is a plain addition and no time is saved. Doubles overflow about 59 years on.
export const VOTE_EPOCH = Date.UTC(2026, 0, 1);

// Amounts to add to the shared counts, in their scaled units.
export type VoteDeltas = {
  sets: Record<string, number>;
  tod: Record<string, number>;
};

// What `foldBlocks` adds to the shared counts; `ids` are the device blocks it covers.
export type Fold = {
  ids: string[];
  deltas: VoteDeltas;
  picks: Record<string, number>;
  lastCustom: DevicePartition["lastCustom"];
};

// Partitions without it were saved before clock keys counted from their first hit, and are upgraded on read.
const PARTITION_VERSION = 2;

// The Firestore / localStorage shape of one device's partition; the device id is the path, not a field.
export type WirePartition = {
  v: number;
  sets: DevicePartition["sets"];
  tod: DevicePartition["tod"];
  lastCustom: DevicePartition["lastCustom"];
  picks: DevicePartition["picks"];
};

function isRecord(raw: unknown): raw is Record<string, unknown> {
  return typeof raw === "object" && raw !== null;
}

function normalizeEntry(raw: unknown): SetEntry | null {
  if (isRecord(raw) && Number.isFinite(raw.c) && Number.isFinite(raw.t)) {
    return { c: raw.c as number, t: raw.t as number };
  }
  return null;
}

function normalizeCounters(
  raw: unknown,
  validId: (id: string) => boolean,
): Record<string, SetEntry> {
  const out: Record<string, SetEntry> = {};
  if (!isRecord(raw)) return out;
  for (const [id, entry] of Object.entries(raw)) {
    const normalized = normalizeEntry(entry);
    if (normalized && validId(id)) out[id] = normalized;
  }
  return out;
}

function normalizeLastCustom(raw: unknown): DevicePartition["lastCustom"] {
  if (isRecord(raw) && Number.isFinite(raw.target) && Number.isFinite(raw.at)) {
    return { target: raw.target as number, at: raw.at as number };
  }
  return null;
}

function normalizePicks(raw: unknown): Record<string, number> {
  const out: Record<string, number> = {};
  if (!isRecord(raw)) return out;
  for (const [key, count] of Object.entries(raw)) {
    if (
      PICK_RE.test(key) &&
      Number.isSafeInteger(count) &&
      (count as number) > 0
    ) {
      out[key] = count as number;
    }
  }
  return out;
}

const emptyBlock: DevicePartition = {
  deviceId: "",
  sets: {},
  tod: {},
  lastCustom: null,
  picks: {},
};

export function toWire(partition: DevicePartition): WirePartition {
  return {
    v: PARTITION_VERSION,
    sets: partition.sets,
    tod: partition.tod,
    lastCustom: partition.lastCustom ?? null,
    picks: partition.picks,
  };
}

/** Parses an untrusted stored or remote partition, dropping anything malformed or from an older key shape. */
export function fromWire(deviceId: string, raw: unknown): DevicePartition {
  const record = isRecord(raw) ? raw : {};
  const sets = normalizeCounters(record.sets, (id) =>
    splitSetId(id).every((key) => KEY_RE.test(key)),
  );
  const current = typeof record.v === "number" && record.v >= PARTITION_VERSION;
  return {
    deviceId,
    sets: current ? sets : upgradeLegacySets(sets),
    tod: normalizeCounters(record.tod, (slot) => SLOT_RE.test(slot)),
    lastCustom: normalizeLastCustom(record.lastCustom),
    picks: normalizePicks(record.picks),
  };
}

function scaled(entry: SetEntry | undefined, halfLifeDays: number): number {
  if (!entry) return 0;
  return entry.c * 2 ** ((entry.t - VOTE_EPOCH) / (halfLifeDays * DAY_MS));
}

function sharedCounters(
  raw: unknown,
  validId: (id: string) => boolean,
): Record<string, SetEntry> {
  const out: Record<string, SetEntry> = {};
  if (!isRecord(raw)) return out;
  for (const [id, count] of Object.entries(raw)) {
    if (validId(id) && Number.isFinite(count) && (count as number) > 0) {
      out[id] = { c: count as number, t: VOTE_EPOCH };
    }
  }
  return out;
}

/** Parses `users/{uid}.snoozeVotes`: each scaled count reads as that count at `VOTE_EPOCH`; counts at or below 0 are dropped. */
export function sharedFromWire(raw: unknown): DevicePartition {
  const record = isRecord(raw) ? raw : {};
  return {
    deviceId: "shared",
    sets: sharedCounters(record.sets, (id) =>
      splitSetId(id).every((key) => KEY_RE.test(key)),
    ),
    tod: sharedCounters(record.tod, (slot) => SLOT_RE.test(slot)),
    lastCustom: normalizeLastCustom(record.lastCustom),
    picks: normalizePicks(record.picks),
  };
}

function counterDeltas(
  before: Record<string, SetEntry>,
  after: Record<string, SetEntry>,
  halfLifeOf: (id: string) => number,
): Record<string, number> {
  const out: Record<string, number> = {};
  for (const id of new Set([...Object.keys(before), ...Object.keys(after)])) {
    if (before[id] === after[id]) continue;
    const halfLife = halfLifeOf(id);
    const delta = scaled(after[id], halfLife) - scaled(before[id], halfLife);
    if (delta !== 0) out[id] = delta;
  }
  return out;
}

/** What to add to the shared counts to take them from `before` to `after`. */
export function voteDeltas(
  before: DevicePartition,
  after: DevicePartition,
): VoteDeltas {
  return {
    sets: counterDeltas(before.sets, after.sets, setHalfLifeDays),
    tod: counterDeltas(before.tod, after.tod, () => TOD_HALF_LIFE_DAYS),
  };
}

export function negated(deltas: VoteDeltas): VoteDeltas {
  const flip = (counts: Record<string, number>) =>
    Object.fromEntries(Object.entries(counts).map(([id, d]) => [id, -d]));
  return { sets: flip(deltas.sets), tod: flip(deltas.tod) };
}

function addInto(into: Record<string, number>, from: Record<string, number>) {
  for (const [id, amount] of Object.entries(from)) {
    into[id] = (into[id] ?? 0) + amount;
  }
}

/** The per-device blocks older builds saved under `snoozeStats`, summed for adding to the shared counts; blocks named in `folded` were added before and are skipped. */
export function foldBlocks(stats: unknown, folded: unknown): Fold {
  const done = isRecord(folded) ? folded : {};
  const fold: Fold = {
    ids: [],
    deltas: { sets: {}, tod: {} },
    picks: {},
    lastCustom: null,
  };
  if (!isRecord(stats)) return fold;
  for (const [id, raw] of Object.entries(stats)) {
    if (done[id] === true) continue;
    const block = fromWire(id, raw);
    const deltas = voteDeltas(emptyBlock, block);
    fold.ids.push(id);
    addInto(fold.deltas.sets, deltas.sets);
    addInto(fold.deltas.tod, deltas.tod);
    addInto(fold.picks, block.picks);
    if (
      block.lastCustom &&
      (!fold.lastCustom || block.lastCustom.at > fold.lastCustom.at)
    ) {
      fold.lastCustom = block.lastCustom;
    }
  }
  return fold;
}

/** `snoozePickLog/global` as 21 counts: index 0 is `none`, 1..20 the ranks; missing or non-integer fields read 0. */
export function globalPickCounts(raw: unknown): number[] {
  const data = isRecord(raw) ? raw : {};
  return Array.from({ length: 21 }, (_, position) => {
    const value = data[position === 0 ? "none" : `${position}`];
    return Number.isInteger(value) ? (value as number) : 0;
  });
}
