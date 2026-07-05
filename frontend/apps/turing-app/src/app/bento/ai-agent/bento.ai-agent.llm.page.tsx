import { useAiAgent } from "@/api/queries/ai-agent.queries";
import { ROUTES } from "@/app/routes.const";
import { AIAgentLlmForm } from "@/components/agent/ai-agent.llm.form";
import { LoadProvider } from "@/components/loading-provider";
import { IconCpu2 } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BentoAgentSubHero } from "./bento.ai-agent.sub-hero";

export default function BentoAIAgentLlmPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const { data: agent, isError } = useAiAgent(id);
  const error = isError ? t("common.connectionError", { resource: t("aiAgent.title").toLowerCase() }) : null;

  return (
    <LoadProvider checkIsNotUndefined={agent} error={error} tryAgainUrl={`${ROUTES.BENTO_AI_AGENT_INSTANCE}/${id}/llm`}>
      <BentoAgentSubHero
        agentId={id}
        agentTitle={agent?.title ?? t("aiAgent.title")}
        icon={IconCpu2}
        tone="violet"
        title={t("aiAgent.llm.title")}
        subtitle={t("aiAgent.llm.description")}
      />
      {agent && <AIAgentLlmForm value={agent} chrome="bento" />}
    </LoadProvider>
  );
}
