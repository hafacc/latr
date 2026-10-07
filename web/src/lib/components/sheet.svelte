<!-- @component A bottom sheet on a native modal <dialog>: top layer, backdrop, Esc and focus trap for free. -->
<script lang="ts">
import type { Snippet } from "svelte";

let {
  label,
  onClose,
  children,
}: { label: string; onClose: () => void; children: Snippet } = $props();

let dialog: HTMLDialogElement;

$effect(() => {
  dialog.showModal();
  return () => dialog.close();
});
</script>

<!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_noninteractive_element_interactions: the click only closes on the backdrop; Esc is the dialog's own cancel -->
<dialog
  bind:this={dialog}
  aria-label={label}
  oncancel={(e) => {
    e.preventDefault();
    onClose();
  }}
  onclick={(e) => {
    if (e.target === e.currentTarget) onClose();
  }}
  onkeydown={(e) => e.stopPropagation()}
  class="m-0 mt-auto p-0 w-full max-w-none max-h-[85dvh] overflow-y-auto overscroll-contain bg-surface-raised text-text rounded-t-[28px] shadow-sheet animate-sheet"
>
  <div class="pb-[max(env(safe-area-inset-bottom),16px)]">
    <div class="flex justify-center pt-3 pb-1" aria-hidden="true">
      <div class="w-8 h-1 rounded-full bg-border"></div>
    </div>
    {@render children()}
  </div>
</dialog>
