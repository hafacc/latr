"use client";

import {
  type ReactElement,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
} from "react";
import type { IconType } from "react-icons";
import {
  LuArrowLeft,
  LuCalendar,
  LuCalendarArrowUp,
  LuCalendarDays,
  LuCalendarPlus,
  LuChevronLeft,
  LuChevronRight,
  LuClock,
  LuHistory,
  LuMoon,
  LuSun,
  LuSunrise,
  LuTimer,
} from "react-icons/lu";
import {
  formatClockMinutes,
  monthGrid,
  monthLabel,
  nextQuarterHour,
  parseClockInput,
  parseYmd,
  toYmd,
  WEEKDAY_SHORT,
} from "../utils/calendar";
import {
  formatClock,
  type LastRow,
  type QuickTime,
  quickTimeEpoch,
  type Row,
  type RowIcon,
  type SnoozeSource,
} from "../utils/snooze-suggest";
import Sheet from "./sheet";
import StyledText from "./styled-text";

const MENU_CLOCK_TICK_MS = 30_000;

const ROW_ICONS: Record<RowIcon, IconType> = {
  offset: LuTimer,
  todayDay: LuSun,
  todayNight: LuMoon,
  tomorrow: LuSunrise,
  days: LuCalendarDays,
  weekday: LuCalendarArrowUp,
  monthly: LuCalendar,
};

type PickHandler = (
  epochMillis: number,
  source: SnoozeSource,
  pickedKey?: string,
) => void;

type MenuProps = {
  rows: Row[];
  lastRow: LastRow | null;
  quickTimes: QuickTime[];
  now: number;
  refreshNow: () => void;
  onPick: PickHandler;
  onClose: () => void;
};

/** Today at the strongest quick time if it's still ahead, else the next quarter hour after now. */
function initialPick(
  now: Date,
  quickTimes: QuickTime[],
): { ymd: string; time: string } {
  let best: QuickTime | null = null;
  for (const q of quickTimes) if (!best || q.weight > best.weight) best = q;
  const today = toYmd(now);
  if (best) {
    const epoch = quickTimeEpoch(today, best.clockMinutes);
    if (epoch !== null && epoch > now.getTime()) {
      return { ymd: today, time: formatClockMinutes(best.clockMinutes) };
    }
  }
  const next = nextQuarterHour(now);
  return { ymd: next.ymd, time: formatClockMinutes(next.minutes) };
}

function MenuRowButton({
  icon: Icon,
  tint,
  text,
  time,
  onClick,
}: {
  icon: IconType;
  tint: "snooze" | "neutral";
  text: string;
  time?: string;
  onClick: () => void;
}): ReactElement {
  return (
    <button
      type="button"
      role="menuitem"
      onClick={onClick}
      className="w-full flex items-center gap-2.5 min-h-10 max-md:min-h-12 px-2.5 rounded-[10px] text-sm text-left text-text hover:bg-surface-hover focus-visible:bg-surface-hover outline-none transition-colors"
    >
      <span
        className={`w-7 h-7 shrink-0 rounded-lg flex items-center justify-center ${
          tint === "snooze"
            ? "bg-snooze-soft text-snooze"
            : "bg-surface-muted text-text-secondary"
        }`}
      >
        <Icon className="w-4 h-4" aria-hidden="true" />
      </span>
      <span className="flex-1 min-w-0 font-medium">{text}</span>
      {time && (
        <span className="text-[13px] text-text-secondary tabular-nums shrink-0">
          {time}
        </span>
      )}
    </button>
  );
}

