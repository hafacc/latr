<script lang="ts">
import ArrowLeft from "@lucide/svelte/icons/arrow-left";
import type { Repeat, RepeatDraft } from "../utils/repeat";
import type { SnoozeSource } from "../utils/snooze-suggest";
import Sheet from "./sheet.svelte";
import SnoozeMenu from "./snooze-menu.svelte";
import StyledText from "./styled-text.svelte";

let {
  todoText,
  repeat,
  onPick,
  onRepeat,
  onClose,
}: {
  todoText: string;
  repeat: Repeat | null;
  onRepeat: (draft: RepeatDraft) => void;
  onPick: (
    epochMillis: number,
    source: SnoozeSource,
    pickedKey?: string,
  ) => void;
  onClose: () => void;
} = $props();

let step = $state<"menu" | "custom" | "repeat">("menu");
let repeatTimeStep = $state(false);
const title = $derived(
  step !== "repeat" ? "Snooze" : repeatTimeStep ? "Pick a time" : "Repeat",
);
</script>

<Sheet label={title} {onClose}>
  <div class="px-5 pt-1 pb-1">
    {#if step === "repeat"}
      <div class="flex items-center gap-1.5 -ml-1.5">
        <button
          type="button"
          onclick={() => {
            if (repeatTimeStep) repeatTimeStep = false;
            else step = "menu";
          }}
          aria-label="Back"
          class="w-7 h-7 flex items-center justify-center rounded-lg text-text-secondary hover:bg-surface-hover transition-colors"
        >
          <ArrowLeft class="w-4 h-4" />
        </button>
        <h2 class="m-0 text-lg font-semibold">{title}</h2>
      </div>
    {:else}
      <h2 class="m-0 text-lg font-semibold">{title}</h2>
    {/if}
    <p class="m-0 mt-0.5 text-sm text-text-secondary truncate">
      <StyledText text={todoText} />
    </p>
  </div>
  {#if step === "repeat"}
    <div class="px-1.5">
      <SnoozeMenu {repeat} {onPick} {onRepeat} bind:step bind:repeatTimeStep />
    </div>
  {:else}
    <div class="px-1.5" role="menu" aria-label="Snooze until">
      <SnoozeMenu {repeat} {onPick} {onRepeat} bind:step />
    </div>
  {/if}
</Sheet>
