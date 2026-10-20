import { describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import {
  anchorRepeat,
  nextOccurrence,
  type Repeat,
  type RepeatDraft,
  readRepeat,
  repeatText,
} from "./repeat";

const fixtures = JSON.parse(
  readFileSync(
    new URL("../../../../testdata/repeat-fixtures.json", import.meta.url),
    "utf8",
  ),
) as {
  next: { name: string; repeat: Repeat; after: string; next: string }[];
  anchor: { name: string; repeat: RepeatDraft; now: string; anchor: string }[];
  text: { repeat: Repeat; text: string }[];
  read: { name: string; in: unknown; out: Repeat | null }[];
};

describe("shared repeat fixtures (must match Android)", () => {
  for (const c of fixtures.next) {
    test(`next: ${c.name}`, () => {
      expect(nextOccurrence(c.repeat, c.after)).toBe(c.next);
    });
  }
  for (const c of fixtures.anchor) {
    test(`anchor: ${c.name}`, () => {
      expect(anchorRepeat(c.repeat, c.now).anchor).toBe(c.anchor);
    });
  }
  for (const c of fixtures.text) {
    test(`text: ${c.text}`, () => {
      expect(repeatText(readRepeat(c.repeat) as Repeat)).toBe(c.text);
    });
  }
  for (const c of fixtures.read) {
    test(`read: ${c.name}`, () => {
      expect(readRepeat(c.in)).toEqual(c.out);
    });
  }
});
