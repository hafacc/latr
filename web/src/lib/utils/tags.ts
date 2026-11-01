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

/** A `#tag` being typed: `start` is its `#`, the caret ends the name typed so far. */
export type TypedTag = { start: number; prefix: string };

/** The tag the caret sits at the end of, or null; the name may still be empty. */
export function typingTag(text: string, caret: number): TypedTag | null {
  const start = text.lastIndexOf("#", caret - 1);
  if (start < 0 || !startsTag(text[start - 1])) return null;
  const prefix = text.slice(start + 1, caret);
  for (const char of prefix) {
    if (!NAME_CHAR.test(char)) return null;
  }
  const next = text.codePointAt(caret);
  if (next !== undefined && NAME_CHAR.test(String.fromCodePoint(next))) {
    return null;
  }
  return { start, prefix };
}

/** Tag names (without `#`) by lowercased name: the first spelling seen and how many texts use it. */
export type TagCounts = Map<string, { name: string; count: number }>;

/** How many of `texts` use each tag; a text counts once per tag. */
export function countTags(texts: Iterable<string>): TagCounts {
  const counts: TagCounts = new Map();
  for (const text of texts) {
    for (const name of tagNames(text)) {
      const key = name.toLowerCase();
      const entry = counts.get(key);
      if (entry) entry.count++;
      else counts.set(key, { name, count: 1 });
    }
  }
  return counts;
}

function tagNames(text: string): Set<string> {
  const seen = new Map<string, string>();
  for (const tag of parseTags(text)) {
    const name = text.slice(tag.start + 1, tag.end);
    const key = name.toLowerCase();
    if (!seen.has(key)) seen.set(key, name);
  }
  return new Set(seen.values());
}

/**
 * Known tags starting with `prefix` (ignoring case), most used first, then by name.
 *
 * `ownText` is the saved text of the todo being edited: its tags are left out, being
 * there already (and so is the half-typed one it holds). A tag spelled exactly as typed
 * is left out too.
 */
export function suggestTags(
  counts: TagCounts,
  ownText: string,
  prefix: string,
  limit = 5,
): string[] {
  const own = new Set([...tagNames(ownText)].map((name) => name.toLowerCase()));
  const wanted = prefix.toLowerCase();
  const matches: { name: string; key: string; count: number }[] = [];
  for (const [key, { name, count }] of counts) {
    if (!own.has(key) && key.startsWith(wanted) && key !== wanted) {
      matches.push({ name, key, count });
    }
  }
  matches.sort(
    (left, right) =>
      right.count - left.count ||
      (left.key < right.key ? -1 : left.key > right.key ? 1 : 0),
  );
  return matches.slice(0, limit).map((match) => match.name);
}

/** `text` with the typed tag replaced by `#name` and a space, caret after the space. */
export function completeTag(
  text: string,
  typed: TypedTag,
  caret: number,
  name: string,
): { text: string; caret: number } {
  const before = text.slice(0, typed.start);
  const after = text.slice(caret);
  const tag = `#${name}`;
  if (after.startsWith(" ")) {
    return {
      text: before + tag + after,
      caret: before.length + tag.length + 1,
    };
  } else {
    return {
      text: `${before}${tag} ${after}`,
      caret: before.length + tag.length + 1,
    };
  }
}
