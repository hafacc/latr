import type { Bucket } from "./group";
import {
  positionAbove,
  positionBelow,
  positionBetween,
  timeKey,
} from "./position";
import { type Filter, isoToEpoch, isSnoozed, listKey, type Todo } from "./todo";

/** Where a row is dropped: a bucket, and an index among that bucket's other rows. */
export type DropSlot = { bucket: number; index: number };

function sameSnooze(a: Todo, b: Todo | undefined): b is Todo {
  return (
    b !== undefined &&
    a.snoozeUntil !== null &&
    b.snoozeUntil !== null &&
    isoToEpoch(a.snoozeUntil) === isoToEpoch(b.snoozeUntil)
  );
}

/** The slot a row is in now, or null when it isn't in `buckets`. */
export function slotOf(
  buckets: readonly Bucket[],
  id: string,
): DropSlot | null {
  for (const [bucket, { todos }] of buckets.entries()) {
    const index = todos.findIndex((t) => t.id === id);
    if (index >= 0) return { bucket, index };
  }
  return null;
}

/**
 * The todo as dropping it at `slot` leaves it.
 *
 * Returns the same object when the slot is where the row already is, and null
 * when the row can't go there: an unpinned row among pinned ones (or the
 * reverse) in Active, or in Snoozed anywhere but among rows snoozed to the
 * same time. `buckets` is the list as shown, from [groupForFilter].
 */
export function planMove(
  todo: Todo,
  filter: Filter,
  buckets: readonly Bucket[],
  slot: DropSlot,
  now: number,
): Todo | null {
  const current = slotOf(buckets, todo.id);
  const bucket = buckets[slot.bucket];
  if (current === null || bucket === undefined) return null;
  if (current.bucket === slot.bucket && current.index === slot.index) {
    return todo;
  }
  const others = bucket.todos.filter((t) => t.id !== todo.id);
  const above: Todo | undefined = others[slot.index - 1];
  const below: Todo | undefined = others[slot.index];

  let upper: Todo | undefined = above;
  let lower: Todo | undefined = below;
  if (filter === "SNOOZED") {
    upper = sameSnooze(todo, above) ? above : undefined;
    lower = sameSnooze(todo, below) ? below : undefined;
  } else if (filter === "ACTIVE" && bucket.pinned !== todo.pinned) {
    return null;
  }
  // Snoozed orders rows snoozed to one time by their own place, as All does.
  const keyFilter = filter === "SNOOZED" ? "ALL" : filter;
  // Under a date heading, the top and the bottom go halfway to the heading's edge.
  const floor = filter === "SNOOZED" ? null : bucket.floor;
  let position: string | null;
  if (upper && lower) {
    position = positionBetween(
      listKey(lower, keyFilter),
      listKey(upper, keyFilter),
      todo.id,
    );
  } else if (lower) {
    const lo = listKey(lower, keyFilter);
    const edge = floor === null ? null : timeKey(bucket.ceiling ?? now);
    position =
      (edge === null ? null : positionBetween(lo, edge, todo.id)) ??
      positionAbove(lo, todo.id);
  } else if (upper) {
    const hi = listKey(upper, keyFilter);
    position =
      (floor === null ? null : positionBetween(timeKey(floor), hi, todo.id)) ??
      positionBelow(hi, todo.id);
  } else {
    return null;
  }
  if (position === null) return null;
  return {
    ...todo,
    position,
    modifiedAt: now,
    snoozeUntil: isSnoozed(todo, now) ? todo.snoozeUntil : null,
  };
}
