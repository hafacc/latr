<script lang="ts">
import Monitor from "@lucide/svelte/icons/monitor";
import Moon from "@lucide/svelte/icons/moon";
import Sun from "@lucide/svelte/icons/sun";
import { getThemeState, type ThemeMode } from "../utils/theme.svelte";
import type { IconType } from "./filters";

const OPTIONS: { mode: ThemeMode; label: string; icon: IconType }[] = [
  { mode: "system", label: "System", icon: Monitor },
  { mode: "light", label: "Light", icon: Sun },
  { mode: "dark", label: "Dark", icon: Moon },
];

const theme = getThemeState();
</script>

<fieldset class="flex p-0.5 rounded-full bg-surface-muted border-0 m-0">
  <legend class="sr-only">Theme</legend>
  {#each OPTIONS as option (option.mode)}
    <button
      type="button"
      aria-pressed={theme.mode === option.mode}
      onclick={() => theme.setMode(option.mode)}
      class={[
        "flex-1 h-7 inline-flex items-center justify-center gap-1.5 rounded-full text-xs font-medium transition-colors",
        theme.mode === option.mode
          ? "bg-surface text-text shadow-pop"
          : "text-text-secondary hover:text-text",
      ]}
    >
      <option.icon class="w-3.5 h-3.5" />
      {option.label}
    </button>
  {/each}
</fieldset>
