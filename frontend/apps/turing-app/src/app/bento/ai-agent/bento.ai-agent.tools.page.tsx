import { useAiAgent } from "@/api/queries/ai-agent.queries";
import { ROUTES } from "@/app/routes.const";
import { AIAgentToolsForm } from "@/components/agent/ai-agent.tools.form";
import { LoadProvider } from "@/components/loading-provider";
import { IconTool } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BentoAgentSubHero } from "./bento.ai-agent.sub-hero";

export default function BentoAIAgentToolsPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const { data: agent, isError } = useAiAgent(id);
  const error = isError ? t("common.connectionError", { resource: t("aiAgent.title").toLowerCase() }) : null;

  return (
    <LoadProvider checkIsNotUndefined={agent} error={error} tryAgainUrl={`${ROUTES.BENTO_AI_AGENT_INSTANCE}/${id}/tools`}>
      <BentoAgentSubHero
        agentId={id}
        agentTitle={agent?.title ?? t("aiAgent.title")}
        icon={IconTool}
        tone="emerald"
        title={t("aiAgent.tools.title")}
        subtitle={t("aiAgent.tools.description")}
      />
      {agent && <AIAgentToolsForm value={agent} chrome="bento" />}
    </LoadProvider>
  );
}
