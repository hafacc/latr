<script lang="ts">
let {
  class: className,
  shadowClass = "fill-accent-shadow",
}: { class?: string; shadowClass?: string } = $props();

const OUTLINE = "M55.49 27.74 L63.32 29.4 L58.94 50 L70 50 L70 58 L49.06 58 Z";
</script>

<svg
  viewBox="49 27 24 34"
  fill="currentColor"
  xmlns="http://www.w3.org/2000/svg"
  aria-hidden="true"
  class={className}
>
  <path d={OUTLINE} transform="translate(2.2 2.2)" class={shadowClass} />
  <path d={OUTLINE} />
</svg>
