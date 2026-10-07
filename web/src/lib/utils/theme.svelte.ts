import { createContext } from "svelte";
import { MediaQuery } from "svelte/reactivity";

export type ThemeMode = "system" | "light" | "dark";

/** The chosen theme, saved in localStorage and applied as a class on the root element. */
export class ThemeState {
  mode = $state<ThemeMode>("system");
  #systemDark = new MediaQuery("(prefers-color-scheme: dark)", false);

  constructor() {
    $effect(() => {
      const read = () => {
        const stored = window.localStorage.getItem("theme");
        if (stored === "dark" || stored === "light") {
          this.mode = stored;
        } else {
          window.localStorage.removeItem("theme");
          this.mode = "system";
        }
      };
      read();
      window.addEventListener("storage", read);
      return () => window.removeEventListener("storage", read);
    });

    $effect(() => {
      const dark =
        this.mode === "system"
          ? this.#systemDark.current
          : this.mode === "dark";
      document.documentElement.classList.toggle("dark", dark);
      for (const meta of document.querySelectorAll(
        'meta[name="theme-color"]',
      )) {
        meta.setAttribute("content", dark ? "#171615" : "#fbfaf8");
      }
    });
  }

  setMode = (mode: ThemeMode): void => {
    this.mode = mode;
    if (mode === "system") window.localStorage.removeItem("theme");
    else window.localStorage.setItem("theme", mode);
  };
}

export const [getThemeState, setThemeState] = createContext<ThemeState>();
