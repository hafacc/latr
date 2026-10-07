<script lang="ts">
import PanelLeftClose from "@lucide/svelte/icons/panel-left-close";
import PanelLeftOpen from "@lucide/svelte/icons/panel-left-open";
import { FILTERS, type Filter } from "../utils/todo";
import { FILTER_META } from "./filters";
import Logo from "./logo.svelte";
import SidebarAccount from "./sidebar-account.svelte";

let {
  filter,
  onFilter,
  counts,
  collapsed,
  onToggleCollapsed,
}: {
  filter: Filter;
  onFilter: (filter: Filter) => void;
  counts: Partial<Record<Filter, number>>;
  collapsed: boolean;
  onToggleCollapsed: () => void;
} = $props();

const expandedOnly =
  "group-data-[collapsed=true]/sidebar:hidden group-data-[collapsed=true]/sidebar:group-hover/sidebar:inline";
</script>

<aside
  data-collapsed={collapsed}
  class="group/sidebar hidden md:flex flex-col fixed inset-y-0 left-0 z-20 bg-surface-muted transition-[width] duration-200 ease-out w-60 data-[collapsed=true]:w-14 data-[collapsed=true]:hover:w-60 overflow-hidden px-2 py-4"
>
  <div class="flex items-center justify-between h-8 px-2 mb-3">
    <span class="flex items-center gap-2.5">
      <Logo class="w-5 h-5 shrink-0 text-accent" />
      <span class={["font-semibold text-base tracking-tight", expandedOnly]}>
        Latr
      </span>
    </span>
    <button
      type="button"
      onclick={onToggleCollapsed}
      class="p-1.5 rounded-lg text-text-secondary hover:bg-surface-hover hover:text-text transition-colors group-data-[collapsed=true]/sidebar:hidden group-data-[collapsed=true]/sidebar:group-hover/sidebar:block"
      aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
      title={collapsed ? "Expand" : "Collapse"}
    >
      {#if collapsed}
        <PanelLeftOpen class="w-4 h-4" />
      {:else}
        <PanelLeftClose class="w-4 h-4" />
      {/if}
    </button>
  </div>

  <nav aria-label="Filters" class="flex-1 space-y-0.5">
    {#each FILTERS as f (f)}
      {@const { label, icon: Icon } = FILTER_META[f]}
      {@const active = f === filter}
      {@const count = counts[f]}
      <button
        type="button"
        onclick={() => onFilter(f)}
        aria-current={active ? "page" : undefined}
        class={[
          "w-full flex items-center gap-2.5 px-2.5 h-8 rounded-[10px] text-sm transition-colors",
          active
            ? "bg-surface-hover text-text font-medium"
            : "text-text hover:bg-surface-hover",
        ]}
      >
        <Icon
          class={[
            "w-4 h-4 shrink-0",
            active ? "text-accent" : "text-text-secondary",
          ]}
        />
        <span class={["flex-1 text-left truncate", expandedOnly]}>
          {label}
        </span>
        {#if count !== undefined && count > 0}
          <span
            class={[
              "text-[12.5px] text-text-secondary tabular-nums",
              expandedOnly,
            ]}
          >
            {count}
          </span>
        {/if}
      </button>
    {/each}
  </nav>

  <SidebarAccount />
</aside>
