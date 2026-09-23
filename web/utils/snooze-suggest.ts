// Learned snooze suggestions (see CLAUDE.md "Snooze Options"). Pure functions only.

const DAY_MS = 24 * 60 * 60 * 1000;
const HOUR_MS = 60 * 60 * 1000;
const MINUTE_MS = 60 * 1000;
const TOD_HALF_LIFE_DAYS = 21;
const SLOT_MINUTES = 5;
const QUICK_TIMES_MAX = 4;
const SCORE_EPS = 1e-9;
const LITTLE_WHILE_MAX_HOURS = 3;

export const KEY_RE =
  /^(D[0-6]|W[1-4]|Wd[1-7]|Wn[1-7]|Dom1|Dom15|DomL|Mo[1-3])(@([01]\d|2[0-3])[0-5][05]|_h([0-9]|1[0-2]))$/;
export const SLOT_RE = /^([01]\d|2[0-3])[0-5][05]$/;

export type SnoozeSource = "suggestion" | "last" | "custom";

export type SetEntry = { c: number; t: number };
export type DevicePartition = {
  deviceId: string;
  sets: Record<string, SetEntry>;
  // Date-independent counts per "HHMM" clock slot, for the custom picker's quick times.
  tod: Record<string, SetEntry>;
  lastCustom: { target: number; at: number } | null;
};

export function emptyPartition(deviceId: string): DevicePartition {
  return { deviceId, sets: {}, tod: {}, lastCustom: null };
}

function addDays(d: Date, days: number): Date {
  const out = new Date(d);
  out.setDate(out.getDate() + days);
  return out;
}

function dateOnly(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate());
}

/** Calendar date of `d` shifted so the "day" runs 05:00 -> 04:59 (a post-midnight pick is still "tonight"). */
function snoozeDay(d: Date): Date {
  return dateOnly(
    new Date(
      d.getFullYear(),
      d.getMonth(),
      d.getDate(),
      d.getHours() - 5,
      d.getMinutes(),
    ),
  );
}

function addDaysToSnoozeDay(now: Date, n: number): Date {
  return addDays(snoozeDay(now), n);
}

function minutesSince0500(d: Date): number {
  const m = d.getHours() * 60 + d.getMinutes();
  return (((m - 300) % 1440) + 1440) % 1440;
}

export type Bucket = "morning" | "afternoon" | "evening" | "night";

export function bucketOf(minsSince0500: number): Bucket {
  if (minsSince0500 < 420) return "morning";
  if (minsSince0500 < 720) return "afternoon";
  if (minsSince0500 < 960) return "evening";
  return "night";
}

function pad2(n: number): string {
  return String(n).padStart(2, "0");
}

/** The "HHMM" wall-clock slot of `d`, rounded to 5 minutes but never past the snooze day's 04:55. */
export function slotOf(d: Date): string {
  const slot = Math.min(
    1440 - SLOT_MINUTES,
    Math.round(minutesSince0500(d) / SLOT_MINUTES) * SLOT_MINUTES,
  );
  const clock = (slot + 300) % 1440;
  return `${pad2(Math.floor(clock / 60))}${pad2(clock % 60)}`;
}

function slotClockMinutes(slot: string): number {
  return (
    Number.parseInt(slot.slice(0, 2), 10) * 60 +
    Number.parseInt(slot.slice(2), 10)
  );
}

/** The next occurrence of `day`-of-month strictly after `a` (an occurrence on `a` itself doesn't count). */
function nextDom(a: Date, day: number): Date {
  const y = a.getFullYear();
  const m = a.getMonth();
  let candidate = new Date(y, m, day);
  if (dateOnly(candidate).getTime() <= dateOnly(a).getTime()) {
    candidate = new Date(y, m + 1, day);
  }
  return candidate;
}

function monthEnd(y: number, m: number): Date {
  return new Date(y, m + 1, 0);
}

/** The next month-end strictly after `a` (a is a month-end doesn't count). */
function nextMonthEnd(a: Date): Date {
  let candidate = monthEnd(a.getFullYear(), a.getMonth());
  if (dateOnly(candidate).getTime() <= dateOnly(a).getTime()) {
    candidate = monthEnd(a.getFullYear(), a.getMonth() + 1);
  }
  return candidate;
}

