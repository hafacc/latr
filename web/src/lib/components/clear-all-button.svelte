<script lang="ts">
let { onClick }: { onClick: () => void } = $props();
</script>

<button
  type="button"
  onclick={onClick}
  class="h-8 px-3 rounded-lg text-sm text-text-secondary hover:text-text hover:bg-surface-hover transition-colors shrink-0"
>
  Clear all
</button>
