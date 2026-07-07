import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

import { useChatFlowTriggerConflicts } from "@/api/queries/chat-flow.queries";
import { ChatFlowTriggerConflictsPanel } from "@/app/console/chat-flow/components/chat-flow.trigger-conflicts-panel";
import { IconShieldCheck } from "@tabler/icons-react";

/**
 * Standalone "Trigger ambiguity" page — agent-scoped view of every pair of
 * chat flows whose trigger descriptions overlap enough that the procedural
 * router may swing between them turn-to-turn. Lives under the agent's
 * "Analysis" sidebar group; the chat-flow editor only ever showed conflicts
 * for the flow being edited, this shows them all.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
export default function AIAgentTriggerConflictsPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const { data: conflicts } = useChatFlowTriggerConflicts(id);
  const isEmpty = !conflicts || conflicts.length === 0;

  return (
    <div className="px-4 lg:px-6 py-2 pb-8">
      {isEmpty ? (
        <div className="flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed py-16 text-muted-foreground">
          <IconShieldCheck className="h-12 w-12 text-emerald-500/70" />
          <h3 className="text-lg font-semibold text-foreground">
            {t("chatFlow.triggerConflicts.emptyTitle", {
              defaultValue: "No trigger ambiguity detected",
            })}
          </h3>
          <p className="max-w-md text-center text-sm">
            {t("chatFlow.triggerConflicts.emptyDescription", {
              defaultValue:
                "The procedural router can separate this agent's chat flows on their trigger descriptions. New overlaps will surface here.",
            })}
          </p>
        </div>
      ) : (
        <ChatFlowTriggerConflictsPanel agentId={id} />
      )}
    </div>
  );
}
