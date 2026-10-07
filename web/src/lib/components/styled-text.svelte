<!-- @component Renders `*bold*` / `_italic_` with the markers kept, dimmed. -->
<script lang="ts">
import { type StyledNode, styledTree } from "../utils/inline-style";

let { text }: { text: string } = $props();
</script>

<!-- Spans with the editor's classes, not <strong>/<em>, so a row looks the same at rest and while editing. -->
{#snippet render(
  nodes: StyledNode[],
)}
  {#each nodes as node, index (index)}
    {#if node.kind === "text"}
      {node.text}
    {:else if node.kind === "marker"}
      <span class="md-marker">{node.text}</span>
    {:else}
      <span class={node.style === "bold" ? "md-bold" : "md-italic"}
        >{@render render(node.children)}</span
      >
    {/if}
  {/each}
{/snippet}
{@render render(styledTree(text))}
