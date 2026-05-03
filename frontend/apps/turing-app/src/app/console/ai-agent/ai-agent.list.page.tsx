import { useAiAgents } from "@/api/queries/ai-agent.queries";
import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import { IconRobot } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function AIAgentListPage() {
  const { t } = useTranslation();
  const { data: agents, isError } = useAiAgents();
  const error = isError ? t("common.connectionError", { resource: "AI agents" }) : null;
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
