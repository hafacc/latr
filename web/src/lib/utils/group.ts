import { keyTime } from "./position";
import { activeKey, type Filter, isoToEpoch, ownKey, type Todo } from "./todo";

export type Bucket = {
  label: string;
  todos: Todo[];
  /** The Pinned bucket, which only pinned rows move within. */
  pinned: boolean;
  /** The earliest time a row under a date heading can have; null when the bucket isn't one. */
  floor: number | null;
  /** The time rows under a date heading are before; null for Today, which runs to now. */
  ceiling: number | null;
};

function startOfDay(epoch: number): number {
  const d = new Date(epoch);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

function daysFrom(dayStart: number, days: number): number {
  const d = new Date(dayStart);
  d.setDate(d.getDate() + days);
  return d.getTime();
}

function groupByPastKey(
  todos: readonly Todo[],
  keyFn: (t: Todo) => string,
  now: number,
): Bucket[] {
  const today = startOfDay(now);
  const yesterday = daysFrom(today, -1);
  const weekAgo = daysFrom(today, -6);
  const buckets: Bucket[] = [
    { ...makeBucket("Today"), floor: today },
    { ...makeBucket("Yesterday"), floor: yesterday, ceiling: today },
    { ...makeBucket("This week"), floor: weekAgo, ceiling: yesterday },
    { ...makeBucket("Earlier"), floor: 0, ceiling: weekAgo },
  ];
  for (const t of todos) {
    const at = keyTime(keyFn(t));
    const bucket =
      buckets.find((b) => b.floor !== null && at >= b.floor) ?? buckets[3];
    bucket.todos.push(t);
  }
  return buckets.filter((b) => b.todos.length > 0);
}

function groupBySnoozeUntil(todos: readonly Todo[], now: number): Bucket[] {
  const tomorrow = daysFrom(startOfDay(now), 1);
  const dayAfter = daysFrom(tomorrow, 1);
  const weekOut = daysFrom(tomorrow, 6);
  const buckets = ["Later today", "Tomorrow", "This week", "Later"].map(
    makeBucket,
  );
  for (const t of todos) {
    if (!t.snoozeUntil) continue;
    const at = isoToEpoch(t.snoozeUntil);
    if (at < tomorrow) buckets[0].todos.push(t);
    else if (at < dayAfter) buckets[1].todos.push(t);
    else if (at < weekOut) buckets[2].todos.push(t);
    else buckets[3].todos.push(t);
  }
  return buckets.filter((b) => b.todos.length > 0);
}

/** Buckets an already-sorted list, preserving order within each bucket. */
export function groupForFilter(
  sorted: readonly Todo[],
  filter: Filter,
  now: number,
): Bucket[] {
  switch (filter) {
    case "SNOOZED":
      return groupBySnoozeUntil(sorted, now);
    case "ACTIVE": {
      const pinned = sorted.filter((t) => t.pinned);
      const rest = sorted.filter((t) => !t.pinned);
      const buckets: Bucket[] = [];
      if (pinned.length > 0) {
        buckets.push({ ...makeBucket("Pinned"), todos: pinned, pinned: true });
      }
      buckets.push(...groupByPastKey(rest, activeKey, now));
      return buckets;
    }
    case "DONE":
    case "ALL":
      return groupByPastKey(sorted, ownKey, now);
  }
}

function makeBucket(label: string): Bucket {
  return { label, todos: [], pinned: false, floor: null, ceiling: null };
}
