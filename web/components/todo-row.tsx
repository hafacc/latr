"use client";

import {
  type ReactElement,
  type ReactNode,
  type PointerEvent as ReactPointerEvent,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
} from "react";
import {
  LuCheck,
  LuClock,
  LuPin,
  LuPinOff,
  LuTrash2,
  LuUndo2,
} from "react-icons/lu";
import { formatSnoozeTime } from "../utils/format";
import { useModifierHeld } from "../utils/kbd-modifier";
import { useTodos } from "../utils/store";
import {
  commitThreshold,
  committedSide,
  lockDirection,
  type SwipeAction,
  type SwipeLock,
  type SwipeSide,
  startsAtEdge,
  swipeAction,
} from "../utils/swipe";
import type { Todo } from "../utils/todo";
import { isoToEpoch, isSnoozed } from "../utils/todo";
import { DESKTOP_QUERY, useMediaQuery } from "../utils/use-media";
import { SnoozePopover, SnoozeSheet } from "./snooze-menu";

function Hint({
  children,
  tint,
}: {
  children: string;
  tint?: string;
}): ReactElement {
  return (
    <kbd
      className={`
        absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2
        min-w-4 h-4 px-1 flex items-center justify-center
        text-[10px] font-sans font-semibold leading-none
        rounded-[5px] bg-surface border border-border
        pointer-events-none
        ${tint ?? ""}
      `}
    >
      {children}
    </kbd>
  );
}

function StateIcon({
  isDone,
  isSnoozed,
  pinned,
  wasUnsnoozed,
}: {
  isDone: boolean;
  isSnoozed: boolean;
  pinned: boolean;
  wasUnsnoozed: boolean;
}): ReactElement {
  // Pinned displaces the state glyph only on an active row; elsewhere it just tints it.
  if (isDone) {
    return (
      <span
        className={`w-[18px] h-[18px] rounded-full flex items-center justify-center ${
          pinned ? "bg-accent text-on-accent" : "bg-done text-on-done"
        }`}
      >
        <LuCheck className="w-3 h-3" strokeWidth={3} />
      </span>
    );
  } else if (isSnoozed) {
    return (
      <LuClock
        className={`w-[18px] h-[18px] ${pinned ? "text-accent" : "text-snooze"}`}
      />
    );
  } else if (pinned) {
    return <LuPin className="w-[18px] h-[18px] text-accent" />;
  } else if (wasUnsnoozed) {
    return <LuClock className="w-[18px] h-[18px] text-snooze opacity-70" />;
  } else {
    return (
      <svg
        viewBox="0 0 24 24"
        className="w-[18px] h-[18px] text-muted"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.5"
        aria-hidden="true"
      >
        <circle cx="12" cy="12" r="8.5" />
      </svg>
    );
  }
}

const SWIPE_STYLE: Record<
  SwipeAction,
  { soft: string; strong: string; label: string; icon: ReactNode }
> = {
  snooze: {
    soft: "bg-snooze-soft text-snooze",
    strong: "bg-snooze text-on-snooze",
    label: "Snooze",
    icon: <LuClock className="w-5 h-5" />,
  },
  reactivate: {
    soft: "bg-accent-soft text-accent",
    strong: "bg-accent text-on-accent",
    label: "Restore",
    icon: <LuUndo2 className="w-5 h-5" />,
  },
  done: {
    soft: "bg-done-soft text-done",
    strong: "bg-done text-on-done",
    label: "Done",
    icon: <LuCheck className="w-5 h-5" />,
  },
  delete: {
    soft: "bg-danger-soft text-danger",
    strong: "bg-danger text-on-danger",
    label: "Delete",
    icon: <LuTrash2 className="w-5 h-5" />,
  },
};

type Drag = {
  pointerId: number;
  x: number;
  y: number;
  width: number;
  lock: SwipeLock;
};

