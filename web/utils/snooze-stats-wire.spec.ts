import { describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { fromWire, toWire } from "./snooze-stats-wire";
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
    expect(Object.keys(wire).sort()).toEqual(["lastCustom", "sets", "tod"]);
    expect(wire.lastCustom).toBeNull();
  });

  test("garbage input parses to an empty partition", () => {
    expect(fromWire("device-a", null)).toEqual(emptyPartition("device-a"));
    expect(fromWire("device-a", "x")).toEqual(emptyPartition("device-a"));
  });
});
