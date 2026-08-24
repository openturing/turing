import { useLlmInstances } from "@/api/queries/llm-instance.queries";
import { IconCpu2, IconRefresh } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { NavLink } from "react-router-dom";
import { ROUTES } from "@/app/routes.const";
import type { WidgetId } from "../use-dashboard";
import { WidgetWrapper } from "./widget-wrapper";

interface LLMStatusWidgetProps {
  onHide: (id: WidgetId) => void;
  colSpan?: 1 | 2 | 3;
}

export function LLMStatusWidget({ onHide, colSpan }: LLMStatusWidgetProps) {
  const { t } = useTranslation();
  // T1018 — read through the shared hook. This widget kept its own
  // instances/loading pair and filled it from the service in an effect; the
  // query cache already holds this list for the LLM pages, so the dashboard now
  // reads the same entry instead of re-fetching one of its own. A failure still
  // renders the empty state, which is what the swallowed catch did.
  const { data: instances = [], isLoading, refetch } = useLlmInstances();

  const enabledCount = instances.filter((i) => i.enabled === 1).length;
  const totalCount = instances.length;

  return (
    <WidgetWrapper
      id="llmStatus"
      titleKey="dashboard.widgets.llmStatus.title"
      icon={IconCpu2}
      gradient="from-blue-600 to-indigo-600"
      colSpan={colSpan}
      onHide={onHide}
    >
      {isLoading ? (
        <div className="text-xs text-muted-foreground">{t("dashboard.loading")}</div>
      ) : instances.length === 0 ? (
        <div className="flex flex-col items-center justify-center h-full gap-2 text-center py-4">
          <IconCpu2 className="size-8 text-muted-foreground/40" />
          <p className="text-xs text-muted-foreground">{t("dashboard.widgets.llmStatus.noInstances")}</p>
          <NavLink
            to={ROUTES.LLM_ROOT}
            className="text-xs font-medium text-blue-600 dark:text-blue-400 hover:underline"
          >
            {t("dashboard.widgets.llmStatus.configure")}
          </NavLink>
        </div>
      ) : (
        <div className="space-y-2">
          {/* Summary */}
          <div className="flex items-center justify-between px-3 py-2 rounded-lg bg-secondary/30 border border-border/40">
            <span className="text-xs text-muted-foreground">{t("dashboard.widgets.llmStatus.active")}</span>
            <span className="text-xs font-semibold">
              <span className="text-emerald-600">{enabledCount}</span>
              <span className="text-muted-foreground"> / {totalCount}</span>
            </span>
          </div>

          {/* List */}
          {instances.slice(0, 6).map((inst) => (
            <div
              key={inst.id}
              className="flex items-center gap-2 rounded-lg border border-border/50 px-3 py-2 hover:bg-secondary/30 transition-colors"
            >
              <span
                className={`w-1.5 h-1.5 rounded-full shrink-0 ${
                  inst.enabled === 1 ? "bg-emerald-500" : "bg-slate-400"
                }`}
              />
              <div className="flex-1 min-w-0">
                <p className="text-xs font-medium truncate">{inst.title}</p>
                <p className="text-[10px] text-muted-foreground truncate">{inst.modelName}</p>
              </div>
              <NavLink
                to={`${ROUTES.LLM_ROOT}/instance/${inst.id}`}
                className="text-[10px] text-muted-foreground hover:text-foreground shrink-0"
              >
                {t("home.open")}
              </NavLink>
            </div>
          ))}

          <button
            onClick={() => void refetch()}
            className="flex items-center gap-1 text-[10px] text-muted-foreground hover:text-foreground transition-colors mt-1"
          >
            <IconRefresh className="size-3" />
            {t("dashboard.refresh")}
          </button>
        </div>
      )}
    </WidgetWrapper>
  );
}
