<script lang="ts">
import type { SnoozeSource } from "../utils/snooze-suggest";
import SnoozeMenu from "./snooze-menu.svelte";

let {
  onPick,
  onClose,
}: {
  onPick: (
    epochMillis: number,
    source: SnoozeSource,
    pickedKey?: string,
  ) => void;
  onClose: () => void;
} = $props();

let root: HTMLDivElement;
let above = $state(false);

$effect(() => {
  const anchor = root.parentElement;
  if (!anchor) return;
  function place() {
    if (!anchor) return;
    const rect = anchor.getBoundingClientRect();
    const below = window.innerHeight - rect.bottom - 16;
    const aboveSpace = rect.top - 16;
    above = root.offsetHeight > below && aboveSpace > below;
  }
  place();
  const observer = new ResizeObserver(place);
  observer.observe(root);
  return () => observer.disconnect();
});

$effect(() => {
  function onDocClick(e: MouseEvent) {
    if (!root.contains(e.target as Node)) onClose();
  }
  function onKey(e: KeyboardEvent) {
    if (e.key === "Escape") onClose();
  }
  document.addEventListener("mousedown", onDocClick);
  document.addEventListener("keydown", onKey);
  return () => {
    document.removeEventListener("mousedown", onDocClick);
    document.removeEventListener("keydown", onKey);
  };
});
</script>

<!-- svelte-ignore a11y_click_events_have_key_events: the handlers only keep clicks and keys from reaching the row behind -->
<div
  bind:this={root}
  class={[
    "absolute z-30 right-0 w-80 rounded-[14px] bg-surface-raised shadow-pop animate-rise",
    above ? "bottom-full mb-2" : "top-full mt-2",
  ]}
  onclick={(e) => e.stopPropagation()}
  onkeydown={(e) => e.stopPropagation()}
  role="menu"
  tabindex="-1"
  aria-label="Snooze until"
>
  <SnoozeMenu {onPick} />
</div>
