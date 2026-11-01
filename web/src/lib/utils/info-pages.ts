import privacy from "../../../../content/privacy.json";

/** Web address of the project's issue tracker, where feedback goes. */
export const FEEDBACK_URL = "https://github.com/hafacc/latr/issues";

/** A run of page text, linked to a site path when `href` is set. */
export interface TextPart {
  text: string;
  href?: string;
}

/** One headed section of an info page. */
export interface InfoSection {
  heading: string;
  body: string;
}

/** An info page's text, shared with the Android app through `content/`. */
export interface InfoPage {
  title: string;
  sections: InfoSection[];
  closing: string;
}

/** The privacy page. */
export const PRIVACY: InfoPage = privacy;

const LINK = /\[([^\]]+)\]\(([^)]+)\)/g;

/** Split `[text](path)` links out of a page's text. */
export function textParts(text: string): TextPart[] {
  const parts: TextPart[] = [];
  let end = 0;
  for (const match of text.matchAll(LINK)) {
    if (match.index > end) parts.push({ text: text.slice(end, match.index) });
    parts.push({ text: match[1], href: match[2] });
    end = match.index + match[0].length;
  }
  if (end < text.length) parts.push({ text: text.slice(end) });
  return parts;
}
