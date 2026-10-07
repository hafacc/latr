<script lang="ts">
import Check from "@lucide/svelte/icons/check";
import Clock from "@lucide/svelte/icons/clock";
import Pin from "@lucide/svelte/icons/pin";
import PinOff from "@lucide/svelte/icons/pin-off";
import Trash2 from "@lucide/svelte/icons/trash-2";
import Undo2 from "@lucide/svelte/icons/undo-2";
import { flushSync, untrack } from "svelte";
import { getAppState } from "../utils/app-state.svelte";
import { formatSnoozeTime } from "../utils/format";
import { getModifierState } from "../utils/kbd-modifier.svelte";
import { desktop } from "../utils/media.svelte";
import type { SnoozeSource } from "../utils/snooze-suggest";
import {
  commitThreshold,
  committedSide,
  lockDirection,
  type SwipeAction,
  type SwipeLock,
  type SwipeSide,
  startsAtEdge,
  swipeAction,
} from "../utils/swipe";
import { isoToEpoch, isSnoozed, type Todo } from "../utils/todo";
import type { IconType } from "./filters";
import Hint from "./hint.svelte";
import SnoozePopover from "./snooze-popover.svelte";
import SnoozeSheet from "./snooze-sheet.svelte";
import StateIcon from "./state-icon.svelte";
import StyledText from "./styled-text.svelte";
import type { TextEditorHandle } from "./text-editor";
import TextEditor from "./text-editor.svelte";

type PointerOnRow = PointerEvent & { currentTarget: HTMLDivElement };

type Drag = {
  pointerId: number;
  x: number;
  y: number;
  width: number;
  lock: SwipeLock;
};

const SWIPE_STYLE: Record<
  SwipeAction,
  { soft: string; strong: string; label: string; icon: IconType }
> = {
  snooze: {
    soft: "bg-snooze-soft text-snooze",
    strong: "bg-snooze text-on-snooze",
    label: "Snooze",
    icon: Clock,
  },
  reactivate: {
    soft: "bg-accent-soft text-accent",
    strong: "bg-accent text-on-accent",
    label: "Restore",
    icon: Undo2,
  },
  done: {
    soft: "bg-done-soft text-done",
    strong: "bg-done text-on-done",
    label: "Done",
    icon: Check,
  },
  delete: {
    soft: "bg-danger-soft text-danger",
    strong: "bg-danger text-on-danger",
    label: "Delete",
    icon: Trash2,
  },
};

// Desktop action buttons hide at rest and fade in on hover/focus (or while the snooze popover is open).
const hoverAction =
  "opacity-0 pointer-events-none transition-opacity group-hover/row:opacity-100 group-hover/row:pointer-events-auto group-focus-within/row:opacity-100 group-focus-within/row:pointer-events-auto group-data-[keep-actions=true]/row:opacity-100 group-data-[keep-actions=true]/row:pointer-events-auto";
const actionButton =
  "relative w-7 h-7 flex items-center justify-center rounded-lg hover:bg-surface-muted transition-colors";

let { todo }: { todo: Todo } = $props();

const app = getAppState();
const modifier = getModifierState();

let snoozeOpen = $state(false);
// While the editor has focus its text is owned here, so snapshot updates can't jump the cursor mid-edit.
let draft = $state("");
let isFocused = $state(false);
let isHovered = $state(false);
let dx = $state(0);
let settling = $state(false);
let row = $state<HTMLDivElement>();
let editor = $state<TextEditorHandle | null>(null);
let pendingCaret: { x: number; y: number } | null = null;
let drag: Drag | null = null;
let suppressClick = false;

const focused = $derived(app.focusId === todo.id);
const text = $derived(isFocused ? draft : todo.text);
// Hints follow focus, and fall back to hover when nothing is focused so
// you can ⌘-act on whichever row the mouse is over without clicking in.
const showHints = $derived(
  modifier.held && (focused || (app.focusId === null && isHovered)),
);
const isDone = $derived(todo.state === "DONE");
const isActivelySnoozed = $derived(isSnoozed(todo, app.now));
const wasUnsnoozed = $derived(
  !isDone && !isActivelySnoozed && todo.snoozeUntil !== null,
);
const showPin = $derived(!isDone);
const swipeSide = $derived<SwipeSide | null>(
  dx > 0 ? "start" : dx < 0 ? "end" : null,
);
const swipe = $derived(
  swipeSide
    ? SWIPE_STYLE[swipeAction(swipeSide, isDone, isActivelySnoozed)]
    : null,
);
const pastThreshold = $derived(
  swipeSide !== null && Math.abs(dx) >= commitThreshold(row?.offsetWidth ?? 0),
);
const primaryLabel = $derived(isDone ? "Reactivate" : "Mark done");
const textTone = $derived(
  isDone ? "line-through text-text-secondary" : "text-text",
);

