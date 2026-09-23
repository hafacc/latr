import { describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import {
  type InlineStyle,
  parseInlineStyles,
  plainText,
  type StyledNode,
  styledTree,
  wrapSelection,
} from "./inline-style";

const fixtures = JSON.parse(
  readFileSync(
    new URL("../../testdata/inline-style-fixtures.json", import.meta.url),
    "utf8",
  ),
) as {
  cases: {
    name: string;
    in: string;
    spans: [InlineStyle, number, number][];
    plain: string;
  }[];
};

function flatten(nodes: StyledNode[]): string {
  return nodes
    .map((n) => (n.kind === "span" ? flatten(n.children) : n.text))
    .join("");
}

describe("shared inline-style fixtures (must match Android)", () => {
  for (const c of fixtures.cases) {
    test(c.name, () => {
      expect(
        parseInlineStyles(c.in).map((s) => [s.style, s.start, s.end]),
      ).toEqual(c.spans);
      expect(plainText(c.in)).toBe(c.plain);
      expect(flatten(styledTree(c.in))).toBe(c.in);
    });
  }
});

describe("styledTree", () => {
  test("nests italic inside bold with markers as leaves", () => {
    expect(styledTree("a *b _c_* d")).toEqual([
      { kind: "text", text: "a " },
      {
        kind: "span",
        style: "bold",
        children: [
          { kind: "marker", text: "*" },
          { kind: "text", text: "b " },
          {
            kind: "span",
            style: "italic",
            children: [
              { kind: "marker", text: "_" },
              { kind: "text", text: "c" },
              { kind: "marker", text: "_" },
            ],
          },
          { kind: "marker", text: "*" },
        ],
      },
      { kind: "text", text: " d" },
    ]);
  });
});

describe("wrapSelection", () => {
  test("wraps the selection and keeps it selected inside the markers", () => {
    expect(wrapSelection("buy milk", 4, 8, "*")).toEqual({
      from: 4,
      to: 8,
      insert: "*milk*",
      selStart: 5,
      selEnd: 9,
    });
  });

  test("with no selection inserts a pair and puts the caret between", () => {
    expect(wrapSelection("buy ", 4, 4, "_")).toEqual({
      from: 4,
      to: 4,
      insert: "__",
      selStart: 5,
      selEnd: 5,
    });
  });

  test("trims whitespace off the selection before wrapping", () => {
    expect(wrapSelection("buy milk now", 4, 9, "*")).toEqual({
      from: 4,
      to: 8,
      insert: "*milk*",
      selStart: 5,
      selEnd: 9,
    });
  });

  test("unwraps a selection whose markers sit just outside it", () => {
    expect(wrapSelection("buy *milk*", 5, 9, "*")).toEqual({
      from: 4,
      to: 10,
      insert: "milk",
      selStart: 4,
      selEnd: 8,
    });
  });

  test("unwraps a selection that includes its own markers", () => {
    expect(wrapSelection("buy _milk_", 4, 10, "_")).toEqual({
      from: 4,
      to: 10,
      insert: "milk",
      selStart: 4,
      selEnd: 8,
    });
  });

  test("a different marker wraps rather than unwraps", () => {
    expect(wrapSelection("buy *milk*", 5, 9, "_").insert).toBe("_milk_");
  });
});

describe("parseInlineStyles performance", () => {
  test("an adversarial 120KB line parses in linear time", () => {
    const text = "*a ".repeat(20_000) + "b_ ".repeat(20_000);
    const t0 = performance.now();
    parseInlineStyles(text);
    expect(performance.now() - t0).toBeLessThan(200);
  });
});
