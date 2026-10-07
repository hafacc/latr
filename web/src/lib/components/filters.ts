import CircleCheck from "@lucide/svelte/icons/circle-check";
import Clock from "@lucide/svelte/icons/clock";
import Inbox from "@lucide/svelte/icons/inbox";
import Layers from "@lucide/svelte/icons/layers";
import type { Filter } from "../utils/todo";

export type IconType = typeof Inbox;

export const FILTER_META: Record<Filter, { label: string; icon: IconType }> = {
  ACTIVE: { label: "Active", icon: Inbox },
  SNOOZED: { label: "Snoozed", icon: Clock },
  DONE: { label: "Done", icon: CircleCheck },
  ALL: { label: "All", icon: Layers },
};

// Android's order, so the dock reads the same on both.
export const DOCK_ORDER: Filter[] = ["SNOOZED", "ACTIVE", "DONE", "ALL"];
