<script lang="ts">
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
  Prec,
  Transaction,
} from "@codemirror/state";
import {
  EditorView,
  keymap,
  placeholder as placeholderExt,
} from "@codemirror/view";
import { onMount } from "svelte";
import {
  inlineStyles,
  singleLine,
  type TextEditorHandle,
  theme,
  toggleMarker,
} from "./text-editor";

let {
  handle = $bindable(null),
  value,
  onChange,
  onFocus,
  onBlur,
  onEnter,
  multiline = false,
  placeholder,
  enterKeyHint,
  ariaLabel,
  class: className,
}: {
  // Set once the editor exists, so a parent can focus it.
  handle?: TextEditorHandle | null;
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
  class?: string;
} = $props();

const external = Annotation.define<boolean>();
const attrs = new Compartment();
let host: HTMLDivElement;
let view: EditorView | null = null;

function contentAttributes(hint?: string) {
  return EditorView.contentAttributes.of({
    "aria-label": ariaLabel,
    "aria-multiline": multiline ? "true" : "false",
    spellcheck: "true",
    autocorrect: "on",
    autocapitalize: "sentences",
    writingsuggestions: "true",
    translate: "yes",
    ...(hint ? { enterkeyhint: hint } : {}),
  });
}

// The view is created once; later changes go through the effects below.
onMount(() => {
  const created = new EditorView({
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
                onEnter(v);
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
        attrs.of(contentAttributes(enterKeyHint)),
        EditorView.updateListener.of((update) => {
          if (
            update.docChanged &&
            !update.transactions.some((tr) => tr.annotation(external))
          ) {
            onChange(update.state.doc.toString());
          }
          if (update.focusChanged) {
            if (update.view.hasFocus) onFocus?.();
            else onBlur?.();
          }
        }),
      ],
    }),
  });
  view = created;
  handle = {
    focus: (caret) => {
      created.focus();
      if (caret === undefined) return;
      const pos =
        caret === "end"
          ? created.state.doc.length
          : Math.min(caret, created.state.doc.length);
      created.dispatch({ selection: EditorSelection.cursor(pos) });
    },
    blur: () => created.contentDOM.blur(),
    posAtCoords: (x, y) => created.posAtCoords({ x, y }, false),
    select: (anchor, head) =>
      created.dispatch({ selection: EditorSelection.range(anchor, head) }),
  };
  return () => {
    view = null;
    handle = null;
    created.destroy();
  };
});

$effect(() => {
  const next = value;
  if (!view || view.state.doc.toString() === next) return;
  view.dispatch({
    changes: { from: 0, to: view.state.doc.length, insert: next },
    annotations: [external.of(true), Transaction.addToHistory.of(false)],
  });
});

$effect(() => {
  view?.dispatch({
    effects: attrs.reconfigure(contentAttributes(enterKeyHint)),
  });
});
</script>

<div bind:this={host} class={className}></div>
