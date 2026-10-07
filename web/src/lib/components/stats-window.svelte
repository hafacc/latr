<!-- @component Desktop: a centered modal dialog; phones: a bottom sheet. -->
<script lang="ts">
import { doc, getDoc } from "firebase/firestore";
import { db } from "../utils/firebase";
import { desktop } from "../utils/media.svelte";
import { globalPickCounts } from "../utils/snooze-stats-wire";
import PickRankChart from "./pick-rank-chart.svelte";
import Sheet from "./sheet.svelte";

let { onClose }: { onClose: () => void } = $props();

let counts = $state<number[] | null>(null);
let dialog = $state<HTMLDialogElement>();

$effect(() => {
  let live = true;
  getDoc(doc(db(), "snoozePickLog", "global"))
    .then((snap) => {
      if (live) counts = globalPickCounts(snap.data());
    })
    .catch((e) => console.error("stats read failed", e));
  return () => {
    live = false;
  };
});

$effect(() => {
  const shown = dialog;
  if (!shown) return;
  shown.showModal();
  return () => shown.close();
});
</script>

{#snippet body()}
  <div class="p-5">
    <h2 id="stats-title" class="m-0 mb-4 text-base font-semibold">Stats</h2>
    {#if counts}
      <PickRankChart {counts} />
    {:else}
      <div class="h-44"></div>
    {/if}
  </div>
{/snippet}

{#if desktop.current}
  <!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_noninteractive_element_interactions: the click only closes on the backdrop; Esc is the dialog's own cancel -->
  <dialog
    bind:this={dialog}
    aria-labelledby="stats-title"
    oncancel={(e) => {
      e.preventDefault();
      onClose();
    }}
    onclick={(e) => {
      if (e.target === e.currentTarget) onClose();
    }}
    onkeydown={(e) => e.stopPropagation()}
    class="m-auto p-0 w-[min(560px,calc(100vw-32px))] rounded-[14px] bg-surface-raised text-text shadow-pop animate-rise"
  >
    {@render body()}
  </dialog>
{:else}
  <Sheet label="Stats" {onClose}> {@render body()} </Sheet>
{/if}
