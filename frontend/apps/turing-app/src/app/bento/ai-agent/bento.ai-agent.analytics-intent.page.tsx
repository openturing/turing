import { useAiAgent } from "@/api/queries/ai-agent.queries";
import AnalyticsIntentListContent from "@/app/console/analytics-intent/analytics-intent.list.page";
import { IconChartBar } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BentoAgentSubHero } from "./bento.ai-agent.sub-hero";

/**
 * Bento wrapper for the agent's "Analytics intents" page — the per-agent
 * analytics intent catalog used by the MLT intent classifier. The list content
 * component is self-contained (no console sidebar chrome), so it is reused
 * verbatim below the shared bento hero.
 */
export default function BentoAIAgentAnalyticsIntentPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const { data: agent } = useAiAgent(id);

  return (
    <>
      <BentoAgentSubHero
        agentId={id}
        agentTitle={agent?.title ?? t("aiAgent.title")}
        icon={IconChartBar}
        tone="blue"
        title={t("aiAgent.nav.analyticsIntent")}
        subtitle={t("analyticsIntent.description")}
      />
      <AnalyticsIntentListContent />
    </>
  );
}
