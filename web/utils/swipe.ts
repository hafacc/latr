export const SWIPE_LOCK_SLOP_PX = 10;
export const SWIPE_COMMIT_PX = 96;
export const SWIPE_COMMIT_FRACTION = 0.35;
export const SWIPE_EDGE_PX = 24;

export type SwipeLock = "pending" | "horizontal" | "vertical";

/** Decides whether a drag belongs to the row (horizontal) or the page scroll (vertical). */
export function lockDirection(dx: number, dy: number): SwipeLock {
  const ax = Math.abs(dx);
  const ay = Math.abs(dy);
  if (ax < SWIPE_LOCK_SLOP_PX && ay < SWIPE_LOCK_SLOP_PX) return "pending";
  if (ax > 2 * ay) return "horizontal";
  else if (ay >= SWIPE_LOCK_SLOP_PX) return "vertical";
  else return "pending";
}

export function startsAtEdge(x: number, viewportWidth: number): boolean {
  return x < SWIPE_EDGE_PX || x > viewportWidth - SWIPE_EDGE_PX;
}

export function commitThreshold(rowWidth: number): number {
  return Math.min(SWIPE_COMMIT_PX, rowWidth * SWIPE_COMMIT_FRACTION);
}

export type SwipeSide = "start" | "end";

/** Which side commits at release; velocity is deliberately ignored. */
export function committedSide(dx: number, rowWidth: number): SwipeSide | null {
  const threshold = commitThreshold(rowWidth);
  if (dx >= threshold) return "start";
  else if (dx <= -threshold) return "end";
  else return null;
}

export type SwipeAction = "snooze" | "reactivate" | "done" | "delete";

/** Android's mapping: start-to-end snoozes (or reactivates), end-to-start completes (or deletes a done row). */
export function swipeAction(
  side: SwipeSide,
  isDone: boolean,
  isSnoozed: boolean,
): SwipeAction {
  if (side === "start") {
    return isDone || isSnoozed ? "reactivate" : "snooze";
  } else {
    return isDone ? "delete" : "done";
  }
}
