"use client";

import { doc, getDoc } from "firebase/firestore";
import { type ReactElement, useEffect, useRef, useState } from "react";
import { db } from "../utils/firebase";
import { globalPickCounts } from "../utils/snooze-stats-wire";
import { DESKTOP_QUERY, useMediaQuery } from "../utils/use-media";
import Sheet from "./sheet";

/** One column per pick position: 0 is "none", 1..20 the rank; every bar carries its count. */
export function PickRankChart({ counts }: { counts: number[] }): ReactElement {
  const max = Math.max(1, ...counts);
  return (
    <div className="flex flex-col">
      <div className="flex items-end gap-[2px] h-40">
        {counts.map((count, position) => (
          <div
            // biome-ignore lint/suspicious/noArrayIndexKey: positions are fixed
            key={position}
            className="flex-1 min-w-0 h-full flex flex-col items-center justify-end"
          >
            <span className="mb-1 text-[10px] leading-none tabular-nums text-text-secondary whitespace-nowrap">
              {count}
            </span>
            <div
              className="w-full max-w-6 rounded-t-[4px] bg-accent"
              style={{ height: `${(Math.max(0, count) / max) * 100}%` }}
            />
          </div>
        ))}
      </div>
      <div className="flex gap-[2px] pt-1.5 border-t border-border">
        {counts.map((_, position) => (
          <span
            // biome-ignore lint/suspicious/noArrayIndexKey: positions are fixed
            key={position}
            className="flex-1 min-w-0 text-center text-[10px] leading-none tabular-nums text-muted"
          >
            {position}
          </span>
        ))}
      </div>
    </div>
  );
}

function StatsBody(): ReactElement {
  const [counts, setCounts] = useState<number[] | null>(null);
  useEffect(() => {
    let live = true;
    getDoc(doc(db(), "snoozePickLog", "global"))
      .then((snap) => {
        if (live) setCounts(globalPickCounts(snap.data()));
      })
      .catch((e) => console.error("stats read failed", e));
    return () => {
      live = false;
    };
  }, []);
  return (
    <div className="p-5">
      <h2 id="stats-title" className="m-0 mb-4 text-base font-semibold">
        Stats
      </h2>
      {counts ? <PickRankChart counts={counts} /> : <div className="h-44" />}
    </div>
  );
}

/** Desktop: a centered modal dialog; phones: a bottom sheet. */
export default function StatsWindow({
  onClose,
}: {
  onClose: () => void;
}): ReactElement {
  const desktop = useMediaQuery(DESKTOP_QUERY);
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    if (!desktop) return;
    const dialog = ref.current;
    dialog?.showModal();
    return () => dialog?.close();
  }, [desktop]);

  if (desktop) {
    return (
      <dialog
        ref={ref}
        aria-labelledby="stats-title"
        onCancel={(e) => {
          e.preventDefault();
          onClose();
        }}
        onClick={(e) => {
          if (e.target === e.currentTarget) onClose();
        }}
        onKeyDown={(e) => e.stopPropagation()}
        className="m-auto p-0 w-[min(560px,calc(100vw-32px))] rounded-[14px] bg-surface-raised text-text shadow-pop animate-rise"
      >
        <StatsBody />
      </dialog>
    );
  } else {
    return (
      <Sheet label="Stats" onClose={onClose}>
        <StatsBody />
      </Sheet>
    );
  }
}
