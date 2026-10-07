<script lang="ts">
let { key, tint }: { key: string; tint?: string } = $props();
</script>

<kbd
  class={[
    "absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 min-w-4 h-4 px-1 flex items-center justify-center text-[10px] font-sans font-semibold leading-none rounded-[5px] bg-surface border border-border pointer-events-none",
    tint,
  ]}
>
  {key}
</kbd>
