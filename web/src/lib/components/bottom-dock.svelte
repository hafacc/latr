<script lang="ts">
import Plus from "@lucide/svelte/icons/plus";
import type { Filter } from "../utils/todo";
import { DOCK_ORDER, FILTER_META } from "./filters";

let {
  filter,
  onFilter,
  onCompose,
}: {
  filter: Filter;
  onFilter: (filter: Filter) => void;
  onCompose: () => void;
} = $props();
</script>

<nav
  aria-label="Filters"
  class="md:hidden fixed inset-x-0 bottom-0 z-20 flex items-center justify-center gap-3 pl-[max(1rem,env(safe-area-inset-left))] pr-[max(1rem,env(safe-area-inset-right))] pt-2 pb-[max(env(safe-area-inset-bottom),16px)] pointer-events-none"
>
  <div
    class="pointer-events-auto flex items-center gap-1 p-1.5 rounded-full bg-surface-raised shadow-dock"
  >
    {#each DOCK_ORDER as f (f)}
      {@const { label, icon: Icon } = FILTER_META[f]}
      {@const active = f === filter}
      <button
        type="button"
        onclick={() => onFilter(f)}
        aria-pressed={active}
        aria-label={label}
        class={[
          "h-11 flex items-center justify-center gap-2 rounded-full transition-all",
          active
            ? "px-4 bg-accent-soft text-accent-strong font-medium text-sm"
            : "w-11 text-text-secondary",
        ]}
      >
        <Icon class="w-5 h-5 shrink-0" />
        {#if active}
          <span>{label}</span>
        {/if}
      </button>
    {/each}
  </div>
  <button
    type="button"
    onclick={onCompose}
    aria-label="Add a todo"
    class="pointer-events-auto w-14 h-14 flex items-center justify-center rounded-2xl bg-accent text-on-accent shadow-dock active:scale-95 transition-transform"
  >
    <Plus class="w-6 h-6" />
  </button>
</nav>
