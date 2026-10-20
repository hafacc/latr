/** A `#tag`, as UTF-16 offsets covering the `#` and the name. */
export type TagSpan = { start: number; end: number };

// Shared spec with Android's Tags.kt, pinned by testdata/tag-fixtures.json.
const NAME_CHAR = /[\p{L}\p{Nd}\p{M}_\-/]/u;
const LETTER = /\p{L}/u;
const WHITESPACE = /\s/u;
const OPENERS = new Set(["(", "[", "{", '"', "'", "*", "_", "“", "‘", "«"]);
const TRAILING = new Set(["_", "-", "/"]);

function startsTag(prev: string | undefined): boolean {
  return prev === undefined || WHITESPACE.test(prev) || OPENERS.has(prev);
}

/** Every `#tag` in `text`: a `#` at a word start followed by a name with at least one letter. */
export function parseTags(text: string): TagSpan[] {
  if (!text.includes("#")) return [];
  const tags: TagSpan[] = [];
  let i = 0;
  while (i < text.length) {
    if (text[i] !== "#" || !startsTag(text[i - 1])) {
      i++;
      continue;
    }
    let end = i + 1;
    let lastKept = i + 1;
    let hasLetter = false;
    while (end < text.length) {
      const char = String.fromCodePoint(text.codePointAt(end) as number);
      if (!NAME_CHAR.test(char)) break;
      end += char.length;
      if (!TRAILING.has(char)) {
        lastKept = end;
        if (LETTER.test(char)) hasLetter = true;
      }
    }
    if (hasLetter) tags.push({ start: i, end: lastKept });
    i = Math.max(end, i + 1);
  }
  return tags;
}
