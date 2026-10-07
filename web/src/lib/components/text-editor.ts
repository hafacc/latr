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

export type TextEditorHandle = {
  focus: (caret?: number | "end") => void;
  blur: () => void;
  // Document offset nearest a viewport point.
  posAtCoords: (x: number, y: number) => number | null;
  select: (anchor: number, head: number) => void;
};

const BOLD = Decoration.mark({ class: "md-bold" });
const ITALIC = Decoration.mark({ class: "md-italic" });
const MARKER = Decoration.mark({ class: "md-marker" });

export function inlineDecorations(doc: string): DecorationSet {
  const ranges = [];
  for (const span of parseInlineStyles(doc)) {
    ranges.push(
      (span.style === "bold" ? BOLD : ITALIC).range(span.start, span.end),
    );
    ranges.push(MARKER.range(span.start, span.start + 1));
    ranges.push(MARKER.range(span.end - 1, span.end));
  }
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
