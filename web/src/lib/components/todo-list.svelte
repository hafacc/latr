<script lang="ts">
import { getAppState } from "../utils/app-state.svelte";
import { groupForFilter } from "../utils/group";
import {
  type Filter,
  matchesFilter,
  rankBySearch,
  sortForFilter,
} from "../utils/todo";
import Logo from "./logo.svelte";
import TodoRow from "./todo-row.svelte";

// A null hint is the "how to add one" line, which differs by screen size.
type EmptyCopy = { title: string; hint: string | null };

function emptyCopy(filter: Filter): EmptyCopy {
  switch (filter) {
    case "ACTIVE":
      return { title: "Nothing to do.", hint: null };
    case "SNOOZED":
      return { title: "Nothing snoozed.", hint: "Snoozed todos wait here." };
    case "DONE":
      return {
        title: "Nothing done yet.",
        hint: "Completed todos land here.",
      };
    case "ALL":
      return { title: "No todos.", hint: null };
  }
}

const app = getAppState();
const searching = $derived(app.search.trim().length > 0);

const groups = $derived.by(() => {
  const filtered = app.todos.filter((t) =>
    matchesFilter(t, app.filter, app.now),
  );
  if (searching) {
    const ranked = rankBySearch(filtered, app.search);
    return ranked.length > 0 ? [{ label: "", todos: ranked }] : [];
  } else {
    return groupForFilter(
      sortForFilter(filtered, app.filter),
      app.filter,
      app.now,
    );
  }
});

const empty = $derived<EmptyCopy>(
  searching
    ? { title: `No todos match “${app.search}”`, hint: "Try fewer words." }
    : emptyCopy(app.filter),
);
</script>

{#if groups.length === 0}
  <div class="flex flex-col items-center text-center py-20 gap-2">
    <Logo class="w-8 h-10 text-border mb-2" shadowClass="fill-border-strong" />
    <div class="text-[15px] text-text">{empty.title}</div>
    <div class="text-[13px] text-text-secondary">
      {#if empty.hint === null}
        <span class="hidden md:inline">Press n to add one.</span>
        <span class="md:hidden">Tap + to add one.</span>
      {:else}
        {empty.hint}
      {/if}
    </div>
  </div>
{:else}
  <div class="space-y-5">
    {#each groups as group (group.label || "search")}
      <section>
        {#if group.label}
          <div
            class="flex gap-2 px-3 pb-1.5 text-[12.5px] font-semibold text-text-secondary"
          >
            <span>{group.label}</span>
            <span class="font-normal text-muted tabular-nums">
              {group.todos.length}
            </span>
          </div>
        {/if}
        <div class="space-y-0.5">
          {#each group.todos as todo (todo.id)}
            <TodoRow {todo} />
          {/each}
        </div>
      </section>
    {/each}
  </div>
{/if}
