<script lang="ts">
import { onDestroy, untrack } from "svelte";
import { getAppState } from "../utils/app-state.svelte";
import { type Bucket, groupForFilter } from "../utils/group";
import { type DropSlot, planMove } from "../utils/reorder";
import {
  type Filter,
  isoToEpoch,
  matchesFilter,
  rankBySearch,
  sortForFilter,
  type Todo,
} from "../utils/todo";
import Logo from "./logo.svelte";
import TodoRow from "./todo-row.svelte";

// How close to the top or bottom of the window a drag starts scrolling the page, and how fast.
const SCROLL_EDGE_TOP_PX = 96;
const SCROLL_EDGE_BOTTOM_PX = 120;
const SCROLL_MAX_PX_PER_FRAME = 16;
// The space between rows (`space-y-0.5`).
const ROW_GAP_PX = 2;

// `y` is where the gap for the dropped row opens, in page coordinates.
type DropTarget = { slot: DropSlot; y: number; inPlace: boolean };

type DragSession = {
  todo: Todo;
  pointerId: number;
  targets: DropTarget[];
  // Page coordinates, so they hold while the page scrolls under the drag.
  centerY: number;
  // Where each row and heading sat when the drag began, by `rowKey` or `headingKey`.
  centers: Map<string, number>;
  // How far the rows between the dragged row and its target step aside.
  step: number;
  mouse: boolean;
  startClientY: number;
  startScrollY: number;
  clientY: number;
  target: DropTarget | null;
  frame: number;
};

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

const ranked = $derived(
  searching
    ? rankBySearch(
        app.todos.filter((t) => matchesFilter(t, app.filter, app.now)),
        app.search,
      )
    : [],
);
const groups = $derived<Bucket[]>(
  searching
    ? []
    : groupForFilter(
        sortForFilter(
          app.todos.filter((t) => matchesFilter(t, app.filter, app.now)),
          app.filter,
        ),
        app.filter,
        app.now,
      ),
);
// In Snoozed a row only moves among rows snoozed to its own time.
const tiedSnoozeTimes = $derived.by(() => {
  const seen = new Set<number>();
  const tied = new Set<number>();
  if (app.filter !== "SNOOZED") return tied;
  for (const todo of groups.flatMap((group) => group.todos)) {
    if (!todo.snoozeUntil) continue;
    const at = isoToEpoch(todo.snoozeUntil);
    if (seen.has(at)) tied.add(at);
    else seen.add(at);
  }
  return tied;
});

let container = $state<HTMLDivElement>();
let dragging = $state.raw<{
  id: string;
  offset: number;
  targetY: number;
  mouse: boolean;
} | null>(null);
let session: DragSession | null = null;

function rowKey(id: string): string {
  return `row:${id}`;
}

function headingKey(label: string): string {
  return `heading:${label}`;
}

function movable(todo: Todo): boolean {
  return (
    app.filter !== "SNOOZED" ||
    (todo.snoozeUntil !== null &&
      tiedSnoozeTimes.has(isoToEpoch(todo.snoozeUntil)))
  );
}

/** Every slot `todo` may be dropped at, top to bottom. */
function allowedSlots(todo: Todo): { slot: DropSlot; moved: Todo }[] {
  const now = Date.now();
  const allowed: { slot: DropSlot; moved: Todo }[] = [];
  for (const [bucket, group] of groups.entries()) {
    const others = group.todos.filter((t) => t.id !== todo.id).length;
    for (let index = 0; index <= others; index++) {
      const slot = { bucket, index };
      const moved = planMove(todo, app.filter, groups, slot, now);
      if (moved !== null) allowed.push({ slot, moved });
    }
  }
  return allowed;
}

function dropTargets(todo: Todo, rows: Map<string, DOMRect>): DropTarget[] {
  const own = rows.get(todo.id);
  if (!own) return [];
  return allowedSlots(todo).map(({ slot, moved }) => {
    const others = groups[slot.bucket].todos.filter((t) => t.id !== todo.id);
    const above = rows.get(others[slot.index - 1]?.id);
    const below = rows.get(others[slot.index]?.id);
    let y: number;
    if (moved === todo) y = (own.top + own.bottom) / 2;
    else if (above && below) y = (above.bottom + below.top) / 2;
    else if (below) y = below.top;
    else if (above) y = above.bottom;
    else y = (own.top + own.bottom) / 2;
    return { slot, y: y + window.scrollY, inPlace: moved === todo };
  });
}

function updateDrag() {
  if (!session) return;
  const offset =
    session.clientY -
    session.startClientY +
    window.scrollY -
    session.startScrollY;
  const probe = session.centerY + offset;
  let nearest: DropTarget | null = null;
  for (const target of session.targets) {
    if (!nearest || Math.abs(target.y - probe) < Math.abs(nearest.y - probe)) {
      nearest = target;
    }
  }
  session.target = nearest;
  dragging = {
    id: session.todo.id,
    offset,
    targetY: nearest ? nearest.y : session.centerY,
    mouse: session.mouse,
  };
}

/** How far the row or heading `key` steps aside to open a gap at the drop target. */
function shiftOf(key: string): number {
  const center = session?.centers.get(key);
  if (!dragging || !session || center === undefined) return 0;
  if (center > session.centerY && center < dragging.targetY) {
    return -session.step;
  } else if (center < session.centerY && center > dragging.targetY) {
    return session.step;
  } else {
    return 0;
  }
}

