import {
  autocompletion,
  type Completion,
  type CompletionContext,
  pickedCompletion,
} from "@codemirror/autocomplete";
import {
  EditorSelection,
  EditorState,
  type Extension,
  StateField,
} from "@codemirror/state";
import {
  type Command,
  Decoration,
  type DecorationSet,
  EditorView,
} from "@codemirror/view";
import { parseInlineStyles, wrapSelection } from "../utils/inline-style";
import { completeTag, parseTags, typingTag } from "../utils/tags";

export type TextEditorHandle = {
  focus: (caret?: number | "end") => void;
  blur: () => void;
  // Document offset nearest a viewport point.
  posAtCoords: (x: number, y: number) => number | null;
  select: (anchor: number, head: number) => void;
  // Focus with the caret on the first or last line, nearest viewport x, keeping x for later up/down moves.
  focusAtEdge: (x: number, edge: "first" | "last") => void;
};

const BOLD = Decoration.mark({ class: "md-bold" });
const ITALIC = Decoration.mark({ class: "md-italic" });
const MARKER = Decoration.mark({ class: "md-marker" });
const TAG = Decoration.mark({ class: "md-tag" });

export function inlineDecorations(doc: string): DecorationSet {
  const ranges = [];
  for (const span of parseInlineStyles(doc)) {
    ranges.push(
      (span.style === "bold" ? BOLD : ITALIC).range(span.start, span.end),
    );
    ranges.push(MARKER.range(span.start, span.start + 1));
    ranges.push(MARKER.range(span.end - 1, span.end));
  }
  for (const tag of parseTags(doc)) ranges.push(TAG.range(tag.start, tag.end));
  return Decoration.set(ranges, true);
}

export const inlineStyles = StateField.define<DecorationSet>({
  create: (state) => inlineDecorations(state.doc.toString()),
  update: (deco, tr) =>
    tr.docChanged ? inlineDecorations(tr.state.doc.toString()) : deco,
  provide: (field) => EditorView.decorations.from(field),
});

export function toggleMarker(marker: "*" | "_"): Command {
  return (view) => {
    const { from, to } = view.state.selection.main;
    const edit = wrapSelection(view.state.doc.toString(), from, to, marker);
    view.dispatch({
      changes: { from: edit.from, to: edit.to, insert: edit.insert },
      selection: EditorSelection.range(edit.selStart, edit.selEnd),
      userEvent: "input",
    });
    return true;
  };
}

/** Known tags to offer while a `#tag` is typed, and how to pick one. */
export type TagOffer = { names: string[]; pick: (name: string) => void };

/** Replace the typed tag in `from`..`to` with `#name` and a space. */
function insertTag(
  view: EditorView,
  from: number,
  to: number,
  name: string,
  completion?: Completion,
): void {
  const doc = view.state.doc.toString();
  const done = completeTag(doc, { start: from, prefix: "" }, to, name);
  view.dispatch({
    changes: {
      from,
      to,
      insert: done.text.slice(from, done.text.length - (doc.length - to)),
    },
    selection: EditorSelection.cursor(done.caret),
    annotations: completion ? pickedCompletion.of(completion) : [],
    userEvent: "input.complete",
  });
}

/** What to offer for the tag typed at the caret, or null when nothing is. */
export function tagOfferAt(
  view: EditorView,
  suggest: (prefix: string) => string[],
): TagOffer | null {
  const { empty, head } = view.state.selection.main;
  if (!empty) return null;
  const typed = typingTag(view.state.doc.toString(), head);
  if (!typed) return null;
  const names = suggest(typed.prefix);
  if (names.length === 0) return null;
  return {
    names,
    pick: (name) => {
      const caret = view.state.selection.main.head;
      const current = typingTag(view.state.doc.toString(), caret);
      if (current) insertTag(view, current.start, caret, name);
    },
  };
}

/** A dropdown of known tags while a `#tag` is typed; picking one finishes it and adds a space. */
export function tagCompletion(
  suggest: (prefix: string) => string[],
): Extension {
  return autocompletion({
    override: [
      (context: CompletionContext) => {
        const typed = typingTag(context.state.doc.toString(), context.pos);
        if (!typed) return null;
        const names = suggest(typed.prefix);
        if (names.length === 0) return null;
        return {
          from: typed.start,
          filter: false,
          options: names.map(
            (name): Completion => ({
              label: `#${name}`,
              apply: (view, completion, from, to) =>
                insertTag(view, from, to, name, completion),
            }),
          ),
        };
      },
    ],
    icons: false,
  });
}

export const theme = EditorView.theme({
  "&": { backgroundColor: "transparent" },
  "&.cm-focused": { outline: "none" },
  ".cm-scroller": {
    fontFamily: "inherit",
    lineHeight: "inherit",
    overflow: "visible",
  },
  ".cm-content": { padding: "0", caretColor: "var(--color-text)" },
  ".cm-line": { padding: "0" },
  ".cm-placeholder": { color: "var(--color-text-secondary)" },
  ".cm-tooltip.cm-tooltip-autocomplete": {
    backgroundColor: "var(--color-surface)",
    border: "1px solid var(--color-border)",
    borderRadius: "10px",
    boxShadow: "var(--shadow-pop)",
    padding: "4px",
    fontFamily: "inherit",
  },
  ".cm-tooltip.cm-tooltip-autocomplete > ul": {
    fontFamily: "inherit",
    maxHeight: "15em",
    minWidth: "8em",
  },
  ".cm-tooltip.cm-tooltip-autocomplete > ul > li": {
    padding: "4px 10px",
    borderRadius: "6px",
    color: "var(--color-accent)",
    fontSize: "14px",
    lineHeight: "22px",
  },
  ".cm-tooltip.cm-tooltip-autocomplete > ul > li[aria-selected]": {
    backgroundColor: "var(--color-surface-hover)",
    color: "var(--color-accent)",
  },
});

export const singleLine: Extension = [
  EditorView.clipboardInputFilter.of((text) => text.replace(/\r?\n/g, " ")),
  EditorState.transactionFilter.of((tr) => {
    if (!tr.docChanged || !tr.newDoc.toString().includes("\n")) return tr;
    const text = tr.newDoc.toString();
    const changes = [];
    for (let i = text.indexOf("\n"); i >= 0; i = text.indexOf("\n", i + 1)) {
      changes.push({ from: i, to: i + 1, insert: " " });
    }
    return [tr, { changes, sequential: true }];
  }),
];
