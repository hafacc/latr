import { createContext } from "svelte";
import { dev } from "$app/env";
import { resolve } from "$app/paths";

// Chrome's install event; not in the DOM typings.
type InstallPromptEvent = Event & { prompt: () => Promise<void> };

/** The offline worker's pending update and whether the browser can install the app. */
export class PwaState {
  #waiting = $state.raw<ServiceWorker | null>(null);
  #dismissed = $state.raw<ServiceWorker | null>(null);
  #installEvent = $state.raw<InstallPromptEvent | null>(null);
  #ios = $state(false);

  readonly updateReady = $derived(
    this.#waiting !== null && this.#waiting !== this.#dismissed,
  );
  // "prompt": the browser can install on request; "ios": Safari has no prompt, only Share → Add to Home Screen.
  readonly install: "prompt" | "ios" | null = $derived(
    this.#installEvent ? "prompt" : this.#ios ? "ios" : null,
  );

  constructor() {
    $effect(() => {
      if (dev) return;
      if (!("serviceWorker" in navigator)) return;
      let registration: ServiceWorkerRegistration | null = null;

      const track = (worker: ServiceWorker | null) => {
        // Without a controller this is the first install, which needs no reload.
        if (!worker || !navigator.serviceWorker.controller) return;
        if (worker.state === "installed") {
          this.#waiting = worker;
        } else {
          worker.addEventListener("statechange", () => {
            if (worker.state === "installed") this.#waiting = worker;
          });
        }
      };
      const check = () => {
        if (document.visibilityState === "visible") registration?.update();
      };

      navigator.serviceWorker
        .register(`${resolve("/")}sw.js`, { scope: resolve("/") })
        .then((reg) => {
          registration = reg;
          track(reg.waiting ?? reg.installing);
          reg.addEventListener("updatefound", () => track(reg.installing));
        })
        .catch((e) => console.error(e));
      document.addEventListener("visibilitychange", check);
      return () => document.removeEventListener("visibilitychange", check);
    });

    $effect(() => {
      const nav = navigator as Navigator & { standalone?: boolean };
      this.#ios = nav.standalone === false;
      const onPrompt = (e: Event) => {
        e.preventDefault();
        this.#installEvent = e as InstallPromptEvent;
      };
      const onInstalled = () => {
        this.#installEvent = null;
      };
      window.addEventListener("beforeinstallprompt", onPrompt);
      window.addEventListener("appinstalled", onInstalled);
      return () => {
        window.removeEventListener("beforeinstallprompt", onPrompt);
        window.removeEventListener("appinstalled", onInstalled);
      };
    });
  }

  applyUpdate = (): void => {
    const waiting = this.#waiting;
    if (!waiting) return;
    navigator.serviceWorker.addEventListener("controllerchange", () =>
      location.reload(),
    );
    waiting.postMessage("skipWaiting");
  };

  dismissUpdate = (): void => {
    this.#dismissed = this.#waiting;
  };

  promptInstall = async (): Promise<void> => {
    const event = this.#installEvent;
    if (!event) return;
    await event.prompt();
    this.#installEvent = null;
  };
}

export const [getPwaState, setPwaState] = createContext<PwaState>();
