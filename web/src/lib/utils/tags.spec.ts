import { describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { parseTags } from "./tags";

const fixtures = JSON.parse(
  readFileSync(
    new URL("../../../../testdata/tag-fixtures.json", import.meta.url),
    "utf8",
  ),
) as { cases: { name: string; in: string; tags: [number, number][] }[] };

describe("shared tag fixtures (must match Android)", () => {
  for (const c of fixtures.cases) {
    test(c.name, () => {
      expect(parseTags(c.in).map((t) => [t.start, t.end])).toEqual(c.tags);
    });
  }
});
