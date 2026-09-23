"use client";

import {
  history,
  historyKeymap,
  insertNewline,
  standardKeymap,
} from "@codemirror/commands";
import {
  Annotation,
  Compartment,
  EditorSelection,
  EditorState,
  type Extension,
  Prec,
  StateField,
  Transaction,
} from "@codemirror/state";
import {
  type Command,
  Decoration,
  type DecorationSet,
  EditorView,
  keymap,
  placeholder as placeholderExt,
} from "@codemirror/view";
import {
  type ReactElement,
  type RefObject,
  useEffect,
  useImperativeHandle,
  useLayoutEffect,
  useRef,
} from "react";
import { parseInlineStyles, wrapSelection } from "../utils/inline-style";

export type TextEditorHandle = {
  focus: (caret?: number | "end") => void;
  blur: () => void;
  // Document offset nearest a viewport point.
  posAtCoords: (x: number, y: number) => number | null;
  select: (anchor: number, head: number) => void;
};

const external = Annotation.define<boolean>();

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

const inlineStyles = StateField.define<DecorationSet>({
  create: (state) => inlineDecorations(state.doc.toString()),
  update: (deco, tr) =>
    tr.docChanged ? inlineDecorations(tr.state.doc.toString()) : deco,
  provide: (field) => EditorView.decorations.from(field),
});

function toggleMarker(marker: "*" | "_"): Command {
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

const theme = EditorView.theme({
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

/** A plain-text CodeMirror editor that renders `*bold*` / `_italic_` live. */
export default function TextEditor({
  handleRef,
  value,
  onChange,
  onFocus,
  onBlur,
  onEnter,
  multiline = false,
  placeholder,
  enterKeyHint,
  ariaLabel,
  className,
}: {
  handleRef: RefObject<TextEditorHandle | null>;
  value: string;
  onChange: (value: string) => void;
  onFocus?: () => void;
  onBlur?: () => void;
  // Plain Enter; Shift+Enter inserts a newline when `multiline`.
  onEnter: (view: EditorView) => void;
  multiline?: boolean;
  placeholder?: string;
  enterKeyHint?: string;
  ariaLabel: string;
  className?: string;
}): ReactElement {
  const hostRef = useRef<HTMLDivElement>(null);
  const viewRef = useRef<EditorView | null>(null);
  const restoreRef = useRef<number | null>(null);
  const attrs = useRef(new Compartment());
  const callbacks = useRef({ onChange, onFocus, onBlur, onEnter });
  callbacks.current = { onChange, onFocus, onBlur, onEnter };

  const contentAttributes = (hint?: string) =>
    EditorView.contentAttributes.of({
      "aria-label": ariaLabel,
      "aria-multiline": multiline ? "true" : "false",
      spellcheck: "true",
      autocorrect: "on",
      autocapitalize: "sentences",
      writingsuggestions: "true",
      translate: "yes",
      ...(hint ? { enterkeyhint: hint } : {}),
    });

  // A layout effect so the view exists before a parent's layout effect focuses it.
  // biome-ignore lint/correctness/useExhaustiveDependencies: the view is created once; later changes go through the effects below
  useLayoutEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    const view = new EditorView({
      parent: host,
      state: EditorState.create({
        doc: value,
        extensions: [
          history(),
          Prec.highest(
            keymap.of([
              {
                key: "Enter",
                run: (v) => {
                  callbacks.current.onEnter(v);
                  return true;
                },
              },
              {
                key: "Escape",
                run: (v) => {
                  v.contentDOM.blur();
                  return true;
                },
              },
              {
                key: "Shift-Enter",
                run: (v) => (multiline ? insertNewline(v) : true),
              },
              { key: "Mod-b", run: toggleMarker("*") },
              { key: "Mod-i", run: toggleMarker("_") },
            ]),
          ),
          keymap.of([...historyKeymap, ...standardKeymap]),
          EditorView.lineWrapping,
          inlineStyles,
          theme,
          multiline ? [] : singleLine,
          placeholder ? placeholderExt(placeholder) : [],
          attrs.current.of(contentAttributes(enterKeyHint)),
          EditorView.updateListener.of((update) => {
            if (
              update.docChanged &&
              !update.transactions.some((tr) => tr.annotation(external))
            ) {
              callbacks.current.onChange(update.state.doc.toString());
            }
            if (update.focusChanged) {
              if (update.view.hasFocus) callbacks.current.onFocus?.();
              else callbacks.current.onBlur?.();
            }
          }),
        ],
      }),
    });
    viewRef.current = view;
    const restore = restoreRef.current;
    restoreRef.current = null;
    if (restore !== null) {
      view.focus();
      view.dispatch({ selection: EditorSelection.cursor(restore) });
    }
    return () => {
      // Dev StrictMode remounts the view right after focusing it; carry the focus over.
      if (view.hasFocus) restoreRef.current = view.state.selection.main.head;
      viewRef.current = null;
      view.destroy();
    };
  }, []);

  useEffect(() => {
    const view = viewRef.current;
    if (!view || view.state.doc.toString() === value) return;
    view.dispatch({
      changes: { from: 0, to: view.state.doc.length, insert: value },
      annotations: [external.of(true), Transaction.addToHistory.of(false)],
    });
  }, [value]);

  // biome-ignore lint/correctness/useExhaustiveDependencies: only the hint changes after mount
  useEffect(() => {
    viewRef.current?.dispatch({
      effects: attrs.current.reconfigure(contentAttributes(enterKeyHint)),
    });
  }, [enterKeyHint]);

  useImperativeHandle(handleRef, () => ({
    focus: (caret) => {
      const view = viewRef.current;
      if (!view) return;
      view.focus();
      if (caret === undefined) return;
      const pos =
        caret === "end"
          ? view.state.doc.length
          : Math.min(caret, view.state.doc.length);
      view.dispatch({ selection: EditorSelection.cursor(pos) });
    },
    blur: () => viewRef.current?.contentDOM.blur(),
    posAtCoords: (x, y) =>
      viewRef.current?.posAtCoords({ x, y }, false) ?? null,
    select: (anchor, head) =>
      viewRef.current?.dispatch({
        selection: EditorSelection.range(anchor, head),
      }),
  }));

  return <div ref={hostRef} className={className} />;
}