// Runs when the row is focused and again once its editor exists, whichever comes last.
$effect(() => {
  const handle = editor;
  if (!focused || !handle) return;
  untrack(() => {
    row?.scrollIntoView({ block: "nearest" });
    const pending = pendingCaret;
    pendingCaret = null;
    handle.focus(
      (pending && handle.posAtCoords(pending.x, pending.y)) ?? "end",
    );
  });
  // The on-screen keyboard shrinks the viewport after focus; keep the row clear of the dock.
  const onResize = () => row?.scrollIntoView({ block: "nearest" });
  window.addEventListener("resize", onResize);
  return () => window.removeEventListener("resize", onResize);
});

$effect(() => {
  if (!focused) return;
  function onBlurKey(e: KeyboardEvent) {
    if (e.key === "Escape") {
      (document.activeElement as HTMLElement | null)?.blur();
    }
  }
  window.addEventListener("keydown", onBlurKey);
  return () => window.removeEventListener("keydown", onBlurKey);
});

function primaryToggle() {
  if (isDone) app.reactivate(todo.id);
  else app.markDone(todo.id);
}

function runSwipe(action: SwipeAction) {
  switch (action) {
    case "snooze":
      snoozeOpen = true;
      break;
    case "reactivate":
      app.reactivate(todo.id);
      break;
    case "done":
      app.markDone(todo.id);
      break;
    case "delete":
      app.removeUndoable(todo.id);
      break;
  }
}

function onPointerDown(e: PointerOnRow) {
  if (e.pointerType !== "touch") return;
  if (isFocused) return;
  if (startsAtEdge(e.clientX, window.innerWidth)) return;
  drag = {
    pointerId: e.pointerId,
    x: e.clientX,
    y: e.clientY,
    width: e.currentTarget.offsetWidth,
    lock: "pending",
  };
}

function onPointerMove(e: PointerOnRow) {
  if (!drag || drag.pointerId !== e.pointerId) return;
  const moveX = e.clientX - drag.x;
  const moveY = e.clientY - drag.y;
  if (drag.lock === "pending") {
    drag.lock = lockDirection(moveX, moveY);
    if (drag.lock === "vertical") {
      drag = null;
      return;
    }
    if (drag.lock === "horizontal") {
      e.currentTarget.setPointerCapture(e.pointerId);
      suppressClick = true;
      editor?.blur();
    }
  }
  if (drag.lock === "horizontal") dx = moveX;
}

function endDrag(e: PointerOnRow, cancelled: boolean) {
  const ended = drag;
  if (!ended || ended.pointerId !== e.pointerId) return;
  drag = null;
  if (ended.lock !== "horizontal") return;
  const side = cancelled ? null : committedSide(dx, ended.width);
  settling = true;
  dx = 0;
  if (side) runSwipe(swipeAction(side, isDone, isActivelySnoozed));
  window.setTimeout(() => {
    suppressClick = false;
  }, 0);
}

// The native mousedown focus would land on the static text, which the editor replaces mid-event.
function onTextMouseDown(e: MouseEvent) {
  if (e.button !== 0) return;
  e.preventDefault();
  const start = { x: e.clientX, y: e.clientY };
  pendingCaret = start;
  app.setFocus(todo.id);
  // Mount and focus the editor inside this event: iOS only raises the keyboard for a focus made during the tap.
  flushSync();
  // The editor mounts under the pointer mid-drag, so extend its selection by hand.
  const onMove = (move: MouseEvent) => {
    const anchor = editor?.posAtCoords(start.x, start.y);
    const head = editor?.posAtCoords(move.clientX, move.clientY);
    if (editor && anchor != null && head != null) {
      editor.select(anchor, head);
    }
  };
  const onUp = () => {
    window.removeEventListener("mousemove", onMove);
    window.removeEventListener("mouseup", onUp);
  };
  window.addEventListener("mousemove", onMove);
  window.addEventListener("mouseup", onUp);
}

function onEditorBlur() {
  const typed = draft;
  isFocused = false;
  if (typed.trim().length === 0) app.remove(todo.id);
  else app.dropEmpty();
  app.setFocus(null);
}

function onPick(epoch: number, source: SnoozeSource, pickedKey?: string) {
  if (app.snooze(todo.id, epoch, source, pickedKey)) snoozeOpen = false;
}

function closeSnooze() {
  snoozeOpen = false;
}
</script>

