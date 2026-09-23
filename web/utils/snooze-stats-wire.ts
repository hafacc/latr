import {
  type DevicePartition,
  KEY_RE,
  type SetEntry,
  SLOT_RE,
  splitSetId,
} from "./snooze-suggest";

// The Firestore / localStorage shape of one device's partition; the device id is the path, not a field.
export type WirePartition = {
  sets: DevicePartition["sets"];
  tod: DevicePartition["tod"];
  lastCustom: DevicePartition["lastCustom"];
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

export function toWire(partition: DevicePartition): WirePartition {
  return {
    sets: partition.sets,
    tod: partition.tod,
    lastCustom: partition.lastCustom ?? null,
  };
}

/** Parses an untrusted stored or remote partition, dropping anything malformed or from an older key shape. */
export function fromWire(deviceId: string, raw: unknown): DevicePartition {
  const record = isRecord(raw) ? raw : {};
  return {
    deviceId,
    sets: normalizeCounters(record.sets, (id) =>
      splitSetId(id).every((key) => KEY_RE.test(key)),
    ),
    tod: normalizeCounters(record.tod, (slot) => SLOT_RE.test(slot)),
    lastCustom: normalizeLastCustom(record.lastCustom),
  };
}
