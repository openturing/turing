import { useAiAgent } from "@/api/queries/ai-agent.queries";
import { ROUTES } from "@/app/routes.const";
import { AIAgentMcpForm } from "@/components/agent/ai-agent.mcp.form";
import { LoadProvider } from "@/components/loading-provider";
import { IconServer2 } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BentoAgentSubHero } from "./bento.ai-agent.sub-hero";

export default function BentoAIAgentMcpPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const { data: agent, isError } = useAiAgent(id);
  const error = isError ? t("common.connectionError", { resource: t("aiAgent.title").toLowerCase() }) : null;

  return (
    <LoadProvider checkIsNotUndefined={agent} error={error} tryAgainUrl={`${ROUTES.BENTO_AI_AGENT_INSTANCE}/${id}/mcp`}>
      <BentoAgentSubHero
        agentId={id}
        agentTitle={agent?.title ?? t("aiAgent.title")}
        icon={IconServer2}
        tone="amber"
        title={t("aiAgent.mcp.title")}
        subtitle={t("aiAgent.mcp.description")}
      />
      {agent && <AIAgentMcpForm value={agent} chrome="bento" />}
    </LoadProvider>
  );
}