{#snippet stripButton(
  label: string,
  Icon: IconType,
  onClick: () => void,
  danger: boolean,
)}
  <!-- Preventing the down events keeps the editor focused through the tap, or the strip unmounts before the click lands. -->
  <button
    type="button"
    onpointerdown={(e) => e.preventDefault()}
    onmousedown={(e) => e.preventDefault()}
    onclick={onClick}
    class={[
      "h-9 px-2.5 inline-flex items-center gap-1.5 rounded-lg text-sm font-medium transition-colors",
      danger
        ? "text-danger hover:bg-danger-soft"
        : "text-text-secondary hover:bg-surface-muted",
    ]}
  >
    <Icon class="w-4 h-4" />
    {label}
  </button>
{/snippet}

<!-- svelte-ignore a11y_no_static_element_interactions -->
<!-- biome-ignore lint/a11y/noStaticElementInteractions: touch swipes and hover hints only; every action here is also a button -->
<div
  bind:this={row}
  data-todo-id={todo.id}
  data-keep-actions={snoozeOpen}
  onpointerdown={onPointerDown}
  onpointermove={onPointerMove}
  onpointerup={(e) => endDrag(e, false)}
  onpointercancel={(e) => endDrag(e, true)}
  onmouseenter={() => {
    isHovered = true;
  }}
  onmouseleave={() => {
    isHovered = false;
  }}
  onclickcapture={(e) => {
    if (suppressClick) {
      e.preventDefault();
      e.stopPropagation();
    }
  }}
  class={[
    "group/row relative rounded-[10px] touch-pan-y touch-pinch-zoom scroll-mt-20 max-md:scroll-mt-[calc(env(safe-area-inset-top)+64px)] max-md:scroll-mb-[calc(env(safe-area-inset-bottom)+104px)]",
    dx !== 0 && "overflow-hidden",
    snoozeOpen && "z-20",
  ]}