function addMonthsClamped(a: Date, k: number): Date {
  const day = a.getDate();
  const y = a.getFullYear();
  const targetMonth = a.getMonth() + k;
  const daysInTargetMonth = new Date(y, targetMonth + 1, 0).getDate();
  return new Date(y, targetMonth, Math.min(day, daysInTargetMonth));
}

function sameDate(a: Date, b: Date): boolean {
  return dateOnly(a).getTime() === dateOnly(b).getTime();
}

/** ISO-8601 weekday number: Monday=1 .. Sunday=7 (matches java.time.DayOfWeek.value on Android, so weekday keys are identical cross-platform). */
function isoDow(d: Date): number {
  const jsDay = d.getDay();
  return jsDay === 0 ? 7 : jsDay;
}

function nextWeekdayInRange(
  now: Date,
  dow: number,
  minD: number,
  maxD: number,
): Date {
  const sd = snoozeDay(now);
  for (let d = minD; d <= maxD; d++) {
    const cand = addDays(sd, d);
    if (isoDow(cand) === dow) return cand;
  }
  throw new Error(`unreachable: no ${dow} in [${minD},${maxD}]`);
}

type Family = "D" | "W" | "Wd" | "Wn" | "Dom" | "DomL" | "Mo";

function familyOf(dateRule: string): Family {
  if (dateRule === "DomL") return "DomL";
  if (dateRule.startsWith("Dom")) return "Dom";
  if (dateRule.startsWith("Mo")) return "Mo";
  if (dateRule.startsWith("Wd")) return "Wd";
  if (dateRule.startsWith("Wn")) return "Wn";
  if (dateRule.startsWith("W")) return "W";
  return "D";
}

function numOf(dateRule: string): number {
  const m = dateRule.match(/(\d+)$/);
  return m ? Number.parseInt(m[1], 10) : 0;
}

/** Every date rule `t` satisfies relative to `a`, all read on snooze days. */
function dateRulesFor(a: Date, t: Date): string[] {
  const sa = snoozeDay(a);
  const st = snoozeDay(t);
  const d = Math.round((st.getTime() - sa.getTime()) / DAY_MS);
  if (d < 0) return [];
  const rules: string[] = [];
  if (d <= 6) rules.push(`D${d}`);
  if (d === 7 || d === 14 || d === 21 || d === 28) rules.push(`W${d / 7}`);
  const dow = isoDow(st);
  if (d >= 1 && d <= 7) rules.push(`Wd${dow}`);
  if (d >= 8 && d <= 14) rules.push(`Wn${dow}`);
  if (d >= 1) {
    for (const day of [1, 15]) {
      if (sameDate(st, nextDom(sa, day))) rules.push(`Dom${day}`);
    }
    if (sameDate(st, nextMonthEnd(sa))) rules.push("DomL");
    for (let k = 1; k <= 3; k++) {
      if (sameDate(st, addMonthsClamped(sa, k))) rules.push(`Mo${k}`);
    }
  }
  return rules;
}

function isShortFamily(dateRule: string): boolean {
  const fam = familyOf(dateRule);
  return fam === "D" || fam === "W" || fam === "Wd" || fam === "Wn";
}

/** The snooze day a date rule points to from `now`; the D/W/Wd/Wn helpers shift `now` themselves. */
function resolveDateRule(dateRule: string, now: Date): Date {
  if (dateRule === "DomL") return nextMonthEnd(snoozeDay(now));
  const fam = familyOf(dateRule);
  const num = numOf(dateRule);
  switch (fam) {
    case "D":
      return addDaysToSnoozeDay(now, num);
    case "W":
      return addDaysToSnoozeDay(now, num * 7);
    case "Wd":
      return nextWeekdayInRange(now, num, 1, 7);
    case "Wn":
      return nextWeekdayInRange(now, num, 8, 14);
    case "Dom":
      return nextDom(snoozeDay(now), num);
    case "Mo":
      return addMonthsClamped(snoozeDay(now), num);
    default:
      throw new Error(`unresolvable date rule ${dateRule}`);
  }
}

