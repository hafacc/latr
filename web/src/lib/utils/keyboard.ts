export function isMacPlatform(): boolean {
  return typeof navigator !== "undefined" && /mac/i.test(navigator.platform);
}

/** ⌘ on Mac, Ctrl elsewhere, and not the other one, so Mac's Ctrl text bindings stay text bindings. */
export function isCommandChord(e: KeyboardEvent): boolean {
  if (isMacPlatform()) return e.metaKey && !e.ctrlKey;
  else return e.ctrlKey && !e.metaKey;
}

export function hasModifier(e: KeyboardEvent): boolean {
  return e.metaKey || e.ctrlKey || e.altKey;
}

export function isEditableTarget(target: EventTarget | null): boolean {
  if (!target) return false;
  const el = target as HTMLElement;
  return (
    el.tagName === "INPUT" || el.tagName === "TEXTAREA" || el.isContentEditable
  );
}
