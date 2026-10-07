<!-- @component The whole of `/delete-account/`: sign in if needed, then delete the account from the web alone. -->
<script lang="ts">
import { onAuthStateChanged, type User } from "firebase/auth";
import { getAppState } from "../utils/app-state.svelte";
import { auth, firebaseConfigured } from "../utils/firebase";
import GoogleMark from "./google-mark.svelte";

type Stage = "idle" | "working" | "deleted" | "kept" | "failed";

const button =
  "h-10 px-4 rounded-[10px] text-sm font-medium transition-colors disabled:opacity-50";

const { holder } = getAppState();
let user = $state.raw<User | null>(null);
let stage = $state<Stage>("idle");
const finished = $derived(stage === "deleted" || stage === "kept");

$effect(() => {
  if (!firebaseConfigured()) return;
  return onAuthStateChanged(auth(), (next) => {
    user = next;
  });
});

async function signIn(): Promise<void> {
  try {
    await holder.signIn();
  } catch (e) {
    console.error(e);
  }
}

async function remove(keep: boolean): Promise<void> {
  stage = "working";
  try {
    await holder.deleteAccount(keep);
    stage = keep ? "kept" : "deleted";
  } catch (e) {
    console.error(e);
    stage = "failed";
  }
}
</script>

<main
  class="mx-auto max-w-[620px] px-5 py-12 text-[15px] leading-relaxed text-text-secondary"
>
  <a href="../" class="text-sm text-accent hover:underline">Latr</a>
  <h1 class="mt-3 mb-0 text-2xl font-semibold text-text">
    Delete your Latr account
  </h1>
  {#if finished}
    <p class="mt-5 mb-0">
      Your account is deleted.
      {#if stage === "kept"}
        Your todos are still on this device.
      {/if}
    </p>
  {:else if user}
    <p class="mt-5 mb-0">
      Signed in as
      <span class="text-text">{user.email ?? user.displayName}</span>.
      <button
        type="button"
        onclick={() => void holder.signOut()}
        class="text-accent hover:underline"
      >
        Use a different account
      </button>
    </p>
    <p class="mt-4 mb-0">
      This deletes your account and everything synced to it: your todos and your
      learned snooze times. It happens right away and can't be undone.
    </p>
    {#if stage === "failed"}
      <p class="mt-4 mb-0 text-danger">
        Deleting didn't finish. Some data may already be gone. Please try again.
      </p>
    {/if}
    <div class="mt-5 flex flex-wrap gap-2">
      <button
        type="button"
        disabled={stage === "working"}
        onclick={() => void remove(true)}
        class={[
          button,
          "bg-surface border border-border text-text hover:bg-surface-hover",
        ]}
      >
        Keep todos on this device
      </button>
      <button
        type="button"
        disabled={stage === "working"}
        onclick={() => void remove(false)}
        class={[button, "bg-danger text-on-danger hover:opacity-90"]}
      >
        Delete everything
      </button>
    </div>
  {:else}
    <p class="mt-5 mb-0">
      Sign in to delete your account and everything synced to it.
    </p>
    <button
      type="button"
      onclick={() => void signIn()}
      class={[
        button,
        "mt-5 flex items-center gap-2 bg-surface border border-border text-text hover:bg-surface-hover",
      ]}
    >
      <GoogleMark />
      Continue with Google
    </button>
  {/if}
  <p class="mt-9 mb-0">
    The Android app has the same option in its account menu.
  </p>
</main>
