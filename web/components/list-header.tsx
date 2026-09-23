"use client";

import type { ReactElement, RefObject } from "react";
import { LuArrowLeft, LuSearch, LuX } from "react-icons/lu";
import { AccountButton } from "./auth-menu";
import SyncPill from "./sync-pill";

function ClearAllButton({ onClick }: { onClick: () => void }): ReactElement {
  return (
    <button
      type="button"
      onClick={onClick}
      className="h-8 px-3 rounded-lg text-sm text-text-secondary hover:text-text hover:bg-surface-hover transition-colors shrink-0"
    >
      Clear all
    </button>
  );
}

/** Desktop list header: filter title, count, sync status, search. */
export function ListHeader({
  title,
  count,
  search,
  onSearch,
  searchRef,
  onClearAll,
}: {
  title: string;
  count: number;
  search: string;
  onSearch: (s: string) => void;
  searchRef: RefObject<HTMLInputElement | null>;
  onClearAll: (() => void) | null;
}): ReactElement {
  return (
    <header className="hidden md:flex sticky top-0 z-10 items-center gap-3 h-16 bg-bg/90 backdrop-blur">
      <h1 className="m-0 text-[22px] leading-7 font-semibold tracking-[-0.01em]">
        {title}
      </h1>
      {count > 0 && (
        <span className="text-[13px] text-text-secondary tabular-nums">
          {count}
        </span>
      )}
      <SyncPill />
      <div className="flex-1" />
      {onClearAll && <ClearAllButton onClick={onClearAll} />}
      <label className="group/search flex items-center gap-2 w-60 focus-within:w-72 h-8 px-2.5 rounded-[10px] bg-surface-muted focus-within:bg-surface focus-within:ring-1 focus-within:ring-accent text-text-secondary transition-all">
        <LuSearch className="w-[15px] h-[15px] shrink-0" aria-hidden="true" />
        <input
          ref={searchRef}
          type="search"
          value={search}
          onChange={(e) => onSearch(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Escape") e.currentTarget.blur();
          }}
          placeholder="Search"
          aria-label="Search todos"
          className="flex-1 min-w-0 bg-transparent outline-none text-[13px] text-text placeholder:text-text-secondary [&::-webkit-search-cancel-button]:hidden"
        />
        {search ? (
          <button
            type="button"
            onClick={() => onSearch("")}
            aria-label="Clear search"
            className="p-0.5 rounded text-text-secondary hover:text-text"
          >
            <LuX className="w-3.5 h-3.5" />
          </button>
        ) : (
          <kbd className="text-[11.5px] font-medium font-sans px-1.5 rounded-md bg-surface border border-border group-focus-within/search:hidden">
            /
          </kbd>
        )}
      </label>
    </header>
  );
}

/** Phone app bar: title and count, which a search icon swaps for a search field. */
export function MobileAppBar({
  title,
  count,
  search,
  onSearch,
  searching,
  onSearching: setSearching,
  onClearAll,
}: {
  title: string;
  count: number;
  search: string;
  onSearch: (s: string) => void;
  searching: boolean;
  onSearching: (searching: boolean) => void;
  onClearAll: (() => void) | null;
}): ReactElement {
  const open = searching || search.length > 0;

  return (
    <header className="md:hidden sticky top-0 z-10 flex items-center gap-2 h-14 pl-[max(1rem,env(safe-area-inset-left))] pr-[max(1rem,env(safe-area-inset-right))] pt-[env(safe-area-inset-top)] box-content bg-bg/90 backdrop-blur">
      {open ? (
        <>
          <button
            type="button"
            onClick={() => {
              onSearch("");
              setSearching(false);
            }}
            aria-label="Close search"
            className="-ml-2 w-10 h-10 flex items-center justify-center rounded-full text-text-secondary hover:bg-surface-hover"
          >
            <LuArrowLeft className="w-5 h-5" />
          </button>
          <input
            // biome-ignore lint/a11y/noAutofocus: the field only exists because the user just tapped search
            autoFocus
            type="search"
            value={search}
            onChange={(e) => onSearch(e.target.value)}
            placeholder="Search"
            aria-label="Search todos"
            className="flex-1 min-w-0 h-10 px-3 rounded-[10px] bg-surface-muted outline-none text-[16px] text-text placeholder:text-text-secondary"
          />
        </>
      ) : (
        <>
          <h1 className="m-0 text-[22px] leading-7 font-semibold tracking-[-0.01em]">
            {title}
          </h1>
          {count > 0 && (
            <span className="text-[13px] text-text-secondary tabular-nums">
              {count}
            </span>
          )}
          <SyncPill />
          <div className="flex-1" />
          {onClearAll && <ClearAllButton onClick={onClearAll} />}
          <button
            type="button"
            onClick={() => setSearching(true)}
            aria-label="Search"
            className="w-10 h-10 flex items-center justify-center rounded-full text-text-secondary hover:bg-surface-hover"
          >
            <LuSearch className="w-5 h-5" />
          </button>
          <AccountButton />
        </>
      )}
    </header>
  );
}
