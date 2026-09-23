import type { IconType } from "react-icons";
import { LuCircleCheck, LuClock, LuInbox, LuLayers } from "react-icons/lu";
import type { Filter } from "../utils/todo";

export const FILTER_META: Record<Filter, { label: string; icon: IconType }> = {
  ACTIVE: { label: "Active", icon: LuInbox },
  SNOOZED: { label: "Snoozed", icon: LuClock },
  DONE: { label: "Done", icon: LuCircleCheck },
  ALL: { label: "All", icon: LuLayers },
};

// Android's order, so the dock reads the same on both.
export const DOCK_ORDER: Filter[] = ["SNOOZED", "ACTIVE", "DONE", "ALL"];
