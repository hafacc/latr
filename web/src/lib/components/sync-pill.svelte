<script lang="ts">
import CloudOff from "@lucide/svelte/icons/cloud-off";
import RefreshCw from "@lucide/svelte/icons/refresh-cw";
import { getAppState } from "../utils/app-state.svelte";
import { isOnline } from "../utils/online.svelte";

// How long syncing must persist before the indicator appears, so brief
// fromCache blips around writes don't flicker it.
const SYNC_INDICATOR_DELAY_MS = 500;

const app = getAppState();
let showSyncing = $state(false);

$effect(() => {
  if (!app.syncing) {
    showSyncing = false;
    return;
  }
  const id = setTimeout(() => {
    showSyncing = true;
  }, SYNC_INDICATOR_DELAY_MS);
  return () => clearTimeout(id);
});
</script>

{#if showSyncing}
  {#if isOnline()}
    <span
      class="inline-flex items-center gap-1.5 h-6 px-2 rounded-full bg-surface-muted text-text-secondary text-xs"
      title="Syncing"
    >
      <RefreshCw class="w-3 h-3 animate-spin" aria-hidden="true" />
      Syncing…
    </span>
  {:else}
    <span
      class="inline-flex items-center gap-1.5 h-6 px-2 rounded-full bg-snooze-soft text-snooze text-xs"
      title="Offline — changes saved on this device"
    >
      <CloudOff class="w-3 h-3" aria-hidden="true" />
      Offline
    </span>
  {/if}
{/if}
