<!-- @component Desktop sidebar footer: account row that opens a popover above it. -->
<script lang="ts">
import ChevronsUpDown from "@lucide/svelte/icons/chevrons-up-down";
import { getAuthState } from "../utils/auth.svelte";
import AccountPanel from "./account-panel.svelte";
import Avatar from "./avatar.svelte";

const auth = getAuthState();
let open = $state(false);
let root: HTMLDivElement;

$effect(() => {
  if (!open) return;
  function onDoc(e: MouseEvent) {
    if (!root.contains(e.target as Node)) open = false;
  }
  function onKey(e: KeyboardEvent) {
    if (e.key === "Escape") open = false;
  }
  // A collapsed sidebar shrinks back when the pointer leaves it; close with it.
  const sidebar = root.closest("aside");
  function onLeave() {
    if (sidebar?.dataset.collapsed === "true") open = false;
  }
  document.addEventListener("mousedown", onDoc);
  document.addEventListener("keydown", onKey);
  sidebar?.addEventListener("mouseleave", onLeave);
  return () => {
    document.removeEventListener("mousedown", onDoc);
    document.removeEventListener("keydown", onKey);
    sidebar?.removeEventListener("mouseleave", onLeave);
  };
});
</script>

<div bind:this={root} class="relative">
  <button
    type="button"
    onclick={() => {
      open = !open;
    }}
    aria-expanded={open}
    class="w-full flex items-center gap-2.5 h-11 px-2 rounded-[10px] text-sm text-text hover:bg-surface-hover transition-colors"
    title={auth.user?.email ?? "Account"}
  >
    <Avatar user={auth.user} size="w-7 h-7" />
    <span
      class="flex-1 min-w-0 text-left truncate font-medium group-data-[collapsed=true]/sidebar:hidden group-data-[collapsed=true]/sidebar:group-hover/sidebar:inline"
    >
      {auth.user ? (auth.user.displayName ?? auth.user.email) : "Sign in"}
    </span>
    <ChevronsUpDown
      class="w-4 h-4 text-text-secondary shrink-0 group-data-[collapsed=true]/sidebar:hidden group-data-[collapsed=true]/sidebar:group-hover/sidebar:block"
    />
  </button>
  {#if open}
    <div
      class="absolute bottom-full mb-2 inset-x-0 rounded-[14px] bg-surface-raised shadow-pop z-30 animate-rise"
    >
      <AccountPanel
        onDone={() => {
          open = false;
        }}
      />
    </div>
  {/if}
</div>
