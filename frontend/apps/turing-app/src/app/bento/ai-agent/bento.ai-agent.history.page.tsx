import { useAiAgent } from "@/api/queries/ai-agent.queries";
import AIAgentHistoryContent from "@/app/console/ai-agent/history/ai-agent.history.page";
import { IconHistory } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BentoAgentSubHero } from "./bento.ai-agent.sub-hero";

/**
 * Bento wrapper for the agent's chat-flow submission history. The history
 * content component is self-contained (no console sidebar chrome), so it is
 * reused verbatim below the shared bento hero.
 */
export default function BentoAIAgentHistoryPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const { data: agent } = useAiAgent(id);

  return (
    <>
      <BentoAgentSubHero
        agentId={id}
        agentTitle={agent?.title ?? t("aiAgent.title")}
        icon={IconHistory}
        tone="slate"
        title={t("aiAgent.nav.history")}
        subtitle={t("aiAgent.history.description", {
          defaultValue: "Past conversations grouped by completed flow runs.",
        })}
      />
      <AIAgentHistoryContent />
    </>
  );
}
