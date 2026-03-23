import { ROUTES } from "@/app/routes.const";
import { AIAgentToolsForm } from "@/components/agent/ai-agent.tools.form";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import type { TurAIAgent } from "@/models/agent/ai-agent.model.ts";
import { TurAIAgentService } from "@/services/agent/ai-agent.service";
import { IconTool } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const turAIAgentService = new TurAIAgentService();

export default function AIAgentToolsPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const [agent, setAgent] = useState<TurAIAgent>();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    turAIAgentService.get(id)
      .then(setAgent)
      .catch(() => setError(t("common.connectionError", { resource: t("aiAgent.title").toLowerCase() })));
  }, [id]);

  return (
    <LoadProvider checkIsNotUndefined={agent} error={error} tryAgainUrl={`${ROUTES.AI_AGENT_INSTANCE}/${id}/tools`}>
      <SubPageHeader icon={IconTool} name={t("aiAgent.tools.title")} feature={t("aiAgent.tools.title")}
        description={t("aiAgent.tools.description")} />
      {agent && <AIAgentToolsForm value={agent} />}
    </LoadProvider>
  );
}
