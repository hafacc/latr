<!-- @component Phone app bar: avatar that opens the account sheet. -->
<script lang="ts">
import { getAuthState } from "../utils/auth.svelte";
import AccountPanel from "./account-panel.svelte";
import Avatar from "./avatar.svelte";
import Sheet from "./sheet.svelte";

const auth = getAuthState();
let open = $state(false);

function close() {
  open = false;
}
</script>

<button
  type="button"
  onclick={() => {
    open = true;
  }}
  aria-label="Account"
  class="w-10 h-10 flex items-center justify-center rounded-full hover:bg-surface-hover transition-colors"
>
  <Avatar user={auth.user} size="w-8 h-8" />
</button>
{#if open}
  <Sheet label="Account" onClose={close}>
    <AccountPanel onDone={close} />
  </Sheet>
{/if}
