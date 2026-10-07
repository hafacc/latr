<!-- @component Desktop list header: filter title, count, sync status, search. -->
<script lang="ts">
import Search from "@lucide/svelte/icons/search";
import X from "@lucide/svelte/icons/x";
import ClearAllButton from "./clear-all-button.svelte";
import SyncPill from "./sync-pill.svelte";

let {
  title,
  count,
  search,
  onSearch,
  onClearAll,
}: {
  title: string;
  count: number;
  search: string;
  onSearch: (search: string) => void;
  onClearAll: (() => void) | null;
} = $props();

let input: HTMLInputElement;

export function focusSearch(): void {
  input.focus();
}
</script>

<header
  class="hidden md:flex sticky top-0 z-10 items-center gap-3 h-16 bg-bg/90 backdrop-blur"
>
  <h1 class="m-0 text-[22px] leading-7 font-semibold tracking-[-0.01em]">
    {title}
  </h1>
  {#if count > 0}
    <span class="text-[13px] text-text-secondary tabular-nums">{count}</span>
  {/if}
  <SyncPill />
  <div class="flex-1"></div>
  {#if onClearAll}
    <ClearAllButton onClick={onClearAll} />
  {/if}
  <label
    class="group/search flex items-center gap-2 w-60 focus-within:w-72 h-8 px-2.5 rounded-[10px] bg-surface-muted focus-within:bg-surface focus-within:ring-1 focus-within:ring-accent text-text-secondary transition-all"
  >
    <Search class="w-[15px] h-[15px] shrink-0" aria-hidden="true" />
    <input
      bind:this={input}
      type="search"
      value={search}
      oninput={(e) => onSearch(e.currentTarget.value)}
      onkeydown={(e) => {
        if (e.key === "Escape") e.currentTarget.blur();
      }}
      placeholder="Search"
      aria-label="Search todos"
      class="flex-1 min-w-0 bg-transparent outline-none text-[13px] text-text placeholder:text-text-secondary [&::-webkit-search-cancel-button]:hidden"
    >
    {#if search}
      <button
        type="button"
        onclick={() => onSearch("")}
        aria-label="Clear search"
        class="p-0.5 rounded text-text-secondary hover:text-text"
      >
        <X class="w-3.5 h-3.5" />
      </button>
    {:else}
      <kbd
        class="text-[11.5px] font-medium font-sans px-1.5 rounded-md bg-surface border border-border group-focus-within/search:hidden"
      >
        /
      </kbd>
    {/if}
  </label>
</header>
