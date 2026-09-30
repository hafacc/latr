import {
  type DevicePartition,
  KEY_RE,
  PICK_RE,
  type SetEntry,
  SLOT_RE,
  splitSetId,
  upgradeLegacySets,
} from "./snooze-suggest";

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

/** `snoozePickLog/global` as 21 counts: index 0 is `none`, 1..20 the ranks; missing or non-integer fields read 0. */
export function globalPickCounts(raw: unknown): number[] {
  const data = isRecord(raw) ? raw : {};
  return Array.from({ length: 21 }, (_, position) => {
    const value = data[position === 0 ? "none" : `${position}`];
    return Number.isInteger(value) ? (value as number) : 0;
  });
}
