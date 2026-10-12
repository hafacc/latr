import { describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import {
  between,
  idKey,
  keyTime,
  positionAbove,
  positionBelow,
  positionBetween,
  readPosition,
  timeKey,
} from "./position";

const fixtures = JSON.parse(
  readFileSync(
    new URL("../../../../testdata/position-fixtures.json", import.meta.url),
    "utf8",
  ),
) as {
  timeKey: { epoch: number; key: string }[];
  idKey: { id: string; key: string }[];
  readPosition: { in: unknown; out: string | null }[];
  between: { lo: string; hi: string; out: string | null }[];
  positionBetween: { lo: string; hi: string; id: string; out: string | null }[];
  positionAbove: { lo: string; id: string; out: string | null }[];
  positionBelow: { hi: string; id: string; out: string | null }[];
};

describe("shared position fixtures (must match Android)", () => {
  test("timeKey", () => {
    for (const c of fixtures.timeKey) expect(timeKey(c.epoch)).toBe(c.key);
  });
  test("idKey", () => {
    for (const c of fixtures.idKey) expect(idKey(c.id)).toBe(c.key);
  });
  test("readPosition", () => {
    for (const c of fixtures.readPosition) {
      expect(readPosition(c.in)).toBe(c.out);
    }
  });
  test("between", () => {
    for (const c of fixtures.between) expect(between(c.lo, c.hi)).toBe(c.out);
  });
  test("positionBetween", () => {
    for (const c of fixtures.positionBetween) {
      expect(positionBetween(c.lo, c.hi, c.id)).toBe(c.out);
    }
  });
  test("positionAbove", () => {
    for (const c of fixtures.positionAbove) {
      expect(positionAbove(c.lo, c.id)).toBe(c.out);
    }
  });
  test("positionBelow", () => {
    for (const c of fixtures.positionBelow) {
      expect(positionBelow(c.hi, c.id)).toBe(c.out);
    }
  });
});

describe("positionBetween", () => {
  const lowest = timeKey(1_700_000_000_000) + idKey("a");
  const highest = timeKey(1_700_000_000_000) + idKey("b");

  // Seeded so a failure repeats.
  function seeded(seed: number): () => number {
    let state = seed;
    return () => {
      state = (state * 1_103_515_245 + 12_345) % 2_147_483_648;
      return state / 2_147_483_648;
    };
  }

  test("is the time halfway between two times", () => {
    const made = positionBetween(
      timeKey(1_000),
      timeKey(2_000) + idKey("x"),
      "a",
    );
    expect(made).toBe(timeKey(1_500) + idKey("a"));
  });

  test("always finds room, however often one gap is split", () => {
    const random = seeded(7);
    const keys = [lowest, highest];
    for (let move = 0; move < 2_000; move++) {
      const gap = Math.floor(random() * (keys.length - 1));
      // Mostly the same two gaps, which is what grows a key fastest.
      const index = random() < 0.8 ? Math.min(gap, 1) : gap;
      const made = positionBetween(keys[index], keys[index + 1], `id${move}`);
      if (made === null) throw new Error(`no room at move ${move}`);
      expect(keys[index] < made).toBe(true);
      expect(made < keys[index + 1]).toBe(true);
      expect(readPosition(made)).toBe(made);
      keys.splice(index + 1, 0, made);
    }
  });

  test("a row moved to the same gap again and again stays short", () => {
    let top = highest;
    for (let move = 0; move < 100; move++) {
      top = positionBetween(lowest, top, "a") ?? "";
    }
    expect(top.length).toBeLessThan(80);
  });

  test("rows moved to the top or bottom again and again keep their time", () => {
    let top = lowest;
    let bottom = lowest;
    for (let move = 0; move < 200; move++) {
      const above = positionAbove(top, `up${move}`);
      const below = positionBelow(bottom, `down${move}`);
      if (above === null || below === null) throw new Error(`move ${move}`);
      expect(top < above).toBe(true);
      expect(below < bottom).toBe(true);
      top = above;
      bottom = below;
    }
    expect(keyTime(top)).toBe(1_700_000_000_000);
    expect(keyTime(bottom)).toBe(1_700_000_000_000);
    expect(Math.max(top.length, bottom.length)).toBeLessThan(120);
  });
});
