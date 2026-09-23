"use client";

import { type ReactElement, type ReactNode, useMemo } from "react";
import { groupForFilter } from "../utils/group";
import { useTodos } from "../utils/store";
import {
  type Filter,
  matchesFilter,
  rankBySearch,
  sortForFilter,
} from "../utils/todo";
import Logo from "./logo";
import TodoRow from "./todo-row";

export default function TodoList(): ReactElement {
  const { todos, filter, search, now } = useTodos();

  const { groups, total } = useMemo(() => {
    const filtered = todos.filter((t) => matchesFilter(t, filter, now));
    if (search.trim().length > 0) {
      const ranked = rankBySearch(filtered, search);
      return {
        groups: ranked.length > 0 ? [{ label: "", todos: ranked }] : [],
        total: ranked.length,
      };
    }
    const sorted = sortForFilter(filtered, filter);
    return {
      groups: groupForFilter(sorted, filter, now),
      total: sorted.length,
    };
  }, [todos, filter, search, now]);

  if (total === 0) {
    const searching = search.trim().length > 0;
    const { title, hint } = searching
      ? { title: `No todos match “${search}”`, hint: "Try fewer words." }
      : emptyCopy(filter);
    return (
      <div className="flex flex-col items-center text-center py-20 gap-2">
        <Logo className="w-8 h-10 text-border mb-2" />
        <div className="text-[15px] text-text">{title}</div>
        <div className="text-[13px] text-text-secondary">{hint}</div>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      {groups.map((group) => (
        <section key={group.label || "search"}>
          {group.label && (
            <div className="flex gap-2 px-3 pb-1.5 text-[12.5px] font-semibold text-text-secondary">
              <span>{group.label}</span>
              <span className="font-normal text-muted tabular-nums">
                {group.todos.length}
              </span>
            </div>
          )}
          <div className="space-y-0.5">
            {group.todos.map((t) => (
              <TodoRow key={t.id} todo={t} />
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}

export function ListSkeleton(): ReactElement {
  const widths = ["w-3/5", "w-2/5", "w-4/5", "w-1/2", "w-2/3"];
  return (
    <div
      className="space-y-0.5"
      role="status"
      aria-busy="true"
      aria-label="Loading"
    >
      {widths.map((w) => (
        <div
          key={w}
          className="flex items-center gap-3 min-h-10 max-md:min-h-14 px-3"
        >
          <div className="w-[18px] h-[18px] rounded-full bg-surface-hover animate-shimmer" />
          <div
            className={`h-3 rounded-full bg-surface-hover animate-shimmer ${w}`}
          />
        </div>
      ))}
    </div>
  );
}

const ADD_HINT = (
  <>
    <span className="hidden md:inline">Press n to add one.</span>
    <span className="md:hidden">Tap + to add one.</span>
  </>
);

function emptyCopy(filter: Filter): { title: string; hint: ReactNode } {
  switch (filter) {
    case "ACTIVE":
      return { title: "Nothing to do.", hint: ADD_HINT };
    case "SNOOZED":
      return { title: "Nothing snoozed.", hint: "Snoozed todos wait here." };
    case "DONE":
      return { title: "Nothing done yet.", hint: "Completed todos land here." };
    case "ALL":
      return { title: "No todos.", hint: ADD_HINT };
  }
}
