"use client";

import { type ReactElement, type RefObject, useEffect, useState } from "react";
import { isEditableTarget } from "../utils/keyboard";
import { useTodos } from "../utils/store";
import TextEditor, { type TextEditorHandle } from "./text-editor";

export default function ComposeRow({
  inputRef,
  onCreated,
}: {
  inputRef: RefObject<TextEditorHandle | null>;
  onCreated: () => void;
}): ReactElement {
  const { create, setFocus, setFilter, setSearch } = useTodos();
  const [draft, setDraft] = useState("");
  const [focused, setFocused] = useState(false);

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key !== "n") return;
      if (isEditableTarget(e.target)) return;
      e.preventDefault();
      inputRef.current?.focus();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [inputRef]);

  function submit() {
    const text = draft.trim();
    if (text.length === 0) {
      inputRef.current?.blur();
      return;
    }
    create(text);
    setFocus(null);
    setFilter("ACTIVE");
    setSearch("");
    onCreated();
    setDraft("");
    // Keep focus on the compose input for fast-compose loop.
    requestAnimationFrame(() => inputRef.current?.focus());
  }

  // On phones the row stays mounted but collapsed until the FAB focuses it, since iOS only raises the keyboard for a focus() inside the tap.
  const collapsed = !focused && draft.length === 0;

  return (
    <div
      className={`
        group/compose flex items-center gap-3 px-3 rounded-[10px] transition-colors
        min-h-10
        hover:bg-surface-hover focus-within:bg-surface focus-within:ring-1 focus-within:ring-accent
        ${collapsed ? "max-md:min-h-0 max-md:h-0 max-md:overflow-hidden max-md:opacity-0" : "max-md:min-h-14"}
      `}
    >
      <svg
        viewBox="0 0 24 24"
        className="w-[18px] h-[18px] shrink-0 text-muted"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeDasharray="3 3"
        aria-hidden="true"
      >
        <circle cx="12" cy="12" r="8.5" />
      </svg>
      <TextEditor
        handleRef={inputRef}
        value={draft}
        onChange={setDraft}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        onEnter={submit}
        enterKeyHint={draft.trim().length === 0 ? "done" : "next"}
        placeholder="Add a todo…"
        ariaLabel="Add a todo"
        className="flex-1 min-w-0 py-1 text-[15px] leading-[22px] max-md:text-[16px] max-md:leading-6 text-text"
      />
      <kbd className="hidden md:inline text-[11.5px] font-medium px-1.5 rounded-md bg-surface border border-border text-text-secondary font-sans group-focus-within/compose:hidden">
        n
      </kbd>
      <kbd className="hidden text-[11.5px] font-medium px-1.5 rounded-md bg-surface border border-border text-text-secondary font-sans group-focus-within/compose:md:inline">
        ↵
      </kbd>
    </div>
  );
}
