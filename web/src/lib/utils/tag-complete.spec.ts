import { describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { completeTag, countTags, suggestTags, typingTag } from "./tags";

const fixtures = JSON.parse(
  readFileSync(
    new URL("../../../../testdata/tag-complete-fixtures.json", import.meta.url),
    "utf8",
  ),
) as {
  typing: {
    name: string;
    in: string;
    caret: number;
    out: [number, string] | null;
  }[];
  suggest: {
    name: string;
    texts: string[];
    own: string;
    prefix: string;
    limit?: number;
    out: string[];
  }[];
  complete: {
    name: string;
    in: string;
    caret: number;
    pick: string;
    out: string;
    outCaret: number;
  }[];
};

describe("shared tag completion fixtures (must match Android)", () => {
  for (const c of fixtures.typing) {
    test(`typing: ${c.name}`, () => {
      const typed = typingTag(c.in, c.caret);
      expect(typed && [typed.start, typed.prefix]).toEqual(c.out);
    });
  }
  for (const c of fixtures.suggest) {
    test(`suggest: ${c.name}`, () => {
      expect(suggestTags(countTags(c.texts), c.own, c.prefix, c.limit)).toEqual(
        c.out,
      );
    });
  }
  for (const c of fixtures.complete) {
    test(`complete: ${c.name}`, () => {
      const typed = typingTag(c.in, c.caret);
      expect(typed).not.toBeNull();
      const done = completeTag(
        c.in,
        typed as NonNullable<typeof typed>,
        c.caret,
        c.pick,
      );
      expect([done.text, done.caret]).toEqual([c.out, c.outCaret]);
    });
  }
});