/** Local wall-clock fields read as if UTC, so differences count calendar time, not elapsed time. */
function naiveLocal(d: Date): number {
  return Date.UTC(
    d.getFullYear(),
    d.getMonth(),
    d.getDate(),
    d.getHours(),
    d.getMinutes(),
    d.getSeconds(),
    d.getMilliseconds(),
  );
}

/** All composite keys a commit from `a` to `t` matches, e.g. ["D1@0900", "Wd1@0900"]. */
export function extractKeys(a: Date, t: Date): string[] {
  const slot = slotOf(t);
  const timedKeys = dateRulesFor(a, t).map((r) => `${r}@${slot}`);

  // Whole days on the wall clock, then hours as elapsed time — the inverse of resolveKey.
  const wallMin = Math.round((naiveLocal(t) - naiveLocal(a)) / MINUTE_MS);
  const dEff = Math.round(wallMin / 1440);
  const base = addDays(a, dEff);
  const deltaMin = Math.round((t.getTime() - base.getTime()) / MINUTE_MS);
  const h = Math.round(deltaMin / 60);

  let offsetKeys: string[] = [];
  if (h >= 0 && h <= 12) {
    const offsetRules = dateRulesFor(a, base).filter((r) => isShortFamily(r));
    offsetKeys = offsetRules
      .filter((r) => !(r === "D0" && h === 0))
      .map((r) => `${r}_h${h}`);
  }

  return [...timedKeys, ...offsetKeys];
}

export function isTimedKey(keyId: string): boolean {
  return keyId.includes("@");
}

type ParsedKey =
  | { dateRule: string; kind: "timed"; clockMinutes: number }
  | { dateRule: string; kind: "offset"; hours: number };

function parseKey(keyId: string): ParsedKey {
  const at = keyId.indexOf("@");
  if (at >= 0) {
    return {
      dateRule: keyId.slice(0, at),
      kind: "timed",
      clockMinutes: slotClockMinutes(keyId.slice(at + 1)),
    };
  }
  const h = keyId.indexOf("_h");
  return {
    dateRule: keyId.slice(0, h),
    kind: "offset",
    hours: Number.parseInt(keyId.slice(h + 2), 10),
  };
}

export function classH(keyId: string): number {
  const { dateRule } = parseKey(keyId);
  const fam = familyOf(dateRule);
  return fam === "Dom" || fam === "DomL" || fam === "Mo" ? 60 : 21;
}

function classHForSet(keys: string[]): number {
  return Math.max(...keys.map(classH));
}

export function canonicalSetId(keys: string[]): string {
  return [...keys].sort().join("__");
}

export function splitSetId(setId: string): string[] {
  return setId.split("__");
}

export type CommitUndoSnapshot = {
  setId: string | null;
  prevSet: SetEntry | null;
  // Null when the commit credited no quick-time slot.
  tod: { slot: string; prev: SetEntry | null } | null;
  prevLastCustom: DevicePartition["lastCustom"];
};

export type CommitResult = {
  next: DevicePartition;
  undoSnapshot: CommitUndoSnapshot;
};

function decay(value: number, elapsedMs: number, halfLifeDays: number): number {
  return value * 2 ** (-elapsedMs / (halfLifeDays * DAY_MS));
}

/** The quick-time slot a commit credits: none for same-day snoozes or hours-offset picks. */
export function quickTimeSlot(
  at: number,
  target: number,
  source: SnoozeSource,
  pickedKey: string | null,
): string | null {
  const t = new Date(target);
  if (snoozeDay(t).getTime() === snoozeDay(new Date(at)).getTime()) {
    return null;
  }
  if (source === "suggestion" && pickedKey !== null && !isTimedKey(pickedKey)) {
    return null;
  }
  return slotOf(t);
}

