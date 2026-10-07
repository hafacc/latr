<script lang="ts">
import X from "@lucide/svelte/icons/x";
import { getAppState } from "../utils/app-state.svelte";
import { isMacPlatform } from "../utils/keyboard";
import { getPwaState } from "../utils/pwa.svelte";
import { UNDO_MS } from "../utils/store";

const SNACKBAR =
  "fixed z-30 left-1/2 -translate-x-1/2 bottom-[calc(env(safe-area-inset-bottom)+88px)] md:bottom-7 md:left-[calc(50%+var(--sidebar-reserved)/2)] flex flex-col overflow-hidden rounded-full bg-snackbar text-on-snackbar shadow-dock animate-rise";

const app = getAppState();
const pwa = getPwaState();

const label = $derived.by(() => {
  const entry = app.lastUndo;
  if (!entry) return "";
  else if (entry.kind === "snooze") return "Snoozed";
  else if (entry.kind === "complete") return "Completed";
  else if (entry.todos.length === 1) return "Deleted";
  else return `Deleted ${entry.todos.length} todos`;
});
</script>

{#if app.lastUndo && app.undoExpiresAt}
  <div role="status" class={SNACKBAR}>
    <div class="flex items-center gap-3.5 h-10 pl-[18px] pr-2 text-[13.5px]">
      <span>{label}</span>
      <button
        type="button"
        onclick={app.undo}
        class="px-2 py-1.5 rounded-full font-semibold text-snackbar-accent hover:bg-snackbar-hover"
      >
        Undo
      </button>
      <kbd
        class="hidden md:inline mr-2 px-1.5 rounded-md bg-snackbar-hover font-sans text-[11.5px] font-medium opacity-80"
      >
        {isMacPlatform() ? "⌘Z" : "Ctrl+Z"}
      </kbd>
    </div>
    {#key app.undoExpiresAt}
      <div
        class="h-0.5 bg-snackbar-accent origin-left"
        style:animation="undo-drain {UNDO_MS}ms linear forwards"
      ></div>
    {/key}
  </div>
{:else if pwa.updateReady}
  <div role="status" class={SNACKBAR}>
    <div class="flex items-center gap-3.5 h-10 pl-[18px] pr-2 text-[13.5px]">
      <span>New version</span>
      <button
        type="button"
        onclick={pwa.applyUpdate}
        class="px-2 py-1.5 rounded-full font-semibold text-snackbar-accent hover:bg-snackbar-hover"
      >
        Reload
      </button>
      <button
        type="button"
        onclick={pwa.dismissUpdate}
        aria-label="Dismiss"
        class="-ml-2 p-1.5 rounded-full opacity-80 hover:bg-snackbar-hover"
      >
        <X class="w-3.5 h-3.5" />
      </button>
    </div>
  </div>
{/if}
