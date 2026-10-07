<script lang="ts">
import type { Snippet } from "svelte";

let {
  id,
  title,
  body,
  onCancel,
  children,
}: {
  id: string;
  title: string;
  body: string;
  onCancel: () => void;
  // The dialog's buttons.
  children: Snippet;
} = $props();

let dialog: HTMLDialogElement;

$effect(() => {
  dialog.showModal();
  return () => dialog.close();
});
</script>

<dialog
  bind:this={dialog}
  aria-labelledby={id}
  oncancel={(e) => {
    e.preventDefault();
    onCancel();
  }}
  onkeydown={(e) => e.stopPropagation()}
  class="m-auto p-5 w-[min(400px,calc(100vw-32px))] rounded-[14px] bg-surface-raised text-text shadow-pop animate-rise"
>
  <h2 {id} class="m-0 text-base font-semibold">{title}</h2>
  <p class="mt-2 mb-5 text-sm text-text-secondary">{body}</p>
  <div class="flex flex-wrap justify-end gap-2">{@render children()}</div>
</dialog>