export function commit(
  partition: DevicePartition,
  at: number,
  target: number,
  source: SnoozeSource,
  pickedKey: string | null = null,
): CommitResult {
  const keys = extractKeys(new Date(at), new Date(target));

  const nextSets = { ...partition.sets };
  let undoSetId: string | null = null;
  let prevSet: SetEntry | null = null;
  if (keys.length > 0) {
    const setId = canonicalSetId(keys);
    const prev = partition.sets[setId] ?? null;
    prevSet = prev;
    const decayed = prev ? decay(prev.c, at - prev.t, classHForSet(keys)) : 0;
    nextSets[setId] = { c: decayed + 1, t: at };
    undoSetId = setId;
  }

  const nextTod = { ...partition.tod };
  let undoTod: CommitUndoSnapshot["tod"] = null;
  const slot = quickTimeSlot(at, target, source, pickedKey);
  if (slot !== null) {
    const prev = partition.tod[slot] ?? null;
    const decayed = prev ? decay(prev.c, at - prev.t, TOD_HALF_LIFE_DAYS) : 0;
    nextTod[slot] = { c: decayed + 1, t: at };
    undoTod = { slot, prev };
  }

  const prevLastCustom = partition.lastCustom;
  const lastCustom =
    source === "custom" ? { target, at } : partition.lastCustom;

  return {
    next: {
      deviceId: partition.deviceId,
      sets: nextSets,
      tod: nextTod,
      lastCustom,
    },
    undoSnapshot: { setId: undoSetId, prevSet, tod: undoTod, prevLastCustom },
  };
}

export function undoCommit(
  partition: DevicePartition,
  snap: CommitUndoSnapshot,
): DevicePartition {
  const sets = { ...partition.sets };
  if (snap.setId) {
    if (snap.prevSet) sets[snap.setId] = snap.prevSet;
    else delete sets[snap.setId];
  }
  const tod = { ...partition.tod };
  if (snap.tod) {
    if (snap.tod.prev) tod[snap.tod.slot] = snap.tod.prev;
    else delete tod[snap.tod.slot];
  }
  return {
    deviceId: partition.deviceId,
    sets,
    tod,
    lastCustom: snap.prevLastCustom,
  };
}

function snapTo5Min(epoch: number): number {
  return Math.round(epoch / (5 * MINUTE_MS)) * (5 * MINUTE_MS);
}

/** Resolves a composite key to a concrete epoch given `now`, or null if it can't happen (past / <1min out). */
export function resolveKey(keyId: string, now: Date): number | null {
  const key = parseKey(keyId);
  let dayDate: Date;
  try {
    dayDate = resolveDateRule(key.dateRule, now);
  } catch {
    return null;
  }
  let epoch: number;
  if (key.kind === "offset") {
    const numDays = Math.round(
      (dayDate.getTime() - snoozeDay(now).getTime()) / DAY_MS,
    );
    // Calendar days first (DST-safe), then the hours as elapsed time.
    const base = new Date(now);
    base.setDate(base.getDate() + numDays);
    epoch = snapTo5Min(base.getTime() + key.hours * HOUR_MS);
  } else {
    // Wall-clock minutes past 05:00, so a night time keeps its clock reading across DST.
    epoch = new Date(
      dayDate.getFullYear(),
      dayDate.getMonth(),
      dayDate.getDate(),
      5,
      (key.clockMinutes - 300 + 1440) % 1440,
    ).getTime();
  }
  if (epoch <= now.getTime() + 60_000) return null;
  return epoch;
}

export type RowIcon =
  | "offset"
  | "todayDay"
  | "todayNight"
  | "tomorrow"
  | "days"
  | "weekday"
  | "monthly";

// `text` is the label without its time, for layouts that show the time in its own column.
export type Row = {
  time: number;
  text: string;
  icon: RowIcon;
  keyId: string;
  score: number;
};

export type LastRow = { time: number; text: string };

// Lower wins: today/tomorrow, then named days (Wd/Wn/Dom/DomL), then counted ones (D/W/Mo), shorter period first.
function tieRank(keyId: string): number {
  const { dateRule } = parseKey(keyId);
  if (dateRule === "D0" || dateRule === "D1") return 0;
  switch (familyOf(dateRule)) {
    case "Wd":
      return 1;
    case "Wn":
      return 2;
    case "Dom":
    case "DomL":
      return 3;
    case "D":
      return 4;
    case "W":
      return 5;
    default: // Mo
      return 6;
  }
}

