import { describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import {
  foldBlocks,
  fromWire,
  globalPickCounts,
  sharedFromWire,
  toWire,
  voteDeltas,
} from "./snooze-stats-wire";
import { commit, emptyPartition, rankDeep } from "./snooze-suggest";

const fixtures = JSON.parse(
  readFileSync(
    new URL("../../testdata/snooze-fixtures.json", import.meta.url),
    "utf8",
  ),
) as { wire: { name: string; raw: unknown; normalized: unknown }[] };

describe("snooze stats wire format (must match Android)", () => {
  for (const c of fixtures.wire) {
    test(c.name, () => {
      const parsed = fromWire("device-a", c.raw);
      expect(toWire(parsed)).toEqual(c.normalized as never);
      expect(toWire(fromWire("device-a", toWire(parsed)))).toEqual(
        c.normalized as never,
      );
    });
  }

  test("the device id is the path, not a field", () => {
    const wire = toWire(emptyPartition("device-a"));
    expect(Object.keys(wire).sort()).toEqual([
      "lastCustom",
      "picks",
      "sets",
      "tod",
      "v",
    ]);
    expect(wire.lastCustom).toBeNull();
  });

  test("garbage input parses to an empty partition", () => {
    expect(fromWire("device-a", null)).toEqual(emptyPartition("device-a"));
    expect(fromWire("device-a", "x")).toEqual(emptyPartition("device-a"));
  });
});

describe("globalPickCounts", () => {
  test("none first, then ranks 1..20, missing as 0", () => {
    const counts = globalPickCounts({ none: 4, "1": 7, "20": 2, key: "1" });
    expect(counts).toHaveLength(21);
    expect(counts[0]).toBe(4);
    expect(counts[1]).toBe(7);
    expect(counts[2]).toBe(0);
    expect(counts[20]).toBe(2);
  });

  test("a missing doc is all zeros", () => {
    expect(globalPickCounts(undefined)).toEqual(Array(21).fill(0));
  });
});

describe("shared counts", () => {
  const at = new Date(2026, 8, 22, 10, 0).getTime();
  const day = 24 * 60 * 60 * 1000;
  let block = emptyPartition("d");
  block = commit(block, at, at + day, "custom").next;
  block = commit(block, at + day, at + 3 * day, "custom").next;
  block = commit(block, at + 2 * day, at + 40 * day, "custom").next;

  test("counts saved scaled rank the same as the block they came from", () => {
    const shared = sharedFromWire(voteDeltas(emptyPartition("d"), block));
    const now = at + 30 * day;
    const strip = (rows: ReturnType<typeof rankDeep>) =>
      rows.map((row) => [row.keyId, row.time, row.score.toFixed(9)]);
    expect(strip(rankDeep([shared], now))).toEqual(
      strip(rankDeep([block], now)),
    );
  });

  test("counts at or below zero and malformed ids are dropped", () => {
    const shared = sharedFromWire({
      sets: { "D0@0900": 2, "D0@1000": 0, "D0@1100": -1, bogus: 3 },
      tod: { "0900": 1, "0901": 1 },
    });
    expect(Object.keys(shared.sets)).toEqual(["D0@0900"]);
    expect(Object.keys(shared.tod)).toEqual(["0900"]);
  });

  test("folding sums the blocks not yet folded", () => {
    const wire = toWire({ ...block, picks: { "1": 2 } });
    const one = foldBlocks({ a: wire }, {});
    const fold = foldBlocks({ a: wire, b: wire, c: wire }, { c: true });
    expect(fold.ids).toEqual(["a", "b"]);
    expect(fold.picks).toEqual({ "1": 4 });
    expect(fold.lastCustom).toEqual(block.lastCustom);
    for (const [id, amount] of Object.entries(one.deltas.sets)) {
      expect(fold.deltas.sets[id]).toBeCloseTo(2 * amount, 9);
    }
  });
});
