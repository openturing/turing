import { useChatFlowFunnel } from "@/api/queries/chat-flow.queries";
import { Badge } from "@/components/ui/badge";
import { SectionCard } from "@/components/ui/section-card";
import type { TurChatFlowFunnelNode } from "@/models/agent/chat-flow.model";
import { IconChartBar } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

interface ChatFlowFunnelPanelProps {
  agentId: string;
  flowId: string | undefined;
}

/**
 * T85 / §VII.10.b — funnel sidebar panel. Renders per-node cursor +
 * completion counts as a vertical stack so the operator can spot where
 * the conversation drops off. Hidden on a brand-new flow (no id yet)
 * and on a clean / empty flow (no states or submissions captured).
 *
 * @since 2026.3.1
 */
export function ChatFlowFunnelPanel({
  agentId,
  flowId,
}: Readonly<ChatFlowFunnelPanelProps>) {
  const { t } = useTranslation();
  const { data: report } = useChatFlowFunnel(agentId, flowId);

  if (!report || report.nodes.length === 0) return null;
  if (report.totalStates === 0 && report.totalSubmissions === 0) return null;

  const maxCount = Math.max(
    1,
    ...report.nodes.map((n) => n.cursorCount + n.completedCount),
  );

  return (
    <SectionCard variant="blue">
      <SectionCard.Header
        icon={IconChartBar}
        title={t("chatFlow.funnel.title", {
          defaultValue: "Funnel — drop-off & completion",
        })}
        description={t("chatFlow.funnel.description", {
          parked: report.totalStates,
          completed: report.totalSubmissions,
          abandoned: report.abandonedAtFlow,
          defaultValue:
            "{{parked}} parked · {{completed}} completed · {{abandoned}} abandoned",
        })}
      />
      <SectionCard.Content>
        <ul className="space-y-2">
          {report.nodes.map((node) => (
            <FunnelRow key={node.nodeId} node={node} maxCount={maxCount} />
          ))}
        </ul>
      </SectionCard.Content>
    </SectionCard>
  );
}

function FunnelRow({
  node,
  maxCount,
}: Readonly<{ node: TurChatFlowFunnelNode; maxCount: number }>) {
  const total = node.cursorCount + node.completedCount;
  const widthPct = Math.round((total / maxCount) * 100);
  return (
    <li className="rounded-md border border-border/60 bg-background/60 p-2 space-y-1">
      <div className="flex items-center gap-2 flex-wrap">
        <Badge variant="outline" className="font-mono text-[10px]">
          {node.type}
        </Badge>
        <span className="font-medium text-sm truncate" title={node.label}>
          {node.label || node.nodeId}
        </span>
        <span className="text-xs text-muted-foreground ml-auto shrink-0 font-mono">
          {node.cursorCount > 0 && (
            <span className="text-amber-600 dark:text-amber-400" title="parked">
              ●{node.cursorCount}
            </span>
          )}
          {node.completedCount > 0 && (
            <span className="text-emerald-600 dark:text-emerald-400 ml-2" title="completed">
              ✓{node.completedCount}
            </span>
          )}
          {total === 0 && <span className="opacity-60">—</span>}
        </span>
      </div>
      <div className="h-1.5 rounded-full bg-muted/40 overflow-hidden">
        <div
          className="h-full bg-gradient-to-r from-blue-500 to-indigo-500"
          style={{ width: `${widthPct}%` }}
        />
      </div>
    </li>
  );
}