function betterTie(a: string, b: string): boolean {
  const ra = tieRank(a);
  const rb = tieRank(b);
  if (ra !== rb) return ra < rb;
  const aTimed = isTimedKey(a);
  const bTimed = isTimedKey(b);
  if (aTimed !== bTimed) return aTimed;
  // Code-unit order, not localeCompare, to match Android's String.compareTo.
  return a < b;
}

const DEFAULT_WINDOW_MS = 30 * MINUTE_MS;
const DEFAULT_FLOOR = 0.05;

// The platforms sum doubles in different orders, so an exact == could pick different winners.
function sameScore(a: number, b: number): boolean {
  return Math.abs(a - b) <= SCORE_EPS * Math.max(1, Math.abs(a), Math.abs(b));
}

export function rank(
  partitions: DevicePartition[],
  now: number,
  n = 5,
  floor = DEFAULT_FLOOR,
  windowMs = DEFAULT_WINDOW_MS,
): Row[] {
  const liveSets = new Map<string, number>();
  const setMembers = new Map<string, string[]>();
  for (const part of partitions) {
    for (const [setId, entry] of Object.entries(part.sets)) {
      const members = setMembers.get(setId) ?? splitSetId(setId);
      setMembers.set(setId, members);
      const live = decay(entry.c, now - entry.t, classHForSet(members));
      liveSets.set(setId, (liveSets.get(setId) ?? 0) + live);
    }
  }

  const available = new Set(liveSets.keys());
  const nowDate = new Date(now);
  const resolved = new Map<string, number | null>();
  const resolveCached = (key: string): number | null => {
    if (!resolved.has(key)) resolved.set(key, resolveKey(key, nowDate));
    return resolved.get(key) ?? null;
  };
  const rows: Row[] = [];

  while (rows.length < n) {
    const agg = new Map<string, number>();
    for (const setId of available) {
      const live = liveSets.get(setId) ?? 0;
      if (live <= 0) continue;
      for (const key of setMembers.get(setId) ?? []) {
        agg.set(key, (agg.get(key) ?? 0) + live);
      }
    }

    let best: { key: string; score: number; time: number } | null = null;
    for (const [key, score] of agg) {
      const time = resolveCached(key);
      if (time === null) continue;
      if (
        !best ||
        (sameScore(score, best.score)
          ? betterTie(key, best.key)
          : score > best.score)
      ) {
        best = { key, score, time };
      }
    }
    if (!best || best.score <= floor) break;

    const spent = new Set([best.key]);
    for (const [key] of agg) {
      const time = resolveCached(key);
      if (time !== null && Math.abs(time - best.time) <= windowMs) {
        spent.add(key);
      }
    }

    rows.push({
      time: best.time,
      text: labelParts(best.key, best.time, nowDate).text,
      icon: rowIcon(best.key, best.time, nowDate),
      keyId: best.key,
      score: best.score,
    });

    for (const setId of Array.from(available)) {
      const members = setMembers.get(setId) ?? [];
      if (members.some((m) => spent.has(m))) available.delete(setId);
    }
  }

  rows.sort((x, y) => x.time - y.time);
  return rows;
}

export function lastRow(
  partitions: DevicePartition[],
  now: number,
  rows: Row[],
  windowMs = DEFAULT_WINDOW_MS,
): LastRow | null {
  let best: { target: number; at: number } | null = null;
  for (const p of partitions) {
    if (p.lastCustom && (!best || p.lastCustom.at > best.at))
      best = p.lastCustom;
  }
  if (!best || best.target <= now) return null;
  const target = best.target;
  if (rows.some((r) => Math.abs(r.time - target) <= windowMs)) return null;
  return { time: target, text: `Last · ${formatters().date.format(target)}` };
}

export type QuickTime = {
  // Minutes after local midnight.
  clockMinutes: number;
  weight: number;
};

function clockDistance(a: number, b: number): number {
  const d = Math.abs(a - b);
  return Math.min(d, 1440 - d);
}

