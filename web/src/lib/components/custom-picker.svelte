<script lang="ts">
import ArrowLeft from "@lucide/svelte/icons/arrow-left";
import ChevronLeft from "@lucide/svelte/icons/chevron-left";
import ChevronRight from "@lucide/svelte/icons/chevron-right";
import Clock from "@lucide/svelte/icons/clock";
import { getAppState } from "../utils/app-state.svelte";
import {
  formatClockMinutes,
  monthGrid,
  monthLabel,
  nextQuarterHour,
  parseClockInput,
  parseYmd,
  toYmd,
  WEEKDAY_SHORT,
} from "../utils/calendar";
import {
  type QuickTime,
  quickTimeEpoch,
  type SnoozeSource,
} from "../utils/snooze-suggest";

let {
  onPick,
  onBack,
}: {
  onPick: (epochMillis: number, source: SnoozeSource) => void;
  onBack: () => void;
} = $props();

/** Today at the strongest quick time if it's still ahead, else the next quarter hour after now. */
function initialPick(
  now: Date,
  quickTimes: QuickTime[],
): { ymd: string; time: string } {
  let best: QuickTime | null = null;
  for (const q of quickTimes) if (!best || q.weight > best.weight) best = q;
  const today = toYmd(now);
  if (best) {
    const epoch = quickTimeEpoch(today, best.clockMinutes);
    if (epoch !== null && epoch > now.getTime()) {
      return { ymd: today, time: formatClockMinutes(best.clockMinutes) };
    }
  }
  const next = nextQuarterHour(now);
  return { ymd: next.ymd, time: formatClockMinutes(next.minutes) };
}

const navButton =
  "w-7 h-7 flex items-center justify-center rounded-lg text-text-secondary hover:bg-surface-hover disabled:text-muted disabled:hover:bg-transparent transition-colors";

const app = getAppState();
const initial = initialPick(new Date(app.now), app.snoozeQuickTimes);
const initialDate = parseYmd(initial.ymd);

let selected = $state(initial.ymd);
let view = $state({
  year: initialDate.getFullYear(),
  month: initialDate.getMonth(),
});
let customTime = $state(initial.time);

const today = $derived(new Date(app.now));
const grid = $derived(monthGrid(view.year, view.month, today));
const atCurrentMonth = $derived(
  view.year === today.getFullYear() && view.month === today.getMonth(),
);
const typedMinutes = $derived(parseClockInput(customTime));
const customEpoch = $derived.by(() => {
  if (typedMinutes === null) return null;
  const date = parseYmd(selected);
  date.setHours(Math.floor(typedMinutes / 60), typedMinutes % 60, 0, 0);
  return date.getTime() > app.now ? date.getTime() : null;
});

function shiftMonth(delta: number) {
  const date = new Date(view.year, view.month + delta, 1);
  view = { year: date.getFullYear(), month: date.getMonth() };
}

function pick(epoch: number | null) {
  if (epoch === null || epoch <= Date.now()) app.refreshNow();
  else onPick(epoch, "custom");
}
</script>

<div class="flex flex-col gap-3 p-3">
  <div class="flex items-center gap-1.5">
    <button type="button" onclick={onBack} aria-label="Back" class={navButton}>
      <ArrowLeft class="w-4 h-4" />
    </button>
    <span class="flex-1 text-sm font-semibold">
      {monthLabel(view.year, view.month)}
    </span>
    <button
      type="button"
      onclick={() => shiftMonth(-1)}
      disabled={atCurrentMonth}
      aria-label="Previous month"
      class={navButton}
    >
      <ChevronLeft class="w-4 h-4" />
    </button>
    <button
      type="button"
      onclick={() => shiftMonth(1)}
      aria-label="Next month"
      class={navButton}
    >
      <ChevronRight class="w-4 h-4" />
    </button>
  </div>

  <div class="grid grid-cols-7 gap-0.5 text-center">
    {#each WEEKDAY_SHORT as weekday (weekday)}
      <span class="text-[11.5px] font-medium text-text-secondary py-1">
        {weekday}
      </span>
    {/each}
    {#each grid as cell (cell.ymd)}
      {@const isSelected = cell.ymd === selected}
      <button
        type="button"
        disabled={cell.past}
        onclick={() => {
          selected = cell.ymd;
        }}
        aria-pressed={isSelected}
        aria-label={parseYmd(cell.ymd).toDateString()}
        class={[
          "h-[34px] max-md:h-10 rounded-[10px] text-[13px] tabular-nums transition-colors",
          isSelected
            ? "bg-accent text-on-accent font-semibold"
            : cell.past
              ? "text-muted"
              : cell.inMonth
                ? "text-text hover:bg-surface-hover"
                : "text-text-secondary hover:bg-surface-hover",
          cell.today && !isSelected && "ring-1 ring-inset ring-accent",
        ]}
      >
        {cell.day}
      </button>
    {/each}
  </div>

  {#if app.snoozeQuickTimes.length > 0}
    <div class="flex gap-1.5">
      {#each app.snoozeQuickTimes as quick (quick.clockMinutes)}
        {@const epoch = quickTimeEpoch(selected, quick.clockMinutes)}
        <button
          type="button"
          disabled={epoch === null || epoch <= app.now}
          onclick={() => pick(epoch)}
          class="flex-1 h-8 max-md:h-10 rounded-full bg-accent-soft text-accent-strong text-[13px] font-medium tabular-nums hover:opacity-90 transition-opacity disabled:bg-surface-muted disabled:text-muted"
        >
          {formatClockMinutes(quick.clockMinutes)}
        </button>
      {/each}
    </div>
  {/if}

  <div class="flex gap-2">
    <label
      class="flex-1 min-w-0 flex items-center gap-2 h-8 max-md:h-10 px-2.5 rounded-[10px] bg-surface-muted text-text-secondary focus-within:ring-1 focus-within:ring-accent"
    >
      <Clock class="w-4 h-4 shrink-0" aria-hidden="true" />
      <input
        type="text"
        inputmode="numeric"
        bind:value={customTime}
        onkeydown={(e) => {
          if (e.key === "Enter") {
            e.preventDefault();
            pick(customEpoch);
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
      disabled={customEpoch === null}
      onclick={() => pick(customEpoch)}
      class="h-8 max-md:h-10 px-4 rounded-[10px] bg-accent text-on-accent text-sm font-medium hover:opacity-90 transition-opacity disabled:opacity-40 disabled:cursor-not-allowed"
    >
      Snooze
    </button>
  </div>
</div>
