"use client";

import {
  createContext,
  type ReactElement,
  type ReactNode,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";

// Chrome's install event; not in the DOM typings.
type InstallPromptEvent = Event & { prompt: () => Promise<void> };

type PwaValue = {
  updateReady: boolean;
  applyUpdate: () => void;
  dismissUpdate: () => void;
  // "prompt": the browser can install on request; "ios": Safari has no prompt, only Share → Add to Home Screen.
  install: "prompt" | "ios" | null;
  promptInstall: () => Promise<void>;
};

const PwaCtx = createContext<PwaValue | null>(null);

export function usePwa(): PwaValue {
  const ctx = useContext(PwaCtx);
  if (!ctx) throw new Error("usePwa must be used inside <PwaProvider>");
  return ctx;
}

export function PwaProvider({
  children,
}: {
  children: ReactNode;
}): ReactElement {
  const [waiting, setWaiting] = useState<ServiceWorker | null>(null);
  const [dismissed, setDismissed] = useState<ServiceWorker | null>(null);
  const [installEvent, setInstallEvent] = useState<InstallPromptEvent | null>(
    null,
  );
  const [ios, setIos] = useState(false);

  useEffect(() => {
    if (process.env.NODE_ENV !== "production") return;
    if (!("serviceWorker" in navigator)) return;
    const basePath = process.env.NEXT_PUBLIC_BASE_PATH ?? "";
    let registration: ServiceWorkerRegistration | null = null;

    function track(worker: ServiceWorker | null) {
      // Without a controller this is the first install, which needs no reload.
      if (!worker || !navigator.serviceWorker.controller) return;
      if (worker.state === "installed") {
        setWaiting(worker);
      } else {
        worker.addEventListener("statechange", () => {
          if (worker.state === "installed") setWaiting(worker);
        });
      }
    }
    function check() {
      if (document.visibilityState === "visible") registration?.update();
    }

    navigator.serviceWorker
      .register(`${basePath}/sw.js`, { scope: `${basePath}/` })
      .then((reg) => {
        registration = reg;
        track(reg.waiting ?? reg.installing);
        reg.addEventListener("updatefound", () => track(reg.installing));
      })
      .catch((e) => console.error(e));
    document.addEventListener("visibilitychange", check);
    return () => document.removeEventListener("visibilitychange", check);
  }, []);

  useEffect(() => {
    const nav = navigator as Navigator & { standalone?: boolean };
    setIos(nav.standalone === false);
    function onPrompt(e: Event) {
      e.preventDefault();
      setInstallEvent(e as InstallPromptEvent);
    }
    function onInstalled() {
      setInstallEvent(null);
    }
    window.addEventListener("beforeinstallprompt", onPrompt);
    window.addEventListener("appinstalled", onInstalled);
    return () => {
      window.removeEventListener("beforeinstallprompt", onPrompt);
      window.removeEventListener("appinstalled", onInstalled);
    };
  }, []);

  const value = useMemo<PwaValue>(
    () => ({
      updateReady: waiting !== null && waiting !== dismissed,
      applyUpdate() {
        if (!waiting) return;
        navigator.serviceWorker.addEventListener("controllerchange", () =>
          location.reload(),
        );
        waiting.postMessage("skipWaiting");
      },
      dismissUpdate() {
        setDismissed(waiting);
      },
      install: installEvent ? "prompt" : ios ? "ios" : null,
      async promptInstall() {
        if (!installEvent) return;
        await installEvent.prompt();
        setInstallEvent(null);
      },
    }),
    [waiting, dismissed, installEvent, ios],
  );

  return <PwaCtx.Provider value={value}>{children}</PwaCtx.Provider>;
}
