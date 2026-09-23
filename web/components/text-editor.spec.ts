import { describe, expect, test } from "bun:test";
import { EditorState } from "@codemirror/state";
import { inlineDecorations, singleLine } from "./text-editor";

function ranges(doc: string): [number, number, string][] {
  const out: [number, number, string][] = [];
  inlineDecorations(doc).between(0, doc.length, (from, to, deco) => {
    out.push([from, to, deco.spec.class]);
  });
  return out.sort(
    (a, b) => a[0] - b[0] || a[1] - b[1] || (a[2] < b[2] ? -1 : 1),
  );
}

describe("inlineDecorations", () => {
  test("marks a bold span and dims both markers", () => {
    expect(ranges("call *mom* now")).toEqual([
      [5, 6, "md-marker"],
      [5, 10, "md-bold"],
      [9, 10, "md-marker"],
    ]);
  });

  test("nests italic inside bold", () => {
    expect(ranges("*_x_*")).toEqual([
      [0, 1, "md-marker"],
      [0, 5, "md-bold"],
      [1, 2, "md-marker"],
      [1, 4, "md-italic"],
      [3, 4, "md-marker"],
      [4, 5, "md-marker"],
    ]);
  });

  test("leaves literal markers undecorated", () => {
    expect(ranges("snake_case_name and **x**")).toEqual([]);
  });
});

describe("singleLine", () => {
  const state = EditorState.create({ doc: "ab", extensions: [singleLine] });

  test("a typed newline becomes a space", () => {
    const next = state.update({ changes: { from: 1, insert: "\n" } }).state;
    expect(next.doc.toString()).toBe("a b");
  });

  test("several newlines in one insert all become spaces", () => {
    const next = state.update({
      changes: { from: 2, insert: "\nc\r\nd\n" },
    }).state;
    expect(next.doc.toString()).not.toContain("\n");
    expect(next.doc.lines).toBe(1);
  });
});
