import { useChatFlowTriggerConflicts } from "@/api/queries/chat-flow.queries";
import { SectionCard } from "@/components/ui/section-card";
import { Badge } from "@/components/ui/badge";
import type { TurChatFlowTriggerConflict } from "@/models/agent/chat-flow.model";
import { IconAlertTriangle } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

import { ROUTES } from "@/app/routes.const";

interface ChatFlowTriggerConflictsPanelProps {
  agentId: string;
  /**
   * When set, filters the panel down to conflicts involving this flow. Used in
   * the editor; the list page passes {@code undefined} to show every pair.
   */
  flowId?: string;
}

/**
 * T91 / §VII.11.a — agent-scoped trigger-description conflict panel.
 *
 * <p>The procedural router decides silently between flows by specificity
 * (1.5× dominance ratio over Lucene MoreLikeThis scores). When two trigger
 * descriptions overlap heavily, that decision can swing turn-by-turn on small
 * scoring deltas and the admin never sees it — until a customer complains.
 * This panel surfaces the conflict at authoring time so the author can
 * disambiguate before deploying.
 *
 * <p>Rendered with the {@code rose} variant when any HIGH-severity conflict is
 * present (procedural router will likely defer to LLM most turns), {@code amber}
 * otherwise (WARNING — survivable overlap).
 *
 * @since 2026.3.1
 */
export function ChatFlowTriggerConflictsPanel({
  agentId,
  flowId,
}: Readonly<ChatFlowTriggerConflictsPanelProps>) {
  const { t } = useTranslation();
  const { data: allConflicts } = useChatFlowTriggerConflicts(agentId);

  const conflicts = (allConflicts ?? []).filter((c) =>
    flowId ? c.flowAId === flowId || c.flowBId === flowId : true,
  );

  if (conflicts.length === 0) {
    return null;
  }

  const hasHigh = conflicts.some((c) => c.severity === "HIGH");
  const variant: "rose" | "amber" = hasHigh ? "rose" : "amber";

  return (
    <SectionCard variant={variant}>
      <SectionCard.Header
        icon={IconAlertTriangle}
        title={t("chatFlow.triggerConflicts.title", {
          defaultValue: "Trigger description ambiguity",
        })}
        description={t("chatFlow.triggerConflicts.description", {
          defaultValue:
            "The procedural router cannot reliably separate these flows on overlapping user messages — it falls back to the LLM router each time. Add distinguishing vocabulary or a negative-list clause to one side.",
        })}
      />
      <SectionCard.Content>
        <ul className="space-y-3">
          {conflicts.map((conflict) => (
            <ConflictRow key={`${conflict.flowAId}-${conflict.flowBId}`}
              agentId={agentId}
              flowId={flowId}
              conflict={conflict}
            />
          ))}
        </ul>
      </SectionCard.Content>
    </SectionCard>
  );
}

function ConflictRow({
  agentId,
  flowId,
  conflict,
}: Readonly<{
  agentId: string;
  flowId?: string;
  conflict: TurChatFlowTriggerConflict;
}>) {
  const baseUrl = `${ROUTES.AI_AGENT_INSTANCE}/${agentId}/chat-flow`;
  // Surface the "other" flow first when we are inside the editor of one of
  // the two — the author already knows what they're editing, the actionable
  // bit is which OTHER flow is colliding.
  const isAEditing = flowId === conflict.flowAId;
  const primary = isAEditing
    ? { id: conflict.flowBId, name: conflict.flowBName }
    : { id: conflict.flowAId, name: conflict.flowAName };
  const secondary = isAEditing
    ? { id: conflict.flowAId, name: conflict.flowAName }
    : { id: conflict.flowBId, name: conflict.flowBName };
  const percent = Math.round(conflict.similarity * 100);
  const isHigh = conflict.severity === "HIGH";

  return (
    <li className="rounded-md border border-border/60 bg-background/60 p-3 space-y-2">
      <div className="flex flex-wrap items-center gap-2">
        <Badge variant={isHigh ? "destructive" : "secondary"} className="text-[10px] uppercase">
          {conflict.severity}
        </Badge>
        <span className="text-xs text-muted-foreground">
          {percent}% overlap · {conflict.intersectionSize} shared stems
        </span>
      </div>
      <div className="text-sm">
        {flowId ? (
          <>
            Collides with{" "}
            <Link
              to={`${baseUrl}/${primary.id}`}
              className="font-medium text-blue-600 hover:underline dark:text-blue-400"
            >
              {primary.name}
            </Link>
          </>
        ) : (
          <>
            <Link
              to={`${baseUrl}/${primary.id}`}
              className="font-medium text-blue-600 hover:underline dark:text-blue-400"
            >
              {primary.name}
            </Link>
            {" ⇄ "}
            <Link
              to={`${baseUrl}/${secondary.id}`}
              className="font-medium text-blue-600 hover:underline dark:text-blue-400"
            >
              {secondary.name}
            </Link>
          </>
        )}
      </div>
      {conflict.overlappingTokens.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {conflict.overlappingTokens.map((token) => (
            <Badge key={token} variant="outline" className="font-mono text-[11px]">
              {token}
            </Badge>
          ))}
        </div>
      )}
      <p className="text-xs text-muted-foreground">{conflict.suggestion}</p>
    </li>
  );
}
