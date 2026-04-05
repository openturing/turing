import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { IconGripVertical, IconX } from "@tabler/icons-react";
import type { Icon } from "@tabler/icons-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import type { WidgetId } from "../use-dashboard";
import { cn } from "@/lib/utils";

interface WidgetWrapperProps {
  id: WidgetId;
  titleKey: string;
  icon: Icon;
  gradient: string;
  colSpan?: 1 | 2 | 3;
  onHide: (id: WidgetId) => void;
  children: ReactNode;
  className?: string;
}

const colSpanClass: Record<number, string> = {
  1: "",
  2: "sm:col-span-2",
  3: "sm:col-span-2 lg:col-span-3",
};

export function WidgetWrapper({
  id,
  titleKey,
  icon: WidgetIcon,
  gradient,
  colSpan = 1,
  onHide,
  children,
  className,
}: WidgetWrapperProps) {
  const { t } = useTranslation();

  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id });

  const style: React.CSSProperties = {
    // Use Translate only — CSS.Transform includes scaleX/scaleY which distorts
    // card content (images, text) when dragging across differently-sized grid cells
    transform: CSS.Translate.toString(transform),
    transition,
    opacity: isDragging ? 0.6 : 1,
    zIndex: isDragging ? 50 : undefined,
  };

  return (
    <div
      ref={setNodeRef}
      style={style}
      className={cn(
        "flex flex-col rounded-xl border bg-card shadow-sm min-h-[200px]",
        colSpanClass[colSpan] ?? "",
        className
      )}
    >
      {/* Header */}
      <div className="flex items-center gap-2 px-4 py-3 border-b border-border/50">
        <button
          {...attributes}
          {...listeners}
          className="cursor-grab active:cursor-grabbing p-1 rounded hover:bg-accent transition-colors shrink-0"
          aria-label={t("dashboard.dragToReorder")}
        >
          <IconGripVertical className="size-4 text-muted-foreground" />
        </button>
        <div className={`inline-flex rounded-md bg-gradient-to-br ${gradient} p-1.5 shrink-0`}>
          <WidgetIcon className="size-3.5 text-white" />
        </div>
        <span className="text-sm font-medium flex-1 truncate">{t(titleKey)}</span>
        <button
          onClick={() => onHide(id)}
          className="p-1 rounded hover:bg-accent transition-colors shrink-0"
          aria-label={t("dashboard.hideWidget")}
        >
          <IconX className="size-3.5 text-muted-foreground" />
        </button>
      </div>

      {/* Body */}
      <div className="flex-1 p-4 overflow-hidden">{children}</div>
    </div>
  );
}
