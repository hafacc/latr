<script lang="ts">
import ArrowLeft from "@lucide/svelte/icons/arrow-left";
import Clock from "@lucide/svelte/icons/clock";
import { flushSync, untrack } from "svelte";
import { getAppState } from "../utils/app-state.svelte";
import { formatClockMinutes, parseClockInput } from "../utils/calendar";
import { desktop } from "../utils/media.svelte";
import {
  LAST_DAY,
  type Repeat,
  type RepeatDraft,
  type RepeatUnit,
} from "../utils/repeat";
import type { QuickTime } from "../utils/snooze-suggest";

let {
  repeat,
  onSave,
  onBack,
  timeStep = $bindable(false),
}: {
  repeat: Repeat | null;
  onSave: (draft: RepeatDraft) => void;
  onBack: () => void;
  // Phones only; the sheet around the editor titles it and leads back from it.
  timeStep?: boolean;
} = $props();

const COUNTS = [1, 2, 3, 4, 5, 6, 7];
const UNITS: readonly RepeatUnit[] = ["day", "week", "month"];
const WEEKDAYS = [
  "Monday",
  "Tuesday",
  "Wednesday",
  "Thursday",
  "Friday",
  "Saturday",
  "Sunday",
];
const MONTH_DAYS = Array.from({ length: 31 }, (_, index) => index + 1);
const DEFAULT_AT = "09:00";

/** The todo's own rule, else weekly on today at the strongest quick time. */
function initialDraft(
  current: Repeat | null,
  now: Date,
  quickTimes: QuickTime[],
): RepeatDraft {
  if (current) {
    return {
      every: current.every,
      unit: current.unit,
      weekday: current.weekday,
      monthDay: current.monthDay,
      at: current.at,
    };
  } else {
    let best: QuickTime | null = null;
    for (const quick of quickTimes) {
      if (!best || quick.weight > best.weight) best = quick;
    }
    return {
      every: 1,
      unit: "week",
      weekday: ((now.getDay() + 6) % 7) + 1,
      monthDay: now.getDate(),
      at: best ? formatClockMinutes(best.clockMinutes) : DEFAULT_AT,
    };
  }
}

const navButton =
  "w-7 h-7 flex items-center justify-center rounded-lg text-text-secondary hover:bg-surface-hover transition-colors";
const sectionLabel =
  "text-xs max-md:text-[13px] font-semibold text-text-secondary";
const choice =
  "h-9 max-md:h-11 rounded-lg max-md:rounded-[10px] tabular-nums transition-colors";
const chosen = "bg-accent text-on-accent font-semibold";
const unchosen = "bg-surface-muted text-text hover:bg-surface-hover";

const app = getAppState();
const uid = $props.id();
const initial = untrack(() =>
  initialDraft(repeat, new Date(app.now), app.snoozeQuickTimes),
);

let every = $state(initial.every);
let unit = $state<RepeatUnit>(initial.unit);
let weekday = $state(initial.weekday);
let monthDay = $state(initial.monthDay);
let typedTime = $state(initial.at);
let timeInput = $state<HTMLInputElement>();

const typedMinutes = $derived(parseClockInput(typedTime));

function unitLabel(value: RepeatUnit): string {
  return every > 1 ? `${value}s` : value;
}

function save(minutes: number | null) {
  if (minutes === null) return;
  onSave({ every, unit, weekday, monthDay, at: formatClockMinutes(minutes) });
}

function openTimeStep() {
  // Synchronous so the focus lands inside the tap, which iOS needs to raise the keyboard.
  flushSync(() => {
    timeStep = true;
  });
  timeInput?.focus();
}
</script>

