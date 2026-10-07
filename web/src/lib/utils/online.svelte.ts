import { createSubscriber } from "svelte/reactivity";

const subscribe = createSubscriber((update) => {
  window.addEventListener("online", update);
  window.addEventListener("offline", update);
  return () => {
    window.removeEventListener("online", update);
    window.removeEventListener("offline", update);
  };
});

/** Live navigator.onLine (events are just change signals). */
export function isOnline(): boolean {
  subscribe();
  return navigator.onLine;
}
