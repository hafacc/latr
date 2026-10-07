<!-- @component Account details, theme and sign-in/out; shared by the desktop popover and the phone sheet. -->
<script lang="ts">
import ChartColumn from "@lucide/svelte/icons/chart-column";
import Download from "@lucide/svelte/icons/download";
import LogOut from "@lucide/svelte/icons/log-out";
import Shield from "@lucide/svelte/icons/shield";
import Trash2 from "@lucide/svelte/icons/trash-2";
import { resolve } from "$app/paths";
import { getAppState } from "../utils/app-state.svelte";
import { getAuthState } from "../utils/auth.svelte";
import { getPwaState } from "../utils/pwa.svelte";
import Avatar from "./avatar.svelte";
import ConfirmDialog from "./confirm-dialog.svelte";
import GoogleMark from "./google-mark.svelte";
import StatsWindow from "./stats-window.svelte";
import ThemeSegmented from "./theme-segmented.svelte";

let { onDone }: { onDone: () => void } = $props();

const row =
  "flex items-center gap-2.5 h-10 px-2 rounded-[10px] text-sm text-text hover:bg-surface-hover transition-colors";
const dialogButton =
  "h-8 px-3 rounded-lg text-sm text-text hover:bg-surface-hover transition-colors";
const dialogDangerButton =
  "h-8 px-3 rounded-lg text-sm font-medium bg-danger text-on-danger hover:opacity-90 transition-opacity";

const app = getAppState();
const auth = getAuthState();
const pwa = getPwaState();

let confirming = $state(false);
let unsynced = $state(false);
let showInstallSteps = $state(false);
let admin = $state(false);
let statsOpen = $state(false);

$effect(() => {
  const { user } = auth;
  admin = false;
  if (!user) return;
  let live = true;
  user
    .getIdTokenResult()
    .then((result) => {
      if (live) admin = result.claims.admin === true;
    })
    .catch((e) => console.error("id token read failed", e));
  return () => {
    live = false;
  };
});
</script>

<div class="flex flex-col gap-3 p-3">
  {#if auth.user}
    <div class="flex items-center gap-3 px-1">
      <Avatar user={auth.user} size="w-9 h-9" />
      <div class="min-w-0">
        <div class="text-sm font-medium truncate">
          {auth.user.displayName ?? auth.user.email}
        </div>
        <div class="text-xs text-text-secondary truncate">
          {auth.user.email}
        </div>
      </div>
    </div>
  {:else}
    <div class="px-1">
      <div class="text-sm font-medium">Sync across devices</div>
      <div class="text-xs text-text-secondary mt-0.5">
        Sign in to keep your todos on every device.
      </div>
    </div>
  {/if}
  <ThemeSegmented />
  {#if pwa.install !== null}
    <div class="flex flex-col">
      <button
        type="button"
        onclick={() => {
          if (pwa.install === "prompt") void pwa.promptInstall();
          else showInstallSteps = !showInstallSteps;
        }}
        aria-expanded={pwa.install === "ios" ? showInstallSteps : undefined}
        class={row}
      >
        <Download class="w-4 h-4 text-text-secondary" />
        Install app
      </button>
      {#if showInstallSteps}
        <p class="m-0 px-2 pb-1 text-xs text-text-secondary">
          Tap Share, then Add to Home Screen.
        </p>
      {/if}
    </div>
  {/if}
  <a href={resolve("privacy/")} class={row}>
    <Shield class="w-4 h-4 text-text-secondary" />
    Privacy
  </a>
  {#if auth.user}
    <button
      type="button"
      role="switch"
      aria-checked={app.snoozePickLog}
      onclick={() => app.setSnoozePickLog(!app.snoozePickLog)}
      class="flex items-center gap-3 px-2 py-2 rounded-[10px] text-left hover:bg-surface-hover transition-colors"
    >
      <span class="flex-1 min-w-0 text-sm text-text">
        Improve snooze suggestions
      </span>
      <span
        aria-hidden="true"
        class={[
          "relative w-9 h-5 shrink-0 rounded-full transition-colors",
          app.snoozePickLog ? "bg-accent" : "bg-border",
        ]}
      >
        <span
          class={[
            "absolute top-0.5 left-0.5 w-4 h-4 rounded-full bg-surface shadow-pop transition-transform",
            app.snoozePickLog && "translate-x-4",
          ]}
        ></span>
      </span>
    </button>
    {#if admin}
      <button
        type="button"
        onclick={() => {
          statsOpen = true;
        }}
        class={row}
      >
        <ChartColumn class="w-4 h-4 text-text-secondary" />
        Stats
      </button>
      {#if statsOpen}
        <StatsWindow
          onClose={() => {
            statsOpen = false;
          }}
        />
      {/if}
    {/if}
    <div class="flex flex-col">
      <button
        type="button"
        onclick={async () => {
          if ((await auth.signOut(false)) === "pending") unsynced = true;
          else onDone();
        }}
        class={row}
      >
        <LogOut class="w-4 h-4 text-text-secondary" />
        Sign out
      </button>
      <button
        type="button"
        onclick={() => {
          confirming = true;
        }}
        class="flex items-center gap-2.5 h-10 px-2 rounded-[10px] text-sm text-danger hover:bg-danger-soft transition-colors"
      >
        <Trash2 class="w-4 h-4" />
        Delete account
      </button>
    </div>
  {:else}
    <button
      type="button"
      onclick={async () => {
        await auth.signIn();
        onDone();
      }}
      class="flex items-center justify-center gap-2 h-10 rounded-[10px] bg-surface border border-border text-sm font-medium text-text hover:bg-surface-hover transition-colors"
    >
      <GoogleMark />
      Continue with Google
    </button>
  {/if}
  {#if confirming}
    {@const cancel = () => {
      confirming = false;
    }}
    {@const confirm = async (keep: boolean) => {
      confirming = false;
      await auth.deleteAccount(keep);
      onDone();
    }}
    <ConfirmDialog
      id="delete-account-title"
      title="Delete your account?"
      body="This deletes your account and everything synced to it."
      onCancel={cancel}
    >
      <button type="button" onclick={cancel} class={dialogButton}>
        Cancel
      </button>
      <button type="button" onclick={() => confirm(true)} class={dialogButton}>
        Keep todos on this device
      </button>
      <button
        type="button"
        onclick={() => confirm(false)}
        class={dialogDangerButton}
      >
        Delete everything
      </button>
    </ConfirmDialog>
  {/if}
  {#if unsynced}
    {@const cancel = () => {
      unsynced = false;
    }}
    <ConfirmDialog
      id="sign-out-title"
      title="Changes haven't synced"
      body="Some changes on this device haven't reached your account yet. Signing out now drops them."
      onCancel={cancel}
    >
      <button type="button" onclick={cancel} class={dialogButton}>Wait</button>
      <button
        type="button"
        onclick={async () => {
          unsynced = false;
          await auth.signOut(true);
          onDone();
        }}
        class={dialogDangerButton}
      >
        Sign out now
      </button>
    </ConfirmDialog>
  {/if}
</div>
