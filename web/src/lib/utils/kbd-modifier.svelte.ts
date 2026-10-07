import { createContext } from "svelte";
import { isMacPlatform } from "./keyboard";

/** Tracks whether the platform command modifier (⌘/Ctrl) is held. */
export class ModifierState {
  held = $state(false);

  constructor() {
    $effect(() => {
      const key = isMacPlatform() ? "Meta" : "Control";
      const onDown = (e: KeyboardEvent) => {
        if (e.key === key) this.held = true;
      };
      const onUp = (e: KeyboardEvent) => {
        if (e.key === key) this.held = false;
      };
      const clear = () => {
        this.held = false;
      };
      window.addEventListener("keydown", onDown);
      window.addEventListener("keyup", onUp);
      window.addEventListener("blur", clear);
      document.addEventListener("visibilitychange", clear);
      return () => {
        window.removeEventListener("keydown", onDown);
        window.removeEventListener("keyup", onUp);
        window.removeEventListener("blur", clear);
        document.removeEventListener("visibilitychange", clear);
      };
    });
  }
}

export const [getModifierState, setModifierState] =
  createContext<ModifierState>();
