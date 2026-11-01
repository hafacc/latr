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
import { desktop } from "../utils/media.svelte";
import {
  inlineStyles,
  singleLine,
  type TagOffer,
  type TextEditorHandle,
  tagCompletion,
  tagOfferAt,
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
  tagSuggestions,
  onTagOffer,
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
  // Known tags matching the name typed after a `#`, offered in a dropdown.
  tagSuggestions?: (prefix: string) => string[];
  // On phones the tags go here, for a bar above the keyboard, instead of a dropdown.
  // `previous` is this editor's last offer, so clearing it leaves another editor's alone.
  onTagOffer?: (offer: TagOffer | null, previous: TagOffer | null) => void;
  ariaLabel: string;
  class?: string;
} = $props();

const external = Annotation.define<boolean>();
const attrs = new Compartment();
let host: HTMLDivElement;
let view: EditorView | null = null;
let offered: TagOffer | null = null;

function offerTags(target: EditorView) {
  const offer =
    !desktop.current && target.hasFocus && tagSuggestions
      ? tagOfferAt(target, tagSuggestions)
      : null;
  if (offer?.names.join("\n") === offered?.names.join("\n")) return;
  const previous = offered;
  offered = offer;
  onTagOffer?.(offer, previous);
}

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
        // Before the keymap below, so Enter and Escape reach an open dropdown first.
        tagCompletion((prefix) =>
          desktop.current ? (tagSuggestions?.(prefix) ?? []) : [],
        ),
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
          if (update.docChanged || update.selectionSet || update.focusChanged) {
            offerTags(update.view);
          }
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
    focusAtEdge: (x, edge) => {
      created.focus();
      const box = created.contentDOM.getBoundingClientRect();
      const lineMiddle = created.defaultLineHeight / 2;
      const y =
        edge === "first" ? box.top + lineMiddle : box.bottom - lineMiddle;
      const pos =
        created.posAtCoords({ x, y }, false) ??
        (edge === "first" ? 0 : created.state.doc.length);
      // A bare range would be read as {anchor, head}, dropping the column.
      created.dispatch({
        selection: EditorSelection.create([
          EditorSelection.cursor(pos, 0, undefined, x - box.left),
        ]),
      });
    },
  };
  return () => {
    view = null;
    handle = null;
    if (offered) onTagOffer?.(null, offered);
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
