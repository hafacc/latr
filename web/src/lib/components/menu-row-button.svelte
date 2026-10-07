<script lang="ts">
import type { IconType } from "./filters";

let {
  icon: Icon,
  tint,
  text,
  time,
  onClick,
}: {
  icon: IconType;
  tint: "snooze" | "neutral";
  text: string;
  time?: string;
  onClick: () => void;
} = $props();
</script>

<button
  type="button"
  role="menuitem"
  onclick={onClick}
  class="w-full flex items-center gap-2.5 min-h-10 max-md:min-h-12 px-2.5 rounded-[10px] text-sm text-left text-text hover:bg-surface-hover focus-visible:bg-surface-hover outline-none transition-colors"
>
  <span
    class={[
      "w-7 h-7 shrink-0 rounded-lg flex items-center justify-center",
      tint === "snooze"
        ? "bg-snooze-soft text-snooze"
        : "bg-surface-muted text-text-secondary",
    ]}
  >
    <Icon class="w-4 h-4" aria-hidden="true" />
  </span>
  <span class="flex-1 min-w-0 font-medium">{text}</span>
  {#if time}
    <span class="text-[13px] text-text-secondary tabular-nums shrink-0">
      {time}
    </span>
  {/if}
</button>
