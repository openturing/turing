import { useAiAgent } from "@/api/queries/ai-agent.queries";
import AIAgentTriggerConflictsContent from "@/app/console/ai-agent/trigger-conflicts/ai-agent.trigger-conflicts.page";
import { IconAlertTriangle } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BentoAgentSubHero } from "./bento.ai-agent.sub-hero";

/**
 * Bento wrapper for the agent's "Trigger ambiguity" analysis page — every pair
 * of chat flows whose trigger descriptions overlap enough that the procedural
 * router may swing between them. The content component is self-contained (no
 * console sidebar chrome), so it is reused verbatim below the shared bento hero.
 */
export default function BentoAIAgentTriggerConflictsPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const { data: agent } = useAiAgent(id);

  return (
    <>
      <BentoAgentSubHero
        agentId={id}
        agentTitle={agent?.title ?? t("aiAgent.title")}
        icon={IconAlertTriangle}
        tone="amber"
        title={t("aiAgent.nav.triggerConflicts")}
        subtitle={t("aiAgent.triggerConflicts.description", {
          defaultValue: "Chat flows whose trigger descriptions overlap enough that the router may swing between them.",
        })}
      />
      <AIAgentTriggerConflictsContent />
    </>
  );
}
