<!-- @component One column per pick position: 0 is "none", 1..20 the rank; every bar carries its count. -->
<script lang="ts">
let { counts }: { counts: number[] } = $props();

const max = $derived(Math.max(1, ...counts));
</script>

<div class="flex flex-col">
  <div class="flex items-end gap-[2px] h-40">
    {#each counts as count, position (position)}
      <div class="flex-1 min-w-0 h-full flex flex-col items-center justify-end">
        <span
          class="mb-1 text-[10px] leading-none tabular-nums text-text-secondary whitespace-nowrap"
        >
          {count}
        </span>
        <div
          class="w-full max-w-6 rounded-t-[4px] bg-accent"
          style:height="{(Math.max(0, count) / max) * 100}%"
        ></div>
      </div>
    {/each}
  </div>
  <div class="flex gap-[2px] pt-1.5 border-t border-border">
    {#each counts as _, position (position)}
      <span
        class="flex-1 min-w-0 text-center text-[10px] leading-none tabular-nums text-muted"
      >
        {position}
      </span>
    {/each}
  </div>
</div>
