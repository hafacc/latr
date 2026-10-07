<script lang="ts">
import Check from "@lucide/svelte/icons/check";
import Clock from "@lucide/svelte/icons/clock";
import Pin from "@lucide/svelte/icons/pin";

let {
  isDone,
  isSnoozed,
  pinned,
  wasUnsnoozed,
}: {
  isDone: boolean;
  isSnoozed: boolean;
  pinned: boolean;
  wasUnsnoozed: boolean;
} = $props();
</script>

<!-- Pinned displaces the state glyph only on an active row; elsewhere it just tints it. -->
{#if isDone}
  <span
    class={[
      "w-[18px] h-[18px] rounded-full flex items-center justify-center",
      pinned ? "bg-accent text-on-accent" : "bg-done text-on-done",
    ]}
  >
    <Check class="w-3 h-3" strokeWidth={3} />
  </span>
{:else if isSnoozed}
  <Clock
    class={["w-[18px] h-[18px]", pinned ? "text-accent" : "text-snooze"]}
  />
{:else if pinned}
  <Pin class="w-[18px] h-[18px] text-accent" />
{:else if wasUnsnoozed}
  <Clock class="w-[18px] h-[18px] text-snooze opacity-70" />
{:else}
  <svg
    viewBox="0 0 24 24"
    class="w-[18px] h-[18px] text-muted"
    fill="none"
    stroke="currentColor"
    stroke-width="1.5"
    aria-hidden="true"
  >
    <circle cx="12" cy="12" r="8.5" />
  </svg>
{/if}
