<script lang="ts">
import InfoText from "../../lib/components/info-text.svelte";
import { PRIVACY } from "../../lib/utils/info-pages";
</script>

<svelte:head>
  <title>{PRIVACY.title} · Latr</title>
</svelte:head>

<main
  class="mx-auto max-w-[620px] px-5 py-12 text-[15px] leading-relaxed text-text-secondary"
>
  <a href="../" class="text-sm text-accent hover:underline">Latr</a>
  <h1 class="mt-3 mb-7 text-2xl font-semibold text-text">{PRIVACY.title}</h1>
  <InfoText page={PRIVACY} />
</main>