function scrollStep() {
  if (!session) return;
  const fromTop = session.clientY - SCROLL_EDGE_TOP_PX;
  const fromBottom =
    session.clientY - (window.innerHeight - SCROLL_EDGE_BOTTOM_PX);
  let speed = 0;
  if (fromTop < 0) speed = Math.max(-1, fromTop / SCROLL_EDGE_TOP_PX);
  else if (fromBottom > 0) {
    speed = Math.min(1, fromBottom / SCROLL_EDGE_BOTTOM_PX);
  }
  if (speed !== 0) {
    window.scrollBy(0, speed * SCROLL_MAX_PX_PER_FRAME);
    updateDrag();
  }
  session.frame = requestAnimationFrame(scrollStep);
}

function onDragMove(event: PointerEvent) {
  if (!session || event.pointerId !== session.pointerId) return;
  session.clientY = event.clientY;
  updateDrag();
}

function endDrag(drop: boolean) {
  const ended = session;
  if (!ended) return;
  session = null;
  dragging = null;
  cancelAnimationFrame(ended.frame);
  window.removeEventListener("pointermove", onDragMove);
  window.removeEventListener("pointerup", onDragUp);
  window.removeEventListener("pointercancel", onDragCancel);
  window.removeEventListener("keydown", onDragKey, true);
  window.removeEventListener("touchmove", preventScroll);
  document.body.style.cursor = "";
  if (!drop || !ended.target || ended.target.inPlace) return;
  // Planned again: the list or the clock may have moved on during the drag.
  const current = app.todos.find((t) => t.id === ended.todo.id);
  if (!current) return;
  const moved = planMove(
    current,
    app.filter,
    groups,
    ended.target.slot,
    Date.now(),
  );
  if (moved !== null && moved !== current) app.move(moved);
}

function onDragUp(event: PointerEvent) {
  if (event.pointerId === session?.pointerId) endDrag(true);
}

function onDragCancel(event: PointerEvent) {
  if (event.pointerId === session?.pointerId) endDrag(false);
}

function onDragKey(event: KeyboardEvent) {
  if (event.key !== "Escape") return;
  event.preventDefault();
  event.stopPropagation();
  endDrag(false);
}

// A touch would otherwise scroll the page instead of moving the row.
function preventScroll(event: TouchEvent) {
  if (event.cancelable) event.preventDefault();
}

function startDrag(todo: Todo, event: PointerEvent) {
  if (session || !container) return;
  const rows = new Map<string, DOMRect>();
  const centers = new Map<string, number>();
  for (const element of container.querySelectorAll<HTMLElement>(
    "[data-todo-id], [data-heading]",
  )) {
    const rect = element.getBoundingClientRect();
    const center = (rect.top + rect.bottom) / 2 + window.scrollY;
    const { todoId, heading } = element.dataset;
    if (todoId !== undefined) {
      rows.set(todoId, rect);
      centers.set(rowKey(todoId), center);
    } else if (heading !== undefined) {
      centers.set(headingKey(heading), center);
    }
  }
  const own = rows.get(todo.id);
  if (!own) return;
  app.setFocus(null);
  session = {
    todo,
    pointerId: event.pointerId,
    targets: dropTargets(todo, rows),
    centerY: (own.top + own.bottom) / 2 + window.scrollY,
    centers,
    step: own.height + ROW_GAP_PX,
    mouse: event.pointerType === "mouse",
    startClientY: event.clientY,
    startScrollY: window.scrollY,
    clientY: event.clientY,
    target: null,
    frame: requestAnimationFrame(scrollStep),
  };
  window.addEventListener("pointermove", onDragMove);
  window.addEventListener("pointerup", onDragUp);
  window.addEventListener("pointercancel", onDragCancel);
  window.addEventListener("keydown", onDragKey, true);
  window.addEventListener("touchmove", preventScroll, { passive: false });
  if (session.mouse) document.body.style.cursor = "grabbing";
  updateDrag();
}

// The slots a drag measured belong to the list it started in.
$effect(() => {
  void app.filter;
  void app.search;
  untrack(() => endDrag(false));
});

onDestroy(() => endDrag(false));

const empty = $derived<EmptyCopy>(
  searching
    ? { title: `No todos match “${app.search}”`, hint: "Try fewer words." }
    : emptyCopy(app.filter),
);
</script>

{#snippet row(
  todo: Todo,
  canMove: boolean,
)}
  <TodoRow
    {todo}
    movable={canMove}
    dragged={dragging?.id === todo.id}
    offset={dragging?.id === todo.id
      ? dragging.offset
      : shiftOf(rowKey(todo.id))}
    gliding={dragging !== null && dragging.id !== todo.id}
    onDragStart={(event) => startDrag(todo, event)}
  />
{/snippet}

{#if groups.length === 0 && ranked.length === 0}
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
  <div
    bind:this={container}
    class={[
      "relative space-y-5",
      dragging && "select-none",
      // Keeps rows the mouse passes over from showing their hover actions.
      dragging?.mouse && "pointer-events-none",
    ]}
  >
    {#if searching}
      <div class="space-y-0.5">
        {#each ranked as todo (todo.id)}
          {@render row(todo, false)}
        {/each}
      </div>
    {/if}
    {#each groups as group (group.label)}
      <section>
        <div
          data-heading={group.label}
          style:transform={dragging
            ? `translateY(${shiftOf(headingKey(group.label))}px)`
            : null}
          class={[
            "flex gap-2 px-3 pb-1.5 text-[12.5px] font-semibold text-text-secondary",
            dragging && "transition-transform duration-150 ease-out",
          ]}
        >
          <span>{group.label}</span>
          <span class="font-normal text-muted tabular-nums">
            {group.todos.length}
          </span>
        </div>
        <div class="space-y-0.5">
          {#each group.todos as todo (todo.id)}
            {@render row(todo, movable(todo))}
          {/each}
        </div>
      </section>
    {/each}
  </div>
{/if}