export default function TodoRow({ todo }: { todo: Todo }): ReactElement {
  const {
    now,
    focusId,
    edit,
    markDone,
    reactivate,
    snooze,
    snoozeRows,
    snoozeLastRow,
    snoozeQuickTimes,
    refreshNow,
    togglePinned,
    remove,
    removeUndoable,
    setFocus,
    dropEmpty,
  } = useTodos();
  const desktop = useMediaQuery(DESKTOP_QUERY);
  const focused = focusId === todo.id;
  const modifierHeld = useModifierHeld();
  const [snoozeOpen, setSnoozeOpen] = useState(false);
  const [text, setText] = useState(todo.text);
  const [isFocused, setIsFocused] = useState(false);
  const [isHovered, setIsHovered] = useState(false);
  const [dx, setDx] = useState(0);
  const [settling, setSettling] = useState(false);
  // Hints follow focus, and fall back to hover when nothing is focused so
  // you can ⌘-act on whichever row the mouse is over without clicking in.
  const showHints =
    modifierHeld && (focused || (focusId === null && isHovered));
  const rowRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const dragRef = useRef<Drag | null>(null);
  const suppressClickRef = useRef(false);

  // While focused, the input value is owned by `text` so snapshot re-renders can't jump the cursor mid-edit.
  useEffect(() => {
    if (!isFocused) setText(todo.text);
  }, [todo.text, isFocused]);

  // Imperatively attach hover listeners so biome's a11y rule doesn't flag
  // onMouseEnter/Leave props on a non-interactive <div>. Hover state only
  // drives visual ⌘-hint positioning; no keyboard path depends on it.
  useEffect(() => {
    const node = rowRef.current;
    if (!node) return;
    const onEnter = () => setIsHovered(true);
    const onLeave = () => setIsHovered(false);
    node.addEventListener("mouseenter", onEnter);
    node.addEventListener("mouseleave", onLeave);
    return () => {
      node.removeEventListener("mouseenter", onEnter);
      node.removeEventListener("mouseleave", onLeave);
    };
  }, []);

  useLayoutEffect(() => {
    if (!focused) return;
    rowRef.current?.scrollIntoView({ block: "nearest" });
    inputRef.current?.focus();
    const len = inputRef.current?.value.length ?? 0;
    inputRef.current?.setSelectionRange(len, len);
    // The on-screen keyboard shrinks the viewport after focus; keep the row clear of the dock.
    const onResize = () => rowRef.current?.scrollIntoView({ block: "nearest" });
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, [focused]);

  useEffect(() => {
    if (!focused) return;
    function onBlurKey(e: KeyboardEvent) {
      if (e.key === "Escape") {
        (document.activeElement as HTMLElement | null)?.blur();
      }
    }
    window.addEventListener("keydown", onBlurKey);
    return () => window.removeEventListener("keydown", onBlurKey);
  }, [focused]);

  const isDone = todo.state === "DONE";
  const isActivelySnoozed = isSnoozed(todo, now);
  const wasUnsnoozed =
    !isDone && !isActivelySnoozed && todo.snoozeUntil !== null;
  const showPin = !isDone;

  function primaryToggle() {
    if (isDone) reactivate(todo.id);
    else markDone(todo.id);
  }

  function runSwipe(action: SwipeAction) {
    switch (action) {
      case "snooze":
        setSnoozeOpen(true);
        break;
      case "reactivate":
        reactivate(todo.id);
        break;
      case "done":
        markDone(todo.id);
        break;
      case "delete":
        removeUndoable(todo.id);
        break;
    }
  }

  function onPointerDown(e: ReactPointerEvent<HTMLDivElement>) {
    if (e.pointerType !== "touch") return;
    if (document.activeElement === inputRef.current) return;
    if (startsAtEdge(e.clientX, window.innerWidth)) return;
    dragRef.current = {
      pointerId: e.pointerId,
      x: e.clientX,
      y: e.clientY,
      width: e.currentTarget.offsetWidth,
      lock: "pending",
    };
  }

  function onPointerMove(e: ReactPointerEvent<HTMLDivElement>) {
    const drag = dragRef.current;
    if (!drag || drag.pointerId !== e.pointerId) return;
    const moveX = e.clientX - drag.x;
    const moveY = e.clientY - drag.y;
    if (drag.lock === "pending") {
      drag.lock = lockDirection(moveX, moveY);
      if (drag.lock === "vertical") {
        dragRef.current = null;
        return;
      }
      if (drag.lock === "horizontal") {
        e.currentTarget.setPointerCapture(e.pointerId);
        suppressClickRef.current = true;
        inputRef.current?.blur();
      }
    }
    if (drag.lock === "horizontal") setDx(moveX);
  }

  function endDrag(e: ReactPointerEvent<HTMLDivElement>, cancelled: boolean) {
    const drag = dragRef.current;
    if (!drag || drag.pointerId !== e.pointerId) return;
    dragRef.current = null;
    if (drag.lock !== "horizontal") return;
    const side = cancelled ? null : committedSide(dx, drag.width);
    setSettling(true);
    setDx(0);
    if (side) runSwipe(swipeAction(side, isDone, isActivelySnoozed));
    window.setTimeout(() => {
      suppressClickRef.current = false;
    }, 0);
  }

  const swipeSide: SwipeSide | null = dx > 0 ? "start" : dx < 0 ? "end" : null;
  const swipe = swipeSide
    ? SWIPE_STYLE[swipeAction(swipeSide, isDone, isActivelySnoozed)]
    : null;
  const pastThreshold =
    swipeSide !== null &&
    Math.abs(dx) >= commitThreshold(rowRef.current?.offsetWidth ?? 0);

  const primaryLabel = isDone ? "Reactivate" : "Mark done";

  // Desktop action buttons hide at rest and fade in on hover/focus (or while the snooze popover is open).
  const hoverAction =
    "opacity-0 pointer-events-none transition-opacity " +
    "group-hover/row:opacity-100 group-hover/row:pointer-events-auto " +
    "group-focus-within/row:opacity-100 group-focus-within/row:pointer-events-auto " +
    "group-data-[keep-actions=true]/row:opacity-100 group-data-[keep-actions=true]/row:pointer-events-auto";
  const actionButton =
    "relative w-7 h-7 flex items-center justify-center rounded-lg hover:bg-surface-muted transition-colors";

  const menuProps = {
    rows: snoozeRows,
    lastRow: snoozeLastRow,
    quickTimes: snoozeQuickTimes,
    now,
    refreshNow,
    onPick: (
      epoch: number,
      source: Parameters<typeof snooze>[2],
      pickedKey?: string,
    ) => {
      if (snooze(todo.id, epoch, source, pickedKey)) setSnoozeOpen(false);
    },
    onClose: () => setSnoozeOpen(false),
  };

  return (
    <div
      ref={rowRef}
      data-todo-id={todo.id}
      data-keep-actions={snoozeOpen}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={(e) => endDrag(e, false)}
      onPointerCancel={(e) => endDrag(e, true)}
      onClickCapture={(e) => {
        if (suppressClickRef.current) {
          e.preventDefault();
          e.stopPropagation();
        }
      }}
      className={`
        group/row relative rounded-[10px] touch-pan-y touch-pinch-zoom
        scroll-mt-20 max-md:scroll-mt-[calc(env(safe-area-inset-top)+64px)] max-md:scroll-mb-[calc(env(safe-area-inset-bottom)+104px)]
        ${dx !== 0 ? "overflow-hidden" : ""}
        ${snoozeOpen ? "z-20" : ""}
      `}
    >
      {swipe && (
        <div
          aria-hidden="true"
          className={`absolute inset-0 flex items-center gap-2 px-5 text-sm font-medium transition-colors ${
            pastThreshold ? swipe.strong : swipe.soft
          } ${swipeSide === "start" ? "justify-start" : "justify-end"}`}
        >
          {swipe.icon}
          {swipe.label}
        </div>
      )}

      <div
        style={dx !== 0 ? { transform: `translateX(${dx}px)` } : undefined}
        onTransitionEnd={() => setSettling(false)}
        className={`
          relative flex items-start gap-3
          min-h-10 max-md:min-h-14 px-3 py-[9px] max-md:py-[15px]
          rounded-[10px]
          ${dx !== 0 ? "bg-surface-hover shadow-dock" : "bg-bg"}
          hover:bg-surface-hover focus-within:bg-surface-hover
          ${settling ? "transition-transform duration-200 ease-out" : "transition-colors"}
        `}
      >
        <button
          type="button"
          data-action="primary"
          onClick={primaryToggle}
          aria-label={primaryLabel}
          title={primaryLabel}
          className="relative -mx-1 -mb-1 -mt-0.5 p-1 rounded-md hover:bg-surface-muted transition-colors shrink-0"
        >
          <span className={`block ${showHints ? "invisible" : ""}`}>
            <StateIcon
              isDone={isDone}
              isSnoozed={isActivelySnoozed}
              pinned={todo.pinned}
              wasUnsnoozed={wasUnsnoozed}
            />
          </span>
          {showHints && <Hint tint="text-done">D</Hint>}
        </button>

        <div className="flex-1 min-w-0 flex flex-col">
          <label className="flex min-w-0 cursor-text">
            <textarea
              ref={inputRef}
              rows={1}
              value={text}
              onChange={(e) => {
                setText(e.target.value);
                edit(todo.id, e.target.value);
              }}
              onFocus={() => {
                setIsFocused(true);
                setFocus(todo.id);
              }}
              onBlur={() => {
                setIsFocused(false);
                setFocus(null);
                if (text.trim().length === 0) remove(todo.id);
                else dropEmpty();
              }}
              onKeyDown={(e) => {
                if (e.key === "Enter" && !e.shiftKey) {
                  e.preventDefault();
                  (e.currentTarget as HTMLTextAreaElement).blur();
                }
              }}
              className={`
                flex-1 min-w-0 bg-transparent outline-none text-text resize-none
                field-sizing-content overflow-hidden text-[15px] leading-[22px] max-md:text-base max-md:leading-6
                placeholder:text-muted
                ${isDone ? "line-through text-text-secondary" : ""}
              `}
            />
          </label>
          {!desktop && isFocused && (
            <div className="flex gap-1 pt-2 -ml-2">
              {isActivelySnoozed && (
                <StripButton
                  onClick={() => reactivate(todo.id)}
                  icon={<LuUndo2 className="w-4 h-4" />}
                >
                  Unsnooze
                </StripButton>
              )}
              {!isDone && (
                <StripButton
                  onClick={() => setSnoozeOpen(true)}
                  icon={<LuClock className="w-4 h-4" />}
                >
                  Snooze
                </StripButton>
              )}
              {showPin && (
                <StripButton
                  onClick={() => togglePinned(todo.id)}
                  icon={
                    todo.pinned ? (
                      <LuPinOff className="w-4 h-4" />
                    ) : (
                      <LuPin className="w-4 h-4" />
                    )
                  }
                >
                  {todo.pinned ? "Unpin" : "Pin"}
                </StripButton>
              )}
              <StripButton
                onClick={() => removeUndoable(todo.id)}
                icon={<LuTrash2 className="w-4 h-4" />}
                danger
              >
                Delete
              </StripButton>
            </div>
          )}
        </div>

        {/* Same badge whether still snoozed (future) or was-unsnoozed (past). */}
        {!isDone && todo.snoozeUntil && (
          <span
            className="
              pt-0.5 text-[13px] leading-[18px] text-text-secondary tabular-nums shrink-0 whitespace-nowrap
              opacity-100 md:group-hover/row:opacity-0 md:group-focus-within/row:opacity-0
              md:group-data-[keep-actions=true]/row:opacity-0
              transition-opacity
            "
          >
            {formatSnoozeTime(isoToEpoch(todo.snoozeUntil))}
          </span>
        )}

        {desktop && (
          <div className="absolute right-2 top-1.5 flex items-center gap-0.5 rounded-lg bg-surface-hover opacity-0 group-hover/row:opacity-100 group-focus-within/row:opacity-100 group-data-[keep-actions=true]/row:opacity-100 transition-opacity">
            {isActivelySnoozed && (
              <button
                type="button"
                data-action="unsnooze"
                onClick={() => reactivate(todo.id)}
                aria-label="Unsnooze"
                title="Unsnooze"
                className={`${actionButton} text-accent ${hoverAction}`}
              >
                <LuUndo2
                  className={`w-4 h-4 ${showHints ? "invisible" : ""}`}
                />
                {showHints && <Hint>U</Hint>}
              </button>
            )}

            {!isDone && (
              <div className={`relative ${hoverAction}`}>
                <button
                  type="button"
                  data-action="snooze"
                  onClick={() => setSnoozeOpen((v) => !v)}
                  aria-label={
                    isActivelySnoozed ? "Reschedule snooze" : "Snooze"
                  }
                  title={isActivelySnoozed ? "Reschedule" : "Snooze"}
                  className={`${actionButton} text-text-secondary`}
                >
                  <LuClock
                    className={`w-4 h-4 ${showHints ? "invisible" : ""}`}
                  />
                  {showHints && <Hint>S</Hint>}
                </button>
                {snoozeOpen && <SnoozePopover {...menuProps} />}
              </div>
            )}

            {showPin && (
              <button
                type="button"
                data-action="pin"
                // Don't take focus on click — otherwise the row's focus-within keeps
                // it highlighted with the cluster open after a pin/unpin tap. The
                // click still fires; pinning is purely a state toggle.
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => togglePinned(todo.id)}
                aria-label={todo.pinned ? "Unpin" : "Pin"}
                title={todo.pinned ? "Unpin" : "Pin"}
                className={`${actionButton} ${hoverAction} ${
                  todo.pinned ? "text-accent" : "text-text-secondary"
                }`}
              >
                <LuPin className="w-4 h-4" />
              </button>
            )}

            <button
              type="button"
              data-action="delete"
              onClick={() => removeUndoable(todo.id)}
              aria-label="Delete"
              title="Delete"
              className={`${actionButton} text-text-secondary hover:text-danger ${hoverAction}`}
            >
              <LuTrash2 className={`w-4 h-4 ${showHints ? "invisible" : ""}`} />
              {showHints && <Hint>⌫</Hint>}
            </button>
          </div>
        )}
      </div>

      {!desktop && snoozeOpen && (
        <SnoozeSheet {...menuProps} todoText={todo.text} />
      )}
    </div>
  );
}

function StripButton({
  onClick,
  icon,
  danger,
  children,
}: {
  onClick: () => void;
  icon: ReactNode;
  danger?: boolean;
  children: string;
}): ReactElement {
  return (
    <button
      type="button"
      // Keep the textarea focused through the tap, or the strip unmounts before the click lands.
      onPointerDown={(e) => e.preventDefault()}
      onMouseDown={(e) => e.preventDefault()}
      onClick={onClick}
      className={`h-9 px-2.5 inline-flex items-center gap-1.5 rounded-lg text-sm font-medium transition-colors ${
        danger
          ? "text-danger hover:bg-danger-soft"
          : "text-text-secondary hover:bg-surface-muted"
      }`}
    >
      {icon}
      {children}
    </button>
  );
}
