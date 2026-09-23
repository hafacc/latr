"use client";

import { type ReactElement, useEffect, useRef, useState } from "react";
import {
  formatClock,
  type QuickTime,
  quickTimeEpoch,
  type Row,
  type SnoozeSource,
} from "../utils/snooze-suggest";

const MENU_CLOCK_TICK_MS = 30_000;

function pad(n: number): string {
  return String(n).padStart(2, "0");
}

function formatDate(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function formatTime(minutes: number): string {
  return `${pad(Math.floor(minutes / 60))}:${pad(minutes % 60)}`;
}

function parseTime(hhmm: string): number | null {
  const parts = hhmm.split(":");
  if (parts.length !== 2) return null;
  const h = Number.parseInt(parts[0], 10);
  const m = Number.parseInt(parts[1], 10);
  if (Number.isNaN(h) || Number.isNaN(m)) return null;
  return h * 60 + m;
}

function clockLabel(clockMinutes: number): string {
  return formatClock(
    new Date(
      2000,
      0,
      1,
      Math.floor(clockMinutes / 60),
      clockMinutes % 60,
    ).getTime(),
  );
}

/** The strongest quick time if it's still ahead on `dateYmd`, else the next quarter hour after now. */
function defaultTime(dateYmd: string, quickTimes: QuickTime[]): string {
  let best: QuickTime | null = null;
  for (const q of quickTimes) if (!best || q.weight > best.weight) best = q;
  if (best) {
    const epoch = quickTimeEpoch(dateYmd, best.clockMinutes);
    if (epoch !== null && epoch > Date.now()) {
      return formatTime(best.clockMinutes);
    }
  }
  const current = new Date();
  const minutes = current.getHours() * 60 + current.getMinutes();
  return formatTime((Math.floor(minutes / 15) * 15 + 15) % 1440);
}

export default function SnoozeMenu({
  rows,
  lastRow,
  quickTimes,
  now,
  refreshNow,
  onPick,
  onClose,
}: {
  rows: Row[];
  lastRow: Pick<Row, "time" | "label"> | null;
  quickTimes: QuickTime[];
  now: number;
  refreshNow: () => void;
  onPick: (
    epochMillis: number,
    source: SnoozeSource,
    pickedKey?: string,
  ) => void;
  onClose: () => void;
}): ReactElement {
  const rootRef = useRef<HTMLDivElement>(null);
  const [customOpen, setCustomOpen] = useState(false);
  const [customDate, setCustomDate] = useState(() => formatDate(new Date()));
  const [customTime, setCustomTime] = useState(() =>
    defaultTime(customDate, quickTimes),
  );

  useEffect(() => {
    refreshNow();
    const id = setInterval(refreshNow, MENU_CLOCK_TICK_MS);
    return () => clearInterval(id);
  }, [refreshNow]);

  useEffect(() => {
    function onDocClick(e: MouseEvent) {
      if (!rootRef.current) return;
      if (!rootRef.current.contains(e.target as Node)) onClose();
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    document.addEventListener("mousedown", onDocClick);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDocClick);
      document.removeEventListener("keydown", onKey);
    };
  }, [onClose]);

  function pickedEpoch(): number | null {
    const d = new Date(`${customDate}T00:00:00`);
    if (Number.isNaN(d.getTime())) return null;
    const minutes = parseTime(customTime);
    if (minutes === null) return null;
    d.setHours(Math.floor(minutes / 60), minutes % 60, 0, 0);
    const epoch = d.getTime();
    if (epoch <= now) return null;
    return epoch;
  }

  const customEpoch = pickedEpoch();
  const minDate = formatDate(new Date());

  return (
    <div
      ref={rootRef}
      className="absolute z-30 right-0 mt-2 w-max min-w-64 max-w-[calc(100vw-2rem)] rounded-xl bg-surface border border-border shadow-xl overflow-hidden p-1"
      onClick={(e) => e.stopPropagation()}
      onKeyDown={(e) => e.stopPropagation()}
      role="menu"
    >
      {rows.map((row) => (
        <button
          key={`${row.label}-${row.time}`}
          type="button"
          onClick={() => onPick(row.time, "suggestion", row.keyId)}
          className="w-full flex items-center px-3 py-2 rounded-lg text-sm text-left text-text hover:bg-surface-hover transition-colors"
        >
          <span className="whitespace-normal text-left">{row.label}</span>
        </button>
      ))}
      {lastRow && (
        <button
          type="button"
          onClick={() => onPick(lastRow.time, "last")}
          className="w-full flex items-center px-3 py-2 rounded-lg text-sm text-left text-text hover:bg-surface-hover transition-colors"
        >
          <span className="whitespace-normal text-left">{lastRow.label}</span>
        </button>
      )}
      <div
        className={
          rows.length > 0 || lastRow ? "mt-1 pt-1 border-t border-border" : ""
        }
      >
        {customOpen ? (
          <div className="p-2 space-y-2">
            <input
              type="date"
              value={customDate}
              min={minDate}
              onChange={(e) => setCustomDate(e.target.value)}
              className="w-full h-8 text-sm bg-surface-muted rounded-lg px-3 text-text outline-none"
            />
            {quickTimes.length > 0 && (
              <div className="flex flex-wrap gap-1">
                {quickTimes.map((q) => {
                  const epoch = quickTimeEpoch(customDate, q.clockMinutes);
                  return (
                    <QuickTimeButton
                      key={q.clockMinutes}
                      label={clockLabel(q.clockMinutes)}
                      disabled={epoch === null || epoch <= now}
                      onClick={() => {
                        if (epoch === null || epoch <= Date.now()) {
                          refreshNow();
                          return;
                        }
                        onPick(epoch, "custom");
                      }}
                    />
                  );
                })}
              </div>
            )}
            <div className="flex gap-1">
              <input
                type="time"
                value={customTime}
                onChange={(e) => setCustomTime(e.target.value)}
                className="flex-1 min-w-0 h-8 text-sm bg-surface-muted rounded-lg px-3 text-text outline-none"
              />
              <button
                type="button"
                className="h-8 text-xs px-3 rounded-lg bg-accent text-white hover:opacity-90 transition-opacity disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:opacity-40"
                disabled={customEpoch === null}
                onClick={() => {
                  if (customEpoch === null) return;
                  onPick(customEpoch, "custom");
                }}
              >
                Snooze
              </button>
            </div>
          </div>
        ) : (
          <button
            type="button"
            onClick={() => setCustomOpen(true)}
            className="w-full text-left px-3 py-2 rounded-lg text-sm text-text hover:bg-surface-hover transition-colors"
          >
            Pick a date &amp; time…
          </button>
        )}
      </div>
    </div>
  );
}

function QuickTimeButton({
  label,
  disabled,
  onClick,
}: {
  label: string;
  disabled: boolean;
  onClick: () => void;
}): ReactElement {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className="flex-1 h-8 text-xs px-2 rounded-lg bg-accent-soft text-accent font-medium hover:opacity-90 transition-opacity disabled:opacity-40 disabled:cursor-not-allowed"
    >
      {label}
    </button>
  );
}
