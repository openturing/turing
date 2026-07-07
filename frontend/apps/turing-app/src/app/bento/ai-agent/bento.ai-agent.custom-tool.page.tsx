import { useAiAgent } from "@/api/queries/ai-agent.queries";
import { ROUTES } from "@/app/routes.const";
import { AIAgentCustomToolForm } from "@/components/agent/ai-agent.custom-tool.form";
import { LoadProvider } from "@/components/loading-provider";
import { IconBraces } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BentoAgentSubHero } from "./bento.ai-agent.sub-hero";

export default function BentoAIAgentCustomToolPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const { data: agent, isError } = useAiAgent(id);
  const error = isError ? t("common.connectionError", { resource: t("aiAgent.title").toLowerCase() }) : null;

  return (
    <LoadProvider checkIsNotUndefined={agent} error={error} tryAgainUrl={`${ROUTES.BENTO_AI_AGENT_INSTANCE}/${id}/custom-tool`}>
      <BentoAgentSubHero
        agentId={id}
        agentTitle={agent?.title ?? t("aiAgent.title")}
        icon={IconBraces}
        tone="rose"
        title={t("aiAgent.customTool.title")}
        subtitle={t("aiAgent.customTool.description")}
      />
      {agent && <AIAgentCustomToolForm value={agent} chrome="bento" />}
    </LoadProvider>
  );
}
