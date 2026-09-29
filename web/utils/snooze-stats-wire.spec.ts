import { describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { fromWire, globalPickCounts, toWire } from "./snooze-stats-wire";
import { emptyPartition } from "./snooze-suggest";

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
