"use client";

import type { ReactElement } from "react";
import { LuPanelLeftClose, LuPanelLeftOpen } from "react-icons/lu";
import { FILTERS, type Filter } from "../utils/todo";
import { SidebarAccount } from "./auth-menu";
import { FILTER_META } from "./filters";
import Logo from "./logo";

export default function Sidebar({
  filter,
  onFilter,
  counts,
  collapsed,
  onToggleCollapsed,
}: {
  filter: Filter;
  onFilter: (f: Filter) => void;
  counts: Partial<Record<Filter, number>>;
  collapsed: boolean;
  onToggleCollapsed: () => void;
}): ReactElement {
  const expandedOnly =
    "group-data-[collapsed=true]/sidebar:hidden group-data-[collapsed=true]/sidebar:group-hover/sidebar:inline";
  return (
    <aside
      data-collapsed={collapsed}
      className="
        group/sidebar
        hidden md:flex flex-col
        fixed inset-y-0 left-0 z-20
        bg-surface-muted
        transition-[width] duration-200 ease-out
        w-60
        data-[collapsed=true]:w-14
        data-[collapsed=true]:hover:w-60
        overflow-hidden
        px-2 py-4
      "
    >
      <div className="flex items-center justify-between h-8 px-2 mb-3">
        <span className="flex items-center gap-2.5">
          <Logo className="w-5 h-5 shrink-0 text-accent" />
          <span
            className={`font-semibold text-base tracking-tight ${expandedOnly}`}
          >
            latr
          </span>
        </span>
        <button
          type="button"
          onClick={onToggleCollapsed}
          className={`p-1.5 rounded-lg text-text-secondary hover:bg-surface-hover hover:text-text transition-colors group-data-[collapsed=true]/sidebar:hidden group-data-[collapsed=true]/sidebar:group-hover/sidebar:block`}
          aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
          title={collapsed ? "Expand" : "Collapse"}
        >
          {collapsed ? (
            <LuPanelLeftOpen className="w-4 h-4" />
          ) : (
            <LuPanelLeftClose className="w-4 h-4" />
          )}
        </button>
      </div>

      <nav aria-label="Filters" className="flex-1 space-y-0.5">
        {FILTERS.map((f) => {
          const { label, icon: Icon } = FILTER_META[f];
          const active = f === filter;
          const count = counts[f];
          return (
            <button
              key={f}
              type="button"
              onClick={() => onFilter(f)}
              aria-current={active ? "page" : undefined}
              className={`
                w-full flex items-center gap-2.5 px-2.5 h-8 rounded-[10px]
                text-sm transition-colors
                ${active ? "bg-surface-hover text-text font-medium" : "text-text hover:bg-surface-hover"}
              `}
            >
              <Icon
                className={`w-4 h-4 shrink-0 ${active ? "text-accent" : "text-text-secondary"}`}
              />
              <span className={`flex-1 text-left truncate ${expandedOnly}`}>
                {label}
              </span>
              {count !== undefined && count > 0 && (
                <span
                  className={`text-[12.5px] text-text-secondary tabular-nums ${expandedOnly}`}
                >
                  {count}
                </span>
              )}
            </button>
          );
        })}
      </nav>

      <SidebarAccount />
    </aside>
  );
}
