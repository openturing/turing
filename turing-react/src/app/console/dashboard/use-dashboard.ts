import { useCallback, useEffect, useState } from "react";
import { arrayMove } from "@dnd-kit/sortable";

export type WidgetId =
  | "systemHealth"
  | "liveMetrics"
  | "snSites"
  | "topTerms"
  | "llmStatus"
  | "quickLinks";

export interface WidgetConfig {
  id: WidgetId;
  visible: boolean;
  colSpan: 1 | 2 | 3;
}

const DEFAULT_WIDGETS: WidgetConfig[] = [
  { id: "systemHealth", visible: true, colSpan: 1 },
  { id: "liveMetrics", visible: true, colSpan: 2 },
  { id: "snSites", visible: true, colSpan: 1 },
  { id: "topTerms", visible: true, colSpan: 1 },
  { id: "llmStatus", visible: true, colSpan: 1 },
  { id: "quickLinks", visible: true, colSpan: 1 },
];

const STORAGE_KEY = "turing-dashboard-widgets-v1";

function loadWidgets(): WidgetConfig[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return DEFAULT_WIDGETS;
    const saved: WidgetConfig[] = JSON.parse(raw);
    const validIds = new Set(DEFAULT_WIDGETS.map((w) => w.id));
    // Filter out widgets that no longer exist, then add new defaults at the end
    const filtered = saved.filter((w) => validIds.has(w.id));
    const filteredIds = new Set(filtered.map((w) => w.id));
    const merged = [
      ...filtered,
      ...DEFAULT_WIDGETS.filter((w) => !filteredIds.has(w.id)),
    ];
    return merged;
  } catch {
    return DEFAULT_WIDGETS;
  }
}

function saveWidgets(widgets: WidgetConfig[]) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(widgets));
  } catch {
    // ignore storage errors
  }
}

export function useDashboard() {
  const [widgets, setWidgets] = useState<WidgetConfig[]>(loadWidgets);

  useEffect(() => {
    saveWidgets(widgets);
  }, [widgets]);

  const toggleWidget = useCallback((id: WidgetId) => {
    setWidgets((prev) =>
      prev.map((w) => (w.id === id ? { ...w, visible: !w.visible } : w))
    );
  }, []);

  const setColSpan = useCallback((id: WidgetId, colSpan: 1 | 2 | 3) => {
    setWidgets((prev) =>
      prev.map((w) => (w.id === id ? { ...w, colSpan } : w))
    );
  }, []);

  const reorder = useCallback((activeId: string, overId: string) => {
    setWidgets((prev) => {
      const oldIndex = prev.findIndex((w) => w.id === activeId);
      const newIndex = prev.findIndex((w) => w.id === overId);
      if (oldIndex === -1 || newIndex === -1) return prev;
      return arrayMove(prev, oldIndex, newIndex);
    });
  }, []);

  const resetLayout = useCallback(() => {
    setWidgets(DEFAULT_WIDGETS);
  }, []);

  const visibleWidgets = widgets.filter((w) => w.visible);

  return { widgets, visibleWidgets, toggleWidget, setColSpan, reorder, resetLayout };
}
