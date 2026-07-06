import { useAiAgent } from "@/api/queries/ai-agent.queries";
import { ROUTES } from "@/app/routes.const";
import { AIAgentRequestOptionsForm } from "@/components/agent/ai-agent.request-options.form";
import { LoadProvider } from "@/components/loading-provider";
import { IconAdjustments } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BentoAgentSubHero } from "./bento.ai-agent.sub-hero";

/**
 * Bento wrapper for the agent's per-provider request options. The form is
 * self-contained (its own save/reset footer, no console chrome), so it is
 * reused verbatim below the shared bento hero.
 */
export default function BentoAIAgentRequestOptionsPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const { data: agent, isError } = useAiAgent(id);
  const error = isError ? t("common.connectionError", { resource: t("aiAgent.title").toLowerCase() }) : null;

  return (
    <LoadProvider checkIsNotUndefined={agent} error={error} tryAgainUrl={`${ROUTES.BENTO_AI_AGENT_INSTANCE}/${id}/request-options`}>
      <BentoAgentSubHero
        agentId={id}
        agentTitle={agent?.title ?? t("aiAgent.title")}
        icon={IconAdjustments}
        tone="slate"
        title={t("aiAgent.nav.requestOptions")}
        subtitle={t("forms.agentRequestOptions.description", {
          defaultValue: "Provider-specific request parameters sent with every model call.",
        })}
      />
      {agent && <AIAgentRequestOptionsForm value={agent} chrome="bento" />}
    </LoadProvider>
  );
}
