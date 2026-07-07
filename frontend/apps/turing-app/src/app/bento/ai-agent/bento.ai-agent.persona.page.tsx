import { useAiAgent } from "@/api/queries/ai-agent.queries";
import { ROUTES } from "@/app/routes.const";
import { AIAgentPersonaForm } from "@/components/agent/ai-agent.persona.form";
import { LoadProvider } from "@/components/loading-provider";
import { IconUserCircle } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BentoAgentSubHero } from "./bento.ai-agent.sub-hero";

/**
 * Bento wrapper for the agent's persona catalog + default-persona picker. The
 * form is self-contained (its own save/reset footer, no console chrome), so it
 * is reused verbatim below the shared bento hero.
 */
export default function BentoAIAgentPersonaPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const { data: agent, isError } = useAiAgent(id);
  const error = isError ? t("common.connectionError", { resource: t("aiAgent.title").toLowerCase() }) : null;

  return (
    <LoadProvider checkIsNotUndefined={agent} error={error} tryAgainUrl={`${ROUTES.BENTO_AI_AGENT_INSTANCE}/${id}/persona`}>
      <BentoAgentSubHero
        agentId={id}
        agentTitle={agent?.title ?? t("aiAgent.title")}
        icon={IconUserCircle}
        tone="violet"
        title={t("aiAgent.persona.title")}
        subtitle={t("aiAgent.persona.description")}
      />
      {agent && <AIAgentPersonaForm value={agent} chrome="bento" />}
    </LoadProvider>
  );
}
