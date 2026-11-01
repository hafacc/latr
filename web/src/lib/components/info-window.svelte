<!-- @component An info page inside the app, with a link to its web page by the title. Desktop: a centered dialog; phones: a bottom sheet. -->
<script lang="ts">
import ExternalLink from "@lucide/svelte/icons/external-link";
import X from "@lucide/svelte/icons/x";
import { resolve } from "$app/paths";
import type { InfoPage } from "../utils/info-pages";
import { desktop } from "../utils/media.svelte";
import InfoText from "./info-text.svelte";
import Sheet from "./sheet.svelte";

let {
  page,
  path,
  onClose,
}: { page: InfoPage; path: string; onClose: () => void } = $props();

let dialog = $state<HTMLDialogElement>();

$effect(() => {
  const shown = dialog;
  if (!shown) return;
  shown.showModal();
  return () => shown.close();
});

const iconButton =
  "grid place-items-center w-8 h-8 rounded-lg text-text-secondary hover:bg-surface-hover hover:text-text transition-colors";
</script>

{#snippet body()}
  <div class="px-5 pt-4 pb-5 text-[15px] leading-relaxed text-text-secondary">
    <div class="flex items-center gap-1 mb-4">
      <h2 id="info-title" class="m-0 text-lg font-semibold text-text">
        {page.title}
      </h2>
      <a
        href={`${resolve("/")}${path}`}
        target="_blank"
        rel="noopener"
        aria-label="Open on the web"
        title="Open on the web"
        class={iconButton}
      >
        <ExternalLink class="w-4 h-4" />
      </a>
      {#if desktop.current}
        <button
          type="button"
          onclick={onClose}
          aria-label="Close"
          class={["ml-auto", iconButton]}
        >
          <X class="w-4 h-4" />
        </button>
      {/if}
    </div>
    <InfoText {page} />
  </div>
{/snippet}

{#if desktop.current}
  <!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_noninteractive_element_interactions: the click only closes on the backdrop; Esc is the dialog's own cancel -->
  <dialog
    bind:this={dialog}
    aria-labelledby="info-title"
    oncancel={(e) => {
      e.preventDefault();
      onClose();
    }}
    onclick={(e) => {
      if (e.target === e.currentTarget) onClose();
    }}
    onkeydown={(e) => e.stopPropagation()}
    class="m-auto p-0 w-[min(620px,calc(100vw-32px))] max-h-[min(720px,calc(100dvh-64px))] overflow-y-auto overscroll-contain rounded-[14px] bg-surface-raised text-text shadow-pop animate-rise"
  >
    {@render body()}
  </dialog>
{:else}
  <Sheet label={page.title} {onClose}>{@render body()}</Sheet>
{/if}
