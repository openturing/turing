import { useAiAgent } from "@/api/queries/ai-agent.queries";
import { ROUTES } from "@/app/routes.const";
import { AIAgentSystemPromptForm } from "@/components/agent/ai-agent.system-prompt.form";
import { LoadProvider } from "@/components/loading-provider";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

/**
 * Bento wrapper for the agent's system-prompt editor + conflict-check. The
 * form drops its console StickyPageHeader under `chrome="bento"` (the hero
 * below stands in) and routes Cancel back into the bento shell via baseRoute.
 */
export default function BentoAIAgentSystemPromptPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const { data: agent, isError } = useAiAgent(id);
  const error = isError ? t("common.connectionError", { resource: t("aiAgent.title").toLowerCase() }) : null;

  return (
    <LoadProvider checkIsNotUndefined={agent} error={error} tryAgainUrl={`${ROUTES.BENTO_AI_AGENT_INSTANCE}/${id}/system-prompt`}>
      {agent && (
        <AIAgentSystemPromptForm value={agent} chrome="bento" baseRoute={ROUTES.BENTO_AI_AGENT_INSTANCE} />
      )}
    </LoadProvider>
  );
}
