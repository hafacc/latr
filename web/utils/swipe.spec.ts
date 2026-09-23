import { describe, expect, test } from "bun:test";
import {
  commitThreshold,
  committedSide,
  lockDirection,
  startsAtEdge,
  swipeAction,
} from "./swipe";

describe("lockDirection", () => {
  test("stays pending inside the slop", () => {
    expect(lockDirection(5, 3)).toBe("pending");
  });
  test("claims horizontal only when |dx| > 2|dy|", () => {
    expect(lockDirection(30, 10)).toBe("horizontal");
    expect(lockDirection(20, 10)).toBe("vertical");
  });
  test("a mostly vertical drag is a scroll", () => {
    expect(lockDirection(4, 30)).toBe("vertical");
  });
});

describe("commit", () => {
  test("uses 96px, or 35% of a narrow row", () => {
    expect(commitThreshold(400)).toBe(96);
    expect(commitThreshold(200)).toBe(70);
  });
  test("commits on distance, either way", () => {
    expect(committedSide(100, 400)).toBe("start");
    expect(committedSide(-100, 400)).toBe("end");
    expect(committedSide(90, 400)).toBeNull();
  });
});

test("edge starts are ignored so the browser's back swipe wins", () => {
  expect(startsAtEdge(10, 390)).toBe(true);
  expect(startsAtEdge(380, 390)).toBe(true);
  expect(startsAtEdge(200, 390)).toBe(false);
});

test("action mapping matches Android", () => {
  expect(swipeAction("start", false, false)).toBe("snooze");
  expect(swipeAction("start", false, true)).toBe("reactivate");
  expect(swipeAction("start", true, false)).toBe("reactivate");
  expect(swipeAction("end", false, false)).toBe("done");
  expect(swipeAction("end", false, true)).toBe("done");
  expect(swipeAction("end", true, false)).toBe("delete");
});
