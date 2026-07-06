import { useAiAgent } from "@/api/queries/ai-agent.queries";
import { ROUTES } from "@/app/routes.const";
import { AIAgentCapabilitiesForm } from "@/components/agent/ai-agent.capabilities.form";
import { LoadProvider } from "@/components/loading-provider";
import { IconSparkles } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BentoAgentSubHero } from "./bento.ai-agent.sub-hero";

/**
 * Bento wrapper for the agent's native tools & capabilities picker. The form is
 * self-contained (its own save/reset footer, no console chrome), so it is
 * reused verbatim below the shared bento hero.
 */
export default function BentoAIAgentCapabilitiesPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const { data: agent, isError } = useAiAgent(id);
  const error = isError ? t("common.connectionError", { resource: t("aiAgent.title").toLowerCase() }) : null;

  return (
    <LoadProvider checkIsNotUndefined={agent} error={error} tryAgainUrl={`${ROUTES.BENTO_AI_AGENT_INSTANCE}/${id}/capabilities`}>
      <BentoAgentSubHero
        agentId={id}
        agentTitle={agent?.title ?? t("aiAgent.title")}
        icon={IconSparkles}
        tone="blue"
        title={t("aiAgent.nav.capabilities")}
        subtitle={t("forms.agentCapabilities.description")}
      />
      {agent && <AIAgentCapabilitiesForm value={agent} chrome="bento" />}
    </LoadProvider>
  );
}
