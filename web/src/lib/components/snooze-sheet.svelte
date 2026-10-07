<script lang="ts">
import type { SnoozeSource } from "../utils/snooze-suggest";
import Sheet from "./sheet.svelte";
import SnoozeMenu from "./snooze-menu.svelte";
import StyledText from "./styled-text.svelte";

let {
  todoText,
  onPick,
  onClose,
}: {
  todoText: string;
  onPick: (
    epochMillis: number,
    source: SnoozeSource,
    pickedKey?: string,
  ) => void;
  onClose: () => void;
} = $props();
</script>

<Sheet label="Snooze" {onClose}>
  <div class="px-5 pt-1 pb-1">
    <h2 class="m-0 text-lg font-semibold">Snooze</h2>
    <p class="m-0 mt-0.5 text-sm text-text-secondary truncate">
      <StyledText text={todoText} />
    </p>
  </div>
  <div class="px-1.5" role="menu" aria-label="Snooze until">
    <SnoozeMenu {onPick} />
  </div>
</Sheet>