function CustomPicker({
  quickTimes,
  now,
  refreshNow,
  onPick,
  onBack,
}: {
  quickTimes: QuickTime[];
  now: number;
  refreshNow: () => void;
  onPick: PickHandler;
  onBack: () => void;
}): ReactElement {
  const today = new Date(now);
  const [initial] = useState(() => initialPick(today, quickTimes));
  const [selected, setSelected] = useState(initial.ymd);
  const [view, setView] = useState(() => {
    const d = parseYmd(initial.ymd);
    return { year: d.getFullYear(), month: d.getMonth() };
  });
  const [customTime, setCustomTime] = useState(initial.time);

  const grid = monthGrid(view.year, view.month, today);
  const atCurrentMonth =
    view.year === today.getFullYear() && view.month === today.getMonth();

  function shiftMonth(delta: number) {
    const d = new Date(view.year, view.month + delta, 1);
    setView({ year: d.getFullYear(), month: d.getMonth() });
  }

  const typedMinutes = parseClockInput(customTime);
  let customEpoch: number | null = null;
  if (typedMinutes !== null) {
    const d = parseYmd(selected);
    d.setHours(Math.floor(typedMinutes / 60), typedMinutes % 60, 0, 0);
    if (d.getTime() > now) customEpoch = d.getTime();
  }

  function commitTyped() {
    if (customEpoch === null || customEpoch <= Date.now()) {
      refreshNow();
      return;
    }
    onPick(customEpoch, "custom");
  }

  const navButton =
    "w-7 h-7 flex items-center justify-center rounded-lg text-text-secondary hover:bg-surface-hover disabled:text-muted disabled:hover:bg-transparent transition-colors";

  return (
    <div className="flex flex-col gap-3 p-3">
      <div className="flex items-center gap-1.5">
        <button
          type="button"
          onClick={onBack}
          aria-label="Back"
          className={navButton}
        >
          <LuArrowLeft className="w-4 h-4" />
        </button>
        <span className="flex-1 text-sm font-semibold">
          {monthLabel(view.year, view.month)}
        </span>
        <button
          type="button"
          onClick={() => shiftMonth(-1)}
          disabled={atCurrentMonth}
          aria-label="Previous month"
          className={navButton}
        >
          <LuChevronLeft className="w-4 h-4" />
        </button>
        <button
          type="button"
          onClick={() => shiftMonth(1)}
          aria-label="Next month"
          className={navButton}
        >
          <LuChevronRight className="w-4 h-4" />
        </button>
      </div>

      <div className="grid grid-cols-7 gap-0.5 text-center">
        {WEEKDAY_SHORT.map((w) => (
          <span
            key={w}
            className="text-[11.5px] font-medium text-text-secondary py-1"
          >
            {w}
          </span>
        ))}
        {grid.map((cell) => {
          const isSelected = cell.ymd === selected;
          return (
            <button
              key={cell.ymd}
              type="button"
              disabled={cell.past}
              onClick={() => setSelected(cell.ymd)}
              aria-pressed={isSelected}
              aria-label={parseYmd(cell.ymd).toDateString()}
              className={`
                h-[34px] max-md:h-10 rounded-[10px] text-[13px] tabular-nums transition-colors
                ${
                  isSelected
                    ? "bg-accent text-on-accent font-semibold"
                    : cell.past
                      ? "text-muted"
                      : cell.inMonth
                        ? "text-text hover:bg-surface-hover"
                        : "text-text-secondary hover:bg-surface-hover"
                }
                ${cell.today && !isSelected ? "ring-1 ring-inset ring-accent" : ""}
              `}
            >
              {cell.day}
            </button>
          );
        })}
      </div>

      {quickTimes.length > 0 && (
        <div className="flex gap-1.5">
          {quickTimes.map((q) => {
            const epoch = quickTimeEpoch(selected, q.clockMinutes);
            return (
              <button
                key={q.clockMinutes}
                type="button"
                disabled={epoch === null || epoch <= now}
                onClick={() => {
                  if (epoch === null || epoch <= Date.now()) {
                    refreshNow();
                    return;
                  }
                  onPick(epoch, "custom");
                }}
                className="flex-1 h-8 max-md:h-10 rounded-full bg-accent-soft text-accent-strong text-[13px] font-medium tabular-nums hover:opacity-90 transition-opacity disabled:bg-surface-muted disabled:text-muted"
              >
                {formatClockMinutes(q.clockMinutes)}
              </button>
            );
          })}
        </div>
      )}

      <div className="flex gap-2">
        <label className="flex-1 min-w-0 flex items-center gap-2 h-8 max-md:h-10 px-2.5 rounded-[10px] bg-surface-muted text-text-secondary focus-within:ring-1 focus-within:ring-accent">
          <LuClock className="w-4 h-4 shrink-0" aria-hidden="true" />
          <input
            type="text"
            inputMode="numeric"
            value={customTime}
            onChange={(e) => setCustomTime(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                commitTyped();
              }
            }}
            aria-label="Time (24-hour)"
            aria-invalid={typedMinutes === null}
            placeholder="HH:MM"
            className="flex-1 min-w-0 bg-transparent outline-none text-sm text-text tabular-nums max-md:text-[16px]"
          />
        </label>
        <button
          type="button"
          disabled={customEpoch === null}
          onClick={commitTyped}
          className="h-8 max-md:h-10 px-4 rounded-[10px] bg-accent text-on-accent text-sm font-medium hover:opacity-90 transition-opacity disabled:opacity-40 disabled:cursor-not-allowed"
        >
          Snooze
        </button>
      </div>
    </div>
  );
}

