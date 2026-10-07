<script lang="ts">
import UserIcon from "@lucide/svelte/icons/user";
import type { User } from "firebase/auth";

let { user, size }: { user: User | null; size: string } = $props();
</script>

{#if user?.photoURL}
  <img
    src={user.photoURL}
    alt=""
    class={[size, "rounded-full shrink-0"]}
    referrerpolicy="no-referrer"
  >
{:else}
  <span
    class={[
      size,
      "rounded-full shrink-0 bg-accent-soft text-accent flex items-center justify-center",
    ]}
  >
    <UserIcon class="w-1/2 h-1/2" aria-hidden="true" />
  </span>
{/if}
