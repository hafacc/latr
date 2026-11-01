<!-- @component An info page's sections, shared by its own page and the in-app window. -->
<script lang="ts">
import { resolve } from "$app/paths";
import { type InfoPage, textParts } from "../utils/info-pages";

let { page }: { page: InfoPage } = $props();
</script>

{#each page.sections as section (section.heading)}
  <section class="mt-7 first:mt-0">
    <h2 class="m-0 text-base font-semibold text-text">{section.heading}</h2>
    <p class="mt-1.5 mb-0">
      {#each textParts(section.body) as part, index (index)}
        {#if part.href}
          <a
            href={`${resolve("/")}${part.href}`}
            class="text-accent hover:underline"
            >{part.text}</a
          >
        {:else}
          {part.text}
        {/if}
      {/each}
    </p>
  </section>
{/each}
<p class="mt-7 mb-0">{page.closing}</p>