function MenuContent({
  rows,
  lastRow,
  quickTimes,
  now,
  refreshNow,
  onPick,
}: Omit<MenuProps, "onClose">): ReactElement {
  const [customOpen, setCustomOpen] = useState(false);

  useEffect(() => {
    refreshNow();
    const id = setInterval(refreshNow, MENU_CLOCK_TICK_MS);
    return () => clearInterval(id);
  }, [refreshNow]);

  if (customOpen) {
    return (
      <CustomPicker
        quickTimes={quickTimes}
        now={now}
        refreshNow={refreshNow}
        onPick={onPick}
        onBack={() => setCustomOpen(false)}
      />
    );
  }

  return (
    <div className="flex flex-col gap-0.5 p-1.5">
      <div className="px-2.5 pt-2 pb-1.5 text-[12.5px] font-semibold text-text-secondary max-md:hidden">
        Snooze until
      </div>
      {rows.map((row) => (
        <MenuRowButton
          key={`${row.keyId}-${row.time}`}
          icon={ROW_ICONS[row.icon]}
          tint="snooze"
          text={row.text}
          time={formatClock(row.time)}
          onClick={() => onPick(row.time, "suggestion", row.keyId)}
        />
      ))}
      {lastRow && (
        <MenuRowButton
          icon={LuHistory}
          tint="neutral"
          text={lastRow.text}
          time={formatClock(lastRow.time)}
          onClick={() => onPick(lastRow.time, "last")}
        />
      )}
      {(rows.length > 0 || lastRow) && (
        <div className="h-px bg-border mx-1.5 my-1" />
      )}
      <MenuRowButton
        icon={LuCalendarPlus}
        tint="neutral"
        text="Pick date & time…"
        onClick={() => setCustomOpen(true)}
      />
    </div>
  );
}

/** Desktop: a popover anchored to the snooze button, flipped upward near the viewport bottom. */
export function SnoozePopover(props: MenuProps): ReactElement {
  const rootRef = useRef<HTMLDivElement>(null);
  const [above, setAbove] = useState(false);
  const { onClose } = props;

  useLayoutEffect(() => {
    const node = rootRef.current;
    const anchor = node?.parentElement;
    if (!node || !anchor) return;
    function place() {
      if (!node || !anchor) return;
      const rect = anchor.getBoundingClientRect();
      const below = window.innerHeight - rect.bottom - 16;
      const aboveSpace = rect.top - 16;
      setAbove(node.offsetHeight > below && aboveSpace > below);
    }
    place();
    const observer = new ResizeObserver(place);
    observer.observe(node);
    return () => observer.disconnect();
  }, []);

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

  return (
    <div
      ref={rootRef}
      className={`absolute z-30 right-0 w-80 rounded-[14px] bg-surface-raised shadow-pop animate-rise ${
        above ? "bottom-full mb-2" : "top-full mt-2"
      }`}
      onClick={(e) => e.stopPropagation()}
      onKeyDown={(e) => e.stopPropagation()}
      role="menu"
      aria-label="Snooze until"
    >
      <MenuContent {...props} />
    </div>
  );
}

/** Phone: the same menu in a bottom sheet, headed by the todo being snoozed. */
export function SnoozeSheet({
  todoText,
  ...props
}: MenuProps & { todoText: string }): ReactElement {
  return (
    <Sheet label="Snooze" onClose={props.onClose}>
      <div className="px-5 pt-1 pb-1">
        <h2 className="m-0 text-lg font-semibold">Snooze</h2>
        <p className="m-0 mt-0.5 text-sm text-text-secondary truncate">
          <StyledText text={todoText} />
        </p>
      </div>
      <div className="px-1.5" role="menu" aria-label="Snooze until">
        <MenuContent {...props} />
      </div>
    </Sheet>
  );
}
