import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurAIAgent } from "@/models/agent/ai-agent.model.ts";
import { TurAIAgentService } from "@/services/agent/ai-agent.service";
import { IconRobot } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

const turAIAgentService = new TurAIAgentService();

export default function AIAgentListPage() {
  const { t } = useTranslation();
  const [agents, setAgents] = useState<TurAIAgent[]>();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    turAIAgentService.query().then(setAgents).catch(() => setError(t("common.connectionError", { resource: "AI agents" })));
  }, [])
  const gridItemList = useGridAdapter(agents, {
    name: "title",
    description: "description",
    url: (item) => `${ROUTES.AI_AGENT_INSTANCE}/${item.id}`,
    icon: "icon",
  });
  return (
    <LoadProvider checkIsNotUndefined={agents} error={error} tryAgainUrl={`${ROUTES.AI_AGENT_INSTANCE}`}>
      {gridItemList.length > 0 ? (
        <GridList gridItemList={gridItemList}>
          <GridList.NewButton to={`${ROUTES.AI_AGENT_INSTANCE}/new`} label={t("aiAgent.title")} />
        </GridList>
      ) : (
        <BlankSlate
          icon={IconRobot}
          title={t("aiAgent.blankTitle")}
          description={t("aiAgent.blankDescription")}
          buttonText={t("aiAgent.newInstance")}
          urlNew={`${ROUTES.AI_AGENT_INSTANCE}/new`} />
      )}
    </LoadProvider>
  )
}
