import { MediaQuery } from "svelte/reactivity";

/** Whether the desktop layout applies; false while prerendering. */
export const desktop = new MediaQuery("(min-width: 768px)", false);