/** Up to four learned clock times at least 30 minutes apart, strongest first, returned in clock order. */
export function quickTimes(
  partitions: DevicePartition[],
  now: number,
  floor = DEFAULT_FLOOR,
): QuickTime[] {
  const weights = new Map<number, number>();
  for (const p of partitions) {
    for (const [slot, entry] of Object.entries(p.tod)) {
      const clock = slotClockMinutes(slot);
      const live = decay(entry.c, now - entry.t, TOD_HALF_LIFE_DAYS);
      weights.set(clock, (weights.get(clock) ?? 0) + live);
    }
  }
  const sinceDayStart = (clock: number) => (clock - 300 + 1440) % 1440;
  const windowMinutes = DEFAULT_WINDOW_MS / MINUTE_MS;
  const out: QuickTime[] = [];
  while (out.length < QUICK_TIMES_MAX) {
    let best: QuickTime | null = null;
    for (const [clockMinutes, weight] of weights) {
      if (
        !best ||
        (sameScore(weight, best.weight)
          ? sinceDayStart(clockMinutes) < sinceDayStart(best.clockMinutes)
          : weight > best.weight)
      ) {
        best = { clockMinutes, weight };
      }
    }
    if (!best || best.weight <= floor) break;
    out.push(best);
    for (const clock of Array.from(weights.keys())) {
      if (clockDistance(clock, best.clockMinutes) <= windowMinutes) {
        weights.delete(clock);
      }
    }
  }
  return out.sort((x, y) => x.clockMinutes - y.clockMinutes);
}

/** `clockMinutes` on the calendar date `dateYmd` ("YYYY-MM-DD"), exactly as typing that time would give. */
export function quickTimeEpoch(
  dateYmd: string,
  clockMinutes: number,
): number | null {
  const match = dateYmd.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!match) return null;
  const [, y, m, d] = match;
  return new Date(
    Number(y),
    Number(m) - 1,
    Number(d),
    Math.floor(clockMinutes / 60),
    clockMinutes % 60,
  ).getTime();
}

const WEEKDAY_NAMES = (() => {
  const fmt = new Intl.DateTimeFormat(undefined, { weekday: "long" });
  // 2023-01-02 was a Monday; index 0 = ISO dow 1 (Monday) .. index 6 = ISO dow 7 (Sunday).
  return Array.from({ length: 7 }, (_, i) =>
    fmt.format(new Date(2023, 0, 2 + i)),
  );
})();

function weekdayName(isoDowNum: number): string {
  return WEEKDAY_NAMES[isoDowNum - 1];
}

function ordinal(n: number): string {
  const v = n % 100;
  const suffixes = ["th", "st", "nd", "rd"];
  const suffix = suffixes[(v - 20) % 10] ?? suffixes[v] ?? suffixes[0];
  return `${n}${suffix}`;
}

type Formatters = {
  zone: string;
  time: Intl.DateTimeFormat;
  date: Intl.DateTimeFormat;
};
let formatterCache: Formatters | null = null;

// Rebuilt when the system time zone changes mid-session; a formatter pins its zone at construction.
function formatters(): Formatters {
  const zone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  if (formatterCache?.zone !== zone) {
    formatterCache = {
      zone,
      time: new Intl.DateTimeFormat(undefined, {
        hour: "2-digit",
        minute: "2-digit",
        hourCycle: "h23",
      }),
      date: new Intl.DateTimeFormat(undefined, {
        month: "short",
        day: "numeric",
      }),
    };
  }
  return formatterCache;
}

export function formatClock(epoch: number): string {
  return formatters().time.format(epoch);
}

export type DayPhrase =
  | "today"
  | "tomorrow"
  | "inDays"
  | "inWeeks"
  | "this"
  | "next"
  | "inTwoWeeks"
  | "calendar";

/** "Today" is the calendar day but the target's day follows the 05:00 rule, so 09:00 seen at 04:50 is "this morning". */
export function dayPhraseOf(
  dateRule: string,
  resolved: Date,
  now: Date,
): { phrase: DayPhrase; dist: number; weekday: number } {
  const targetDay = snoozeDay(resolved);
  const dist = Math.max(
    0,
    Math.round((targetDay.getTime() - dateOnly(now).getTime()) / DAY_MS),
  );
  const weekday = isoDow(targetDay);
  const fam = familyOf(dateRule);
  let phrase: DayPhrase;
  if (fam === "Dom" || fam === "DomL" || fam === "Mo") phrase = "calendar";
  else if (dist === 0) phrase = "today";
  else if (fam === "Wd" || fam === "Wn") {
    if (dist <= 6) phrase = "this";
    else if (dist <= 13) phrase = "next";
    else phrase = "inTwoWeeks";
  } else if (dist === 1) phrase = "tomorrow";
  else if (dist % 7 === 0 && dist <= 28) phrase = "inWeeks";
  else phrase = "inDays";
  return { phrase, dist, weekday };
}