{#snippet timeField()}
  <div class="flex gap-2">
    <label
      class="flex-1 min-w-0 flex items-center gap-2 h-8 max-md:h-10 px-2.5 rounded-[10px] bg-surface-muted text-text-secondary focus-within:ring-1 focus-within:ring-accent"
    >
      <Clock class="w-4 h-4 shrink-0" aria-hidden="true" />
      <input
        bind:this={timeInput}
        type="text"
        inputmode="numeric"
        bind:value={typedTime}
        onkeydown={(e) => {
          if (e.key === "Enter") {
            e.preventDefault();
            save(typedMinutes);
          }
        }}
        aria-label="Time (24-hour)"
        aria-invalid={typedMinutes === null}
        placeholder="HH:MM"
        class="flex-1 min-w-0 bg-transparent outline-none text-sm text-text tabular-nums max-md:text-[16px]"
      >
    </label>
    <button
      type="button"
      disabled={typedMinutes === null}
      onclick={() => save(typedMinutes)}
      class="h-8 max-md:h-10 px-4 rounded-[10px] bg-accent text-on-accent text-sm font-medium hover:opacity-90 transition-opacity disabled:opacity-40 disabled:cursor-not-allowed"
    >
      Save
    </button>
  </div>
{/snippet}

{#if timeStep}
  <div class="flex flex-col gap-3 p-3">
    {@render timeField()}
  </div>
{:else}
  <div class="flex flex-col gap-4 p-3 max-md:pt-2">
    {#if desktop.current}
      <div class="flex items-center gap-1.5">
        <button
          type="button"
          onclick={onBack}
          aria-label="Back"
          class={navButton}
        >
          <ArrowLeft class="w-4 h-4" />
        </button>
        <h2 class="m-0 flex-1 text-[15px] font-semibold">Repeat</h2>
      </div>
    {/if}

    <div class="flex flex-col gap-2">
      <span id="{uid}-every" class={sectionLabel}>Every</span>
      <fieldset
        aria-labelledby="{uid}-every"
        class="min-w-0 grid grid-cols-7 gap-1.5 max-md:gap-2"
      >
        {#each COUNTS as count (count)}
          <button
            type="button"
            aria-pressed={every === count}
            onclick={() => {
              every = count;
            }}
            class={[
              choice,
              "text-sm max-md:text-[16px]",
              every === count ? chosen : unchosen,
            ]}
          >
            {count}
          </button>
        {/each}
      </fieldset>
      <fieldset
        aria-label="Unit"
        class="min-w-0 grid grid-cols-3 gap-0.5 p-0.5 max-md:p-[3px] rounded-[10px] max-md:rounded-xl bg-surface-muted"
      >
        {#each UNITS as value (value)}
          <button
            type="button"
            aria-pressed={unit === value}
            onclick={() => {
              unit = value;
            }}
            class={[
              "h-8 max-md:h-[38px] rounded-lg max-md:rounded-[9px] text-sm max-md:text-[15px] transition-colors",
              unit === value
                ? "bg-surface-raised text-text font-medium shadow-sm"
                : "text-text-secondary hover:text-text",
            ]}
          >
            {unitLabel(value)}
          </button>
        {/each}
      </fieldset>
    </div>

    {#if unit === "week"}
      <div class="flex flex-col gap-2">
        <span id="{uid}-on-week" class={sectionLabel}>On</span>
        <fieldset
          aria-labelledby="{uid}-on-week"
          class="min-w-0 grid grid-cols-7 gap-1.5 max-md:gap-2"
        >
          {#each WEEKDAYS as name, index (name)}
            {@const day = index + 1}
            <button
              type="button"
              aria-pressed={weekday === day}
              aria-label={name}
              onclick={() => {
                weekday = day;
              }}
              class={[
                choice,
                "text-[13px] max-md:text-[15px]",
                weekday === day ? chosen : unchosen,
              ]}
            >
              {name[0]}
            </button>
          {/each}
        </fieldset>
      </div>
    {:else if unit === "month"}
      <div class="flex flex-col gap-2">
        <span id="{uid}-on-month" class={sectionLabel}>On</span>
        <fieldset
          aria-labelledby="{uid}-on-month"
          class="min-w-0 grid grid-cols-7 gap-1"
        >
          {#each MONTH_DAYS as day (day)}
            <button
              type="button"
              aria-pressed={monthDay === day}
              onclick={() => {
                monthDay = day;
              }}
              class={[
                "h-[30px] max-md:h-10 rounded-lg max-md:rounded-[10px] text-[13px] max-md:text-[15px] tabular-nums transition-colors",
                monthDay === day ? chosen : "text-text hover:bg-surface-hover",
              ]}
            >
              {day}
            </button>
          {/each}
          <button
            type="button"
            aria-pressed={monthDay === LAST_DAY}
            onclick={() => {
              monthDay = LAST_DAY;
            }}
            class={[
              "col-span-4 h-[30px] max-md:h-10 rounded-lg max-md:rounded-[10px] text-[13px] max-md:text-[15px] transition-colors",
              monthDay === LAST_DAY ? chosen : unchosen,
            ]}
          >
            Last day
          </button>
        </fieldset>
      </div>
    {/if}

    <div class="flex flex-col gap-2">
      <span id="{uid}-at" class={sectionLabel}>At</span>
      {#if app.snoozeQuickTimes.length > 0 || !desktop.current}
        <fieldset
          aria-labelledby="{uid}-at"
          class="min-w-0 flex items-center gap-1.5 max-md:gap-2"
        >
          {#each app.snoozeQuickTimes as quick (quick.clockMinutes)}
            <button
              type="button"
              onclick={() => save(quick.clockMinutes)}
              class="flex-1 min-w-0 h-8 max-md:h-10 rounded-full bg-accent-soft text-accent-strong text-[13px] max-md:text-[15px] font-medium tabular-nums hover:opacity-90 transition-opacity"
            >
              {formatClockMinutes(quick.clockMinutes)}
            </button>
          {/each}
          {#if !desktop.current}
            <button
              type="button"
              onclick={openTimeStep}
              class="shrink-0 h-10 px-2 ml-auto rounded-[10px] text-accent text-[15px] font-medium hover:bg-surface-hover"
            >
              Other time
            </button>
          {/if}
        </fieldset>
      {/if}
      {#if desktop.current}
        {@render timeField()}
      {/if}
    </div>
  </div>
{/if}
