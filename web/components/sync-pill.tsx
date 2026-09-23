"use client";

import { type ReactElement, useEffect, useState } from "react";
import { LuCloudOff, LuRefreshCw } from "react-icons/lu";
import { useTodos } from "../utils/store";
import { useOnlineStatus } from "../utils/use-online-status";

// How long syncing must persist before the indicator appears, so brief
// fromCache blips around writes don't flicker it.
const SYNC_INDICATOR_DELAY_MS = 500;

export default function SyncPill(): ReactElement | null {
  const { syncing } = useTodos();
  const online = useOnlineStatus();

  const [showSyncing, setShowSyncing] = useState(false);
  useEffect(() => {
    if (!syncing) {
      setShowSyncing(false);
      return;
    }
    const id = setTimeout(() => setShowSyncing(true), SYNC_INDICATOR_DELAY_MS);
    return () => clearTimeout(id);
  }, [syncing]);

  if (!showSyncing) return null;
  if (online) {
    return (
      <span
        className="inline-flex items-center gap-1.5 h-6 px-2 rounded-full bg-surface-muted text-text-secondary text-xs"
        title="Syncing"
      >
        <LuRefreshCw className="w-3 h-3 animate-spin" aria-hidden="true" />
        Syncing…
      </span>
    );
  } else {
    return (
      <span
        className="inline-flex items-center gap-1.5 h-6 px-2 rounded-full bg-snooze-soft text-snooze text-xs"
        title="Offline — changes saved on this device"
      >
        <LuCloudOff className="w-3 h-3" aria-hidden="true" />
        Offline
      </span>
    );
  }
}
