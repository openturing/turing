import {
  IconArrowsSplit2,
  IconBell,
  IconBook2,
  IconClock,
  IconDatabase,
  IconFlag3,
  IconForms,
  IconHandStop,
  IconListCheck,
  IconPencilPlus,
  IconPlayerPlayFilled,
  IconRepeat,
  IconRouteAltLeft,
  IconSparkles,
  IconSubtask,
  IconUserCheck,
  IconVariable,
  IconWebhook,
  type Icon,
} from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

import type { FlowNodeType } from "../types";

/**
 * Left-hand palette of draggable components. Each card is a "native" HTML drag source — React Flow
 * reads the MIME payload on drop and materializes the matching node type.
 *
 * @since 2026.2.4
 */

interface PaletteItem {
  type: FlowNodeType;
  labelKey: string;
  icon: Icon;
  accent: string;
}

const PALETTE: PaletteItem[] = [
  { type: "start", labelKey: "chatFlow.palette.start", icon: IconPlayerPlayFilled, accent: "text-emerald-600" },
  { type: "aiQuestion", labelKey: "chatFlow.palette.aiQuestion", icon: IconBook2, accent: "text-blue-600" },
  { type: "condition", labelKey: "chatFlow.palette.condition", icon: IconDatabase, accent: "text-amber-600" },
  { type: "switch", labelKey: "chatFlow.palette.switch", icon: IconArrowsSplit2, accent: "text-cyan-600" },
  { type: "formCapture", labelKey: "chatFlow.palette.formCapture", icon: IconForms, accent: "text-indigo-600" },
  { type: "slot", labelKey: "chatFlow.palette.slot", icon: IconVariable, accent: "text-sky-600" },
  { type: "writeSlot", labelKey: "chatFlow.palette.writeSlot", icon: IconPencilPlus, accent: "text-teal-600" },
  { type: "functionCall", labelKey: "chatFlow.palette.functionCall", icon: IconBell, accent: "text-orange-600" },
  { type: "webhook", labelKey: "chatFlow.palette.webhook", icon: IconWebhook, accent: "text-pink-600" },
  { type: "scheduleAgent", labelKey: "chatFlow.palette.scheduleAgent", icon: IconClock, accent: "text-lime-600" },
  { type: "planningStep", labelKey: "chatFlow.palette.planningStep", icon: IconListCheck, accent: "text-yellow-600" },
  { type: "iteratePlan", labelKey: "chatFlow.palette.iteratePlan", icon: IconRepeat, accent: "text-emerald-600" },
  { type: "suspend", labelKey: "chatFlow.palette.suspend", icon: IconHandStop, accent: "text-slate-600" },
  { type: "humanApproval", labelKey: "chatFlow.palette.humanApproval", icon: IconUserCheck, accent: "text-rose-600" },
  { type: "subFlow", labelKey: "chatFlow.palette.subFlow", icon: IconSubtask, accent: "text-violet-600" },
  { type: "subFlowSwitch", labelKey: "chatFlow.palette.subFlowSwitch", icon: IconRouteAltLeft, accent: "text-purple-600" },
  { type: "persona", labelKey: "chatFlow.palette.persona", icon: IconSparkles, accent: "text-fuchsia-600" },
  { type: "end", labelKey: "chatFlow.palette.end", icon: IconFlag3, accent: "text-rose-600" },
];

export function FlowSidebar() {
  const { t } = useTranslation();

  const onDragStart = (event: React.DragEvent, type: FlowNodeType) => {
    event.dataTransfer.setData("application/x-chatflow-node", type);
    event.dataTransfer.effectAllowed = "move";
  };

  return (
    <aside className="flex h-full w-56 flex-col gap-2 border-r bg-muted/30 p-4">
      <div className="mb-1 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
        {t("chatFlow.palette.title")}
      </div>
      <div className="grid grid-cols-2 gap-2">
        {PALETTE.map(({ type, labelKey, icon: ItemIcon, accent }) => (
          <button
            key={type}
            type="button"
            draggable
            onDragStart={(e) => onDragStart(e, type)}
            className="flex cursor-grab flex-col items-center gap-1 rounded-md border bg-background p-3 text-xs shadow-sm transition hover:-translate-y-0.5 hover:border-primary hover:shadow active:cursor-grabbing"
          >
            <ItemIcon className={`size-6 ${accent}`} />
            <span className="text-center font-medium leading-tight">{t(labelKey)}</span>
          </button>
        ))}
      </div>
      <p className="mt-auto pt-2 text-[10px] leading-snug text-muted-foreground">
        {t("chatFlow.palette.hint")}
      </p>
    </aside>
  );
}
