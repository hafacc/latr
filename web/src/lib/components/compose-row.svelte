<script lang="ts">
import { getAppState } from "../utils/app-state.svelte";
import { hasModifier, isEditableTarget } from "../utils/keyboard";
import type { TextEditorHandle } from "./text-editor";
import TextEditor from "./text-editor.svelte";

let { onCreated }: { onCreated: () => void } = $props();

const app = getAppState();
let editor = $state<TextEditorHandle | null>(null);
let draft = $state("");
let focused = $state(false);

// On phones the row stays mounted but collapsed until the add button focuses it, since iOS only raises the keyboard for a focus() inside the tap.
const collapsed = $derived(!focused && draft.length === 0);

/** Focus the field; synchronous, so it works from inside a tap handler. */
export function focus(): void {
  editor?.focus();
}

function onKey(e: KeyboardEvent) {
  if (e.key !== "n" || hasModifier(e)) return;
  if (isEditableTarget(e.target)) return;
  e.preventDefault();
  editor?.focus();
}

function submit() {
  const text = draft.trim();
  if (text.length === 0) {
    editor?.blur();
    return;
  }
  app.create(text);
  app.setFocus(null);
  app.setFilter("ACTIVE");
  app.setSearch("");
  onCreated();
  draft = "";
  // Keep focus on the compose input for fast-compose loop.
  requestAnimationFrame(() => editor?.focus());
}
</script>

<svelte:window onkeydown={onKey} />

<div
  class={[
    "group/compose flex items-center gap-3 px-3 rounded-[10px] transition-colors min-h-10 hover:bg-surface-hover focus-within:bg-surface focus-within:ring-1 focus-within:ring-accent",
    collapsed
      ? "max-md:min-h-0 max-md:h-0 max-md:overflow-hidden max-md:opacity-0"
      : "max-md:min-h-14",
  ]}
>
  <svg
    viewBox="0 0 24 24"
    class="w-[18px] h-[18px] shrink-0 text-muted"
    fill="none"
    stroke="currentColor"
    stroke-width="1.5"
    stroke-dasharray="3 3"
    aria-hidden="true"
  >
    <circle cx="12" cy="12" r="8.5" />
  </svg>
  <TextEditor
    bind:handle={editor}
    value={draft}
    onChange={(value) => {
      draft = value;
    }}
    onFocus={() => {
      focused = true;
    }}
    onBlur={() => {
      focused = false;
    }}
    onEnter={submit}
    enterKeyHint={draft.trim().length === 0 ? "done" : "next"}
    placeholder="Add a todo…"
    ariaLabel="Add a todo"
    class="flex-1 min-w-0 py-1 text-[15px] leading-[22px] max-md:text-[16px] max-md:leading-6 text-text"
  />
  <kbd
    class="hidden md:inline text-[11.5px] font-medium px-1.5 rounded-md bg-surface border border-border text-text-secondary font-sans group-focus-within/compose:hidden"
  >
    n
  </kbd>
  <kbd
    class="hidden text-[11.5px] font-medium px-1.5 rounded-md bg-surface border border-border text-text-secondary font-sans group-focus-within/compose:md:inline"
  >
    ↵
  </kbd>
</div>
