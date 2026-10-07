<script lang="ts">
const widths = ["w-3/5", "w-2/5", "w-4/5", "w-1/2", "w-2/3"];
</script>

<div class="space-y-0.5" role="status" aria-busy="true" aria-label="Loading">
  {#each widths as width (width)}
    <div class="flex items-center gap-3 min-h-10 max-md:min-h-14 px-3">
      <div
        class="w-[18px] h-[18px] rounded-full bg-surface-hover animate-shimmer"
      ></div>
      <div
        class={["h-3 rounded-full bg-surface-hover animate-shimmer", width]}
      ></div>
    </div>
  {/each}
</div>
