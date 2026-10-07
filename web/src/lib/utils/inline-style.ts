export type InlineStyle = "bold" | "italic";

// `start`/`end` are UTF-16 offsets covering both markers: text[start] and text[end - 1].
export type StyleSpan = { style: InlineStyle; start: number; end: number };

// Shared spec with Android's InlineStyle.kt: letters, decimal digits and combining marks count as word characters.
const LETTER_OR_DIGIT = /[\p{L}\p{Nd}\p{M}]/u;
// JS `\s`, which is exactly U+0009-000D, 0020, 0085, 00A0, 1680, 2000-200A, 2028, 2029, 202F, 205F, 3000, FEFF (Android matches).
const WHITESPACE = /\s/u;

function styleOf(marker: string): InlineStyle {
  return marker === "*" ? "bold" : "italic";
}

function codePointBefore(text: string, i: number): string | null {
  if (i <= 0) return null;
  const low = text.charCodeAt(i - 1);
  if (low >= 0xdc00 && low <= 0xdfff && i >= 2) {
    const high = text.charCodeAt(i - 2);
    if (high >= 0xd800 && high <= 0xdbff) return text.slice(i - 2, i);
  }
  return text[i - 1];
}

function codePointAfter(text: string, i: number): string | null {
  if (i + 1 >= text.length) return null;
  return String.fromCodePoint(text.codePointAt(i + 1) as number);
}

/** `*bold*` and `_italic_` spans; markers stay in the text, so offsets are the text's own. */
export function parseInlineStyles(text: string): StyleSpan[] {
  if (!text.includes("*") && !text.includes("_")) return [];
  const spans: StyleSpan[] = [];
  const stack: number[] = [];
  // Per-marker indices into `stack`; an entry is live only while `stack[idx]` still holds its position.
  const open: Record<string, { idx: number; pos: number }[]> = {
    "*": [],
    _: [],
  };
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (c === "\n") {
      stack.length = 0;
      open["*"].length = 0;
      open._.length = 0;
      continue;
    }
    if (c !== "*" && c !== "_") continue;
    const prev = codePointBefore(text, i);
    const next = codePointAfter(text, i);
    if (prev === c || next === c) continue;
    const canClose =
      prev !== null &&
      !WHITESPACE.test(prev) &&
      (next === null || !LETTER_OR_DIGIT.test(next));
    const canOpen =
      next !== null &&
      !WHITESPACE.test(next) &&
      (prev === null || !LETTER_OR_DIGIT.test(prev));
    let opener: { idx: number; pos: number } | null = null;
    if (canClose) {
      const list = open[c];
      while (list.length > 0) {
        const top = list[list.length - 1];
        if (stack[top.idx] === top.pos) {
          opener = top;
          break;
        }
        list.pop();
      }
    }
    if (opener) {
      spans.push({ style: styleOf(c), start: opener.pos, end: i + 1 });
      open[c].pop();
      stack.length = opener.idx;
    } else if (canOpen) {
      open[c].push({ idx: stack.length, pos: i });
      stack.push(i);
    }
  }
  return spans.sort((a, b) => a.start - b.start || b.end - a.end);
}

/** The text with every span's markers removed, for search. */
export function plainText(text: string): string {
  const spans = parseInlineStyles(text);
  if (spans.length === 0) return text;
  const markers = new Set<number>();
  for (const s of spans) {
    markers.add(s.start);
    markers.add(s.end - 1);
  }
  let out = "";
  for (let i = 0; i < text.length; i++) if (!markers.has(i)) out += text[i];
  return out;
}

export type StyledNode =
  | { kind: "text"; text: string }
  | { kind: "marker"; text: string }
  | { kind: "span"; style: InlineStyle; children: StyledNode[] };

/** Nests the (properly nested) spans into a tree, with markers as their own leaves. */
export function styledTree(text: string): StyledNode[] {
  const spans = parseInlineStyles(text);
  let i = 0;
  function build(limit: number, from: number): StyledNode[] {
    const nodes: StyledNode[] = [];
    let pos = from;
    while (pos < limit) {
      const span = i < spans.length ? spans[i] : null;
      if (span && span.start < limit) {
        if (span.start > pos)
          nodes.push({ kind: "text", text: text.slice(pos, span.start) });
        i++;
        const inner = build(span.end - 1, span.start + 1);
        nodes.push({
          kind: "span",
          style: span.style,
          children: [
            { kind: "marker", text: text[span.start] },
            ...inner,
            { kind: "marker", text: text[span.end - 1] },
          ],
        });
        pos = span.end;
      } else {
        nodes.push({ kind: "text", text: text.slice(pos, limit) });
        pos = limit;
      }
    }
    return nodes;
  }
  return build(text.length, 0);
}

/**
 * What ⌘B/⌘I replaces [from, to) with, and the selection to restore afterwards. Toggles: a selection
 * already wrapped in `marker` (just outside it, or as its own first/last characters) is unwrapped.
 */
export function wrapSelection(
  value: string,
  start: number,
  end: number,
  marker: "*" | "_",
): {
  from: number;
  to: number;
  insert: string;
  selStart: number;
  selEnd: number;
} {
  while (start < end && WHITESPACE.test(value[start])) start++;
  while (end > start && WHITESPACE.test(value[end - 1])) end--;
  const inner = value.slice(start, end);
  if (value[start - 1] === marker && value[end] === marker) {
    return {
      from: start - 1,
      to: end + 1,
      insert: inner,
      selStart: start - 1,
      selEnd: end - 1,
    };
  }
  if (
    inner.length >= 2 &&
    inner[0] === marker &&
    inner[inner.length - 1] === marker
  ) {
    return {
      from: start,
      to: end,
      insert: inner.slice(1, -1),
      selStart: start,
      selEnd: end - 2,
    };
  }
  return {
    from: start,
    to: end,
    insert: `${marker}${inner}${marker}`,
    selStart: start + 1,
    selEnd: end + 1,
  };
}