>
  {#if swipe}
    <div
      aria-hidden="true"
      class={[
        "absolute inset-0 flex items-center gap-2 px-5 text-sm font-medium transition-colors",
        pastThreshold ? swipe.strong : swipe.soft,
        swipeSide === "start" ? "justify-start" : "justify-end",
      ]}
    >
      <swipe.icon class="w-5 h-5" />
      {swipe.label}
    </div>
  {/if}

  <div
    style:transform={dx !== 0 ? `translateX(${dx}px)` : null}
    ontransitionend={() => {
      settling = false;
    }}
    class={[
      "relative flex items-start gap-3 min-h-10 max-md:min-h-14 px-3 py-[9px] max-md:py-[15px] rounded-[10px] hover:bg-surface-hover focus-within:bg-surface-hover",
      dx !== 0 ? "bg-surface-hover shadow-dock" : "bg-bg",
      settling
        ? "transition-transform duration-200 ease-out"
        : "transition-colors",
    ]}
  >
    <button
      type="button"
      data-action="primary"
      onclick={primaryToggle}
      aria-label={primaryLabel}
      title={primaryLabel}
      class="relative -mx-1 -mb-1 -mt-0.5 p-1 rounded-md hover:bg-surface-muted transition-colors shrink-0"
    >
      <span class={["block", showHints && "invisible"]}>
        <StateIcon
          {isDone}
          isSnoozed={isActivelySnoozed}
          pinned={todo.pinned}
          {wasUnsnoozed}
        />
      </span>
      {#if showHints}
        <Hint key="D" tint="text-done" />
      {/if}
    </button>

    <div class="flex-1 min-w-0 flex flex-col">
      <div class="flex min-w-0 cursor-text">
        {#if focused || isFocused}
          <TextEditor
            bind:handle={editor}
            value={text}
            onChange={(value) => {
              draft = value;
              app.edit(todo.id, value);
            }}
            onFocus={() => {
              draft = todo.text;
              isFocused = true;
              app.setFocus(todo.id);
            }}
            onBlur={onEditorBlur}
            onEnter={(view) => view.contentDOM.blur()}
            multiline
            ariaLabel="Todo"
            class={[
              "flex-1 min-w-0 text-[15px] leading-[22px] max-md:text-[16px] max-md:leading-6",
              textTone,
            ].join(" ")}
          />
        {:else}
          <!-- svelte-ignore a11y_no_noninteractive_tabindex -->
          <!-- biome-ignore lint/a11y/useSemanticElements: static styled text that swaps to the editor on focus -->
          <div
            role="textbox"
            aria-multiline="true"
            tabindex="0"
            onmousedown={onTextMouseDown}
            onfocus={() => app.setFocus(todo.id)}
            class={[
              "flex-1 min-w-0 whitespace-pre-wrap break-words outline-none text-[15px] leading-[22px] max-md:text-[16px] max-md:leading-6",
              textTone,
            ]}
          >
            {#if text === ""}
              {"​"}
            {:else}
              <StyledText {text} />
            {/if}
          </div>
        {/if}
      </div>
      {#if !desktop.current && isFocused}
        <div class="flex gap-1 pt-2 -ml-2">
          {#if isActivelySnoozed}
            {@render stripButton(
              "Unsnooze",
              Undo2,
              () => app.reactivate(todo.id),
              false,
            )}
          {/if}
          {#if !isDone}
            {@render stripButton(
              "Snooze",
              Clock,
              () => {
                snoozeOpen = true;
              },
              false,
            )}
          {/if}
          {#if showPin}
            {@render stripButton(
              todo.pinned ? "Unpin" : "Pin",
              todo.pinned ? PinOff : Pin,
              () => app.togglePinned(todo.id),
              false,
            )}
          {/if}
          {@render stripButton(
            "Delete",
            Trash2,
            () => app.removeUndoable(todo.id),
            true,
          )}
        </div>
      {/if}
    </div>

    <!-- Same badge whether still snoozed (future) or was-unsnoozed (past). -->
    {#if !isDone && todo.snoozeUntil}
      <span
        class="pt-0.5 text-[13px] leading-[18px] text-text-secondary tabular-nums shrink-0 whitespace-nowrap opacity-100 md:group-hover/row:opacity-0 md:group-focus-within/row:opacity-0 md:group-data-[keep-actions=true]/row:opacity-0 transition-opacity"
      >
        {formatSnoozeTime(isoToEpoch(todo.snoozeUntil))}
      </span>
    {/if}

    {#if desktop.current}
      <div
        class="absolute right-2 top-1.5 flex items-center gap-0.5 rounded-lg bg-surface-hover opacity-0 group-hover/row:opacity-100 group-focus-within/row:opacity-100 group-data-[keep-actions=true]/row:opacity-100 transition-opacity"
      >
        {#if isActivelySnoozed}
          <button
            type="button"
            data-action="unsnooze"
            onclick={() => app.reactivate(todo.id)}
            aria-label="Unsnooze"
            title="Unsnooze"
            class={[actionButton, "text-accent", hoverAction]}
          >
            <Undo2 class={["w-4 h-4", showHints && "invisible"]} />
            {#if showHints}
              <Hint key="S" />
            {/if}
          </button>
        {/if}

        {#if !isDone}
          <div class={["relative", hoverAction]}>
            <button
              type="button"
              data-action="snooze"
              onclick={() => {
                snoozeOpen = !snoozeOpen;
              }}
              aria-label={isActivelySnoozed ? "Reschedule snooze" : "Snooze"}
              title={isActivelySnoozed ? "Reschedule" : "Snooze"}
              class={[actionButton, "text-text-secondary"]}
            >
              <Clock
                class={[
                  "w-4 h-4",
                  showHints && !isActivelySnoozed && "invisible",
                ]}
              />
              {#if showHints && !isActivelySnoozed}
                <Hint key="S" />
              {/if}
            </button>
            {#if snoozeOpen}
              <SnoozePopover {onPick} onClose={closeSnooze} />
            {/if}
          </div>
        {/if}

        {#if showPin}
          <!-- Preventing mousedown keeps the button from taking focus, which would hold the row highlighted with its actions showing after a pin. -->
          <button
            type="button"
            data-action="pin"
            onmousedown={(e) => e.preventDefault()}
            onclick={() => app.togglePinned(todo.id)}
            aria-label={todo.pinned ? "Unpin" : "Pin"}
            title={todo.pinned ? "Unpin" : "Pin"}
            class={[
              actionButton,
              hoverAction,
              todo.pinned ? "text-accent" : "text-text-secondary",
            ]}
          >
            <Pin class={["w-4 h-4", showHints && "invisible"]} />
            {#if showHints}
              <Hint key="K" />
            {/if}
          </button>
        {/if}

        <button
          type="button"
          data-action="delete"
          onclick={() => app.removeUndoable(todo.id)}
          aria-label="Delete"
          title="Delete"
          class={[
            actionButton,
            "text-text-secondary hover:text-danger",
            hoverAction,
          ]}
        >
          <Trash2 class={["w-4 h-4", showHints && !focused && "invisible"]} />
          {#if showHints && !focused}
            <Hint key="⌫" />
          {/if}
        </button>
      </div>
    {/if}
  </div>

  {#if !desktop.current && snoozeOpen}
    <SnoozeSheet todoText={todo.text} {onPick} onClose={closeSnooze} />
  {/if}
</div>
