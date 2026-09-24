"use client";

import type { ReactElement } from "react";
import { isMacPlatform } from "../utils/kbd-modifier";
import { usePwa } from "../utils/pwa";
import { UNDO_MS, useTodos } from "../utils/store";

const SNACKBAR = `
        fixed z-30 left-1/2 -translate-x-1/2
        bottom-[calc(env(safe-area-inset-bottom)+88px)] md:bottom-7
        md:left-[calc(50%+var(--sidebar-reserved)/2)]
        flex flex-col overflow-hidden rounded-full
        bg-inverse text-on-inverse shadow-dock animate-rise
`;

export default function UndoSnackbar(): ReactElement | null {
  const { lastUndo, undoExpiresAt, undo } = useTodos();
  const { updateReady, applyUpdate } = usePwa();

  if (!lastUndo || !undoExpiresAt) {
    if (updateReady) {
      return (
        <div role="status" className={SNACKBAR}>
          <div className="flex items-center gap-3.5 h-10 pl-[18px] pr-2 text-[13.5px]">
            <span>New version</span>
            <button
              type="button"
              onClick={applyUpdate}
              className="px-2 py-1.5 rounded-full font-semibold text-inverse-accent hover:bg-inverse-hover"
            >
              Reload
            </button>
          </div>
        </div>
      );
    } else {
      return null;
    }
  }

  let label: string;
  if (lastUndo.kind === "snooze") label = "Snoozed";
  else if (lastUndo.kind === "complete") label = "Completed";
  else if (lastUndo.todos.length === 1) label = "Deleted";
  else label = `Deleted ${lastUndo.todos.length} todos`;

  return (
    <div role="status" className={SNACKBAR}>
      <div className="flex items-center gap-3.5 h-10 pl-[18px] pr-2 text-[13.5px]">
        <span>{label}</span>
        <button
          type="button"
          onClick={undo}
          className="px-2 py-1.5 rounded-full font-semibold text-inverse-accent hover:bg-inverse-hover"
        >
          Undo
        </button>
        <kbd className="hidden md:inline mr-2 px-1.5 rounded-md bg-inverse-hover font-sans text-[11.5px] font-medium opacity-80">
          {isMacPlatform() ? "⌘Z" : "Ctrl+Z"}
        </kbd>
      </div>
      <div
        key={undoExpiresAt}
        className="h-0.5 bg-inverse-accent origin-left"
        style={{ animation: `undo-drain ${UNDO_MS}ms linear forwards` }}
      />
    </div>
  );
}
