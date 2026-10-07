<script lang="ts">
import Calendar from "@lucide/svelte/icons/calendar";
import CalendarArrowUp from "@lucide/svelte/icons/calendar-arrow-up";
import CalendarDays from "@lucide/svelte/icons/calendar-days";
import CalendarPlus from "@lucide/svelte/icons/calendar-plus";
import History from "@lucide/svelte/icons/history";
import Moon from "@lucide/svelte/icons/moon";
import Sun from "@lucide/svelte/icons/sun";
import Sunrise from "@lucide/svelte/icons/sunrise";
import Timer from "@lucide/svelte/icons/timer";
import { getAppState } from "../utils/app-state.svelte";
import {
  formatClock,
  type RowIcon,
  type SnoozeSource,
} from "../utils/snooze-suggest";
import CustomPicker from "./custom-picker.svelte";
import type { IconType } from "./filters";
import MenuRowButton from "./menu-row-button.svelte";

let {
  onPick,
}: {
  onPick: (
    epochMillis: number,
    source: SnoozeSource,
    pickedKey?: string,
  ) => void;
} = $props();

const MENU_CLOCK_TICK_MS = 30_000;

const ROW_ICONS: Record<RowIcon, IconType> = {
  offset: Timer,
  todayDay: Sun,
  todayNight: Moon,
  tomorrow: Sunrise,
  days: CalendarDays,
  weekday: CalendarArrowUp,
  monthly: Calendar,
};

const app = getAppState();
let customOpen = $state(false);

$effect(() => {
  app.refreshNow();
  const id = setInterval(app.refreshNow, MENU_CLOCK_TICK_MS);
  return () => clearInterval(id);
});
</script>

{#if customOpen}
  <CustomPicker
    {onPick}
    onBack={() => {
      customOpen = false;
    }}
  />
{:else}
  <div class="flex flex-col gap-0.5 p-1.5">
    <div
      class="px-2.5 pt-2 pb-1.5 text-[12.5px] font-semibold text-text-secondary max-md:hidden"
    >
      Snooze until
    </div>
    {#each app.snoozeRows as row (`${row.keyId}-${row.time}`)}
      <MenuRowButton
        icon={ROW_ICONS[row.icon]}
        tint="snooze"
        text={row.text}
        time={formatClock(row.time)}
        onClick={() => onPick(row.time, "suggestion", row.keyId)}
      />
    {/each}
    {#if app.snoozeLastRow}
      {@const last = app.snoozeLastRow}
      <MenuRowButton
        icon={History}
        tint="neutral"
        text={last.text}
        time={formatClock(last.time)}
        onClick={() => onPick(last.time, "last")}
      />
    {/if}
    {#if app.snoozeRows.length > 0 || app.snoozeLastRow}
      <div class="h-px bg-border mx-1.5 my-1"></div>
    {/if}
    <MenuRowButton
      icon={CalendarPlus}
      tint="neutral"
      text="Pick date & time…"
      onClick={() => {
        customOpen = true;
      }}
    />
  </div>
{/if}
