<!-- @component A single-line input that shows the `#tags` in its value as chips. -->
<script lang="ts">
import type { Attachment } from "svelte/attachments";
import type { HTMLInputAttributes } from "svelte/elements";
import { parseTags } from "../utils/tags";

let {
  value,
  input = $bindable(),
  attach,
  class: className,
  textClass,
  ...rest
}: Omit<HTMLInputAttributes, "class" | "value"> & {
  value: string;
  input?: HTMLInputElement;
  attach?: Attachment<HTMLInputElement>;
  // Layout and background of the field.
  class?: string;
  // Font and padding, shared by the input and the copy drawn behind it.
  textClass?: string;
} = $props();

let mirror: HTMLDivElement;

const parts = $derived.by(() => {
  const out: { text: string; tag: boolean }[] = [];
  let pos = 0;
  for (const tag of parseTags(value)) {
    if (tag.start > pos)
      out.push({ text: value.slice(pos, tag.start), tag: false });
    out.push({ text: value.slice(tag.start, tag.end), tag: true });
    pos = tag.end;
  }
  if (pos < value.length) out.push({ text: value.slice(pos), tag: false });
  return out;
});

function syncScroll() {
  if (input) mirror.scrollLeft = input.scrollLeft;
}
</script>

<!-- The input's own text is transparent; the copy behind it draws the text, so only its caret and selection show. -->
<div class={["relative", className]}>
  <div
    bind:this={mirror}
    aria-hidden="true"
    class={[
      "absolute inset-0 flex items-center overflow-hidden whitespace-pre pointer-events-none text-text",
      textClass,
    ]}
  >
    {#each parts as part, index (index)}
      {#if part.tag}
        <span class="md-tag">{part.text}</span>
      {:else}
        {part.text}
      {/if}
    {/each}
  </div>
  <input
    bind:this={input}
    {@attach attach ?? (() => {})}
    {value}
    {...rest}
    onscroll={syncScroll}
    onselect={syncScroll}
    onkeyup={syncScroll}
    oninputcapture={() => requestAnimationFrame(syncScroll)}
    class={[
      "relative w-full h-full bg-transparent outline-none text-transparent caret-text placeholder:text-text-secondary [&::-webkit-search-cancel-button]:hidden",
      textClass,
    ]}
  >
</div>
