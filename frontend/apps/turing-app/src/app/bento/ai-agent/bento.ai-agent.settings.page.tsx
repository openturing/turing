import { useAiAgent } from "@/api/queries/ai-agent.queries";
import { ROUTES } from "@/app/routes.const";
import { AIAgentSettingsForm } from "@/components/agent/ai-agent.settings.form";
import { LoadProvider } from "@/components/loading-provider";
import type { TurAIAgent } from "@/models/agent/ai-agent.model.ts";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

export default function BentoAIAgentSettingsPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const isNew = id === "new";

  const { data: agent, isError } = useAiAgent(isNew ? undefined : id);
  const error = isError ? t("common.connectionError", { resource: t("aiAgent.title").toLowerCase() }) : null;
  const value = isNew ? ({} as TurAIAgent) : agent;

  return (
    <LoadProvider checkIsNotUndefined={value} error={error} tryAgainUrl={`${ROUTES.BENTO_AI_AGENT_INSTANCE}/${id}/settings`}>
      {value && (
        <AIAgentSettingsForm value={value} isNew={isNew} chrome="bento" baseRoute={ROUTES.BENTO_AI_AGENT_INSTANCE} />
      )}
    </LoadProvider>
  );
}
