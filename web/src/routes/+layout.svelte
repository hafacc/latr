<script lang="ts">
import "@fontsource-variable/geist/wght.css";
import "../app.css";
import type { Snippet } from "svelte";
import { AppState, setAppState } from "../lib/utils/app-state.svelte";
import {
  ModifierState,
  setModifierState,
} from "../lib/utils/kbd-modifier.svelte";
import { PwaState, setPwaState } from "../lib/utils/pwa.svelte";
import { setThemeState, ThemeState } from "../lib/utils/theme.svelte";

let { children }: { children: Snippet } = $props();

setThemeState(new ThemeState());
setAppState(new AppState());
setModifierState(new ModifierState());
setPwaState(new PwaState());
</script>

{@render children()}
