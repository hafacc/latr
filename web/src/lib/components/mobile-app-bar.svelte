<!-- @component Phone app bar: title and count, which a search icon swaps for a search field. -->
<script lang="ts">
import ArrowLeft from "@lucide/svelte/icons/arrow-left";
import Search from "@lucide/svelte/icons/search";
import AccountButton from "./account-button.svelte";
import ClearAllButton from "./clear-all-button.svelte";
import SyncPill from "./sync-pill.svelte";

let {
  title,
  count,
  search,
  onSearch,
  searching,
  onSearching,
  onClearAll,
}: {
  title: string;
  count: number;
  search: string;
  onSearch: (search: string) => void;
  searching: boolean;
  onSearching: (searching: boolean) => void;
  onClearAll: (() => void) | null;
} = $props();

const open = $derived(searching || search.length > 0);
</script>

<header
  class="md:hidden sticky top-0 z-10 flex items-center gap-2 h-14 pl-[max(1rem,env(safe-area-inset-left))] pr-[max(1rem,env(safe-area-inset-right))] pt-[env(safe-area-inset-top)] box-content bg-bg/90 backdrop-blur"
>
  {#if open}
    <button
      type="button"
      onclick={() => {
        onSearch("");
        onSearching(false);
      }}
      aria-label="Close search"
      class="-ml-2 w-10 h-10 flex items-center justify-center rounded-full text-text-secondary hover:bg-surface-hover"
    >
      <ArrowLeft class="w-5 h-5" />
    </button>
    <!-- Focused as it appears: the field only exists because the user just tapped search. -->
    <input
      {@attach (node) => node.focus()}
      type="search"
      value={search}
      oninput={(e) => onSearch(e.currentTarget.value)}
      placeholder="Search"
      aria-label="Search todos"
      class="flex-1 min-w-0 h-10 px-3 rounded-[10px] bg-surface-muted outline-none text-[16px] text-text placeholder:text-text-secondary"
    >
  {:else}
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
    <button
      type="button"
      onclick={() => onSearching(true)}
      aria-label="Search"
      class="w-10 h-10 flex items-center justify-center rounded-full text-text-secondary hover:bg-surface-hover"
    >
      <Search class="w-5 h-5" />
    </button>
    <AccountButton />
  {/if}
</header>
