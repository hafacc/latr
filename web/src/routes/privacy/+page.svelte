<script lang="ts">
import type { Snippet } from "svelte";
</script>

<svelte:head>
  <title>Privacy · Latr</title>
</svelte:head>

{#snippet section(
  title: string,
  body: Snippet,
)}
  <section class="mt-7">
    <h2 class="m-0 text-base font-semibold text-text">{title}</h2>
    <p class="mt-1.5 mb-0">{@render body()}</p>
  </section>
{/snippet}

<main
  class="mx-auto max-w-[620px] px-5 py-12 text-[15px] leading-relaxed text-text-secondary"
>
  <a href="../" class="text-sm text-accent hover:underline">Latr</a>
  <h1 class="mt-3 mb-0 text-2xl font-semibold text-text">Privacy</h1>
  {#snippet signedOut()}
    Your todos stay on your device. Nothing is sent anywhere.
  {/snippet}
  {@render section("Not signed in", signedOut)}
  {#snippet signedIn()}
    Your todos and a count of the snooze times you pick are stored in Google
    Firebase under your Google account, so they sync between your devices.
    Google gives Latr your name, email and profile photo, shown in the account
    menu. Other users can't read your data. Signing out removes it from the
    device you sign out on.
  {/snippet}
  {@render section("Signed in with Google", signedIn)}
  {#snippet tally()}
    Off unless you turn it on. Each snooze then adds one to a shared tally of
    which menu position was picked. It holds no todo text, no times, and nothing
    that identifies you.
  {/snippet}
  {@render section("Improve snooze suggestions", tally)}
  {#snippet doesnt()}
    No ads, no analytics, no selling or sharing of data. Google handles sign-in
    and storage.
  {/snippet}
  {@render section("What Latr doesn't do", doesnt)}
  {#snippet deleting()}
    Use the
    <a href="../delete-account/" class="text-accent hover:underline"
      >delete account page</a
    >, or open Latr on the web or on Android, open the account menu, and choose
    Delete account. Your todos, snooze counts and account are removed right
    away.
  {/snippet}
  {@render section("Delete your account", deleting)}
  <p class="mt-7 mb-0">Deleting a single todo erases its text.</p>
</main>