function calendarLabelPart(dateRule: string): string {
  if (dateRule === "DomL") return "End of month";
  const num = numOf(dateRule);
  if (familyOf(dateRule) === "Dom") return `The ${ordinal(num)}`;
  return num === 1 ? "In a month" : `In ${num} months`;
}

function dateLabelPart(
  dateRule: string,
  resolved: Date,
  now: Date,
): { text: string; adjectival: boolean } {
  const { phrase, dist, weekday } = dayPhraseOf(dateRule, resolved, now);
  switch (phrase) {
    case "today":
      return { text: "Today", adjectival: false };
    case "tomorrow":
      return { text: "Tomorrow", adjectival: true };
    case "inDays":
      return { text: `In ${dist} days`, adjectival: false };
    case "inWeeks":
      return dist === 7
        ? { text: "In a week", adjectival: false }
        : { text: `In ${dist / 7} weeks`, adjectival: false };
    case "this":
      return { text: `This ${weekdayName(weekday)}`, adjectival: true };
    case "next":
      return { text: `Next ${weekdayName(weekday)}`, adjectival: true };
    case "inTwoWeeks":
      return { text: `${weekdayName(weekday)} in 2 weeks`, adjectival: false };
    default:
      return { text: calendarLabelPart(dateRule), adjectival: false };
  }
}

const TODAY_BUCKET_TEXT: Record<Bucket, string> = {
  morning: "This morning",
  afternoon: "This afternoon",
  evening: "This evening",
  night: "Tonight",
};

export function labelParts(
  keyId: string,
  resolvedEpoch: number,
  now: Date,
): { text: string; time: string; parenthesized: boolean } {
  const key = parseKey(keyId);
  const time = formatClock(resolvedEpoch);
  const resolved = new Date(resolvedEpoch);

  if (key.kind === "offset") {
    if (key.dateRule === "D0") {
      const text =
        key.hours <= LITTLE_WHILE_MAX_HOURS
          ? "In a little while"
          : "Much later";
      return { text, time, parenthesized: true };
    }
    const { text } = dateLabelPart(key.dateRule, resolved, now);
    return key.hours === 0
      ? { text: `${text}, same time`, time, parenthesized: true }
      : { text, time, parenthesized: false };
  }

  const region = bucketOf(minutesSince0500(resolved));
  const { phrase } = dayPhraseOf(key.dateRule, resolved, now);
  if (phrase === "today") {
    return { text: TODAY_BUCKET_TEXT[region], time, parenthesized: false };
  }
  const { text, adjectival } = dateLabelPart(key.dateRule, resolved, now);
  return {
    text: adjectival ? `${text} ${region}` : text,
    time,
    parenthesized: false,
  };
}

export function labelFor(
  keyId: string,
  resolvedEpoch: number,
  now: Date,
): string {
  const { text, time, parenthesized } = labelParts(keyId, resolvedEpoch, now);
  return parenthesized ? `${text} (${time})` : `${text}, ${time}`;
}

/** Mirrors Android's `rowIcon`. */
export function rowIcon(
  keyId: string,
  resolvedEpoch: number,
  now: Date,
): RowIcon {
  const key = parseKey(keyId);
  if (key.kind === "offset") return "offset";
  const resolved = new Date(resolvedEpoch);
  const { dist } = dayPhraseOf(key.dateRule, resolved, now);
  const fam = familyOf(key.dateRule);
  if (dist === 0) {
    const region = bucketOf(minutesSince0500(resolved));
    return region === "morning" || region === "afternoon"
      ? "todayDay"
      : "todayNight";
  } else if (dist === 1) return "tomorrow";
  else if (fam === "D" || fam === "W") return "days";
  else if (fam === "Wd" || fam === "Wn") return "weekday";
  else return "monthly";
}
