"use client";

import { type ReactElement, type ReactNode, useEffect, useRef } from "react";

/** A bottom sheet on a native modal <dialog>: top layer, backdrop, Esc and focus trap for free. */
export default function Sheet({
  label,
  onClose,
  children,
}: {
  label: string;
  onClose: () => void;
  children: ReactNode;
}): ReactElement {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    dialog.showModal();
    return () => dialog.close();
  }, []);

  return (
    <dialog
      ref={ref}
      aria-label={label}
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
      onKeyDown={(e) => e.stopPropagation()}
      className="
        m-0 mt-auto p-0 w-full max-w-none max-h-[85dvh] overflow-y-auto overscroll-contain
        bg-surface-raised text-text rounded-t-[28px] shadow-sheet
        animate-sheet
      "
    >
      <div className="pb-[max(env(safe-area-inset-bottom),16px)]">
        <div className="flex justify-center pt-3 pb-1" aria-hidden="true">
          <div className="w-8 h-1 rounded-full bg-border" />
        </div>
        {children}
      </div>
    </dialog>
  );
}
