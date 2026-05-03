import { useChatFlows } from "@/api/queries/chat-flow.queries";
import { ROUTES } from "@/app/routes.const";
import { AiAuthoringTrigger } from "@/components/ai-authoring/ai-authoring-trigger";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import { IconSitemap } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

/**
 * Lists chat flows that belong to the AI agent currently in the URL.
 *
 * @since 2026.2.5
 */
export default function ChatFlowListPage() {
  const { t } = useTranslation();
  const { id: agentId } = useParams() as { id: string };
  const { data: flows, isError } = useChatFlows(agentId);
  const error = isError ? t("common.connectionError", { resource: "chat flows" }) : null;
  const baseUrl = `${ROUTES.AI_AGENT_INSTANCE}/${agentId}/chat-flow`;

  const gridItemList = useGridAdapter(flows, {
    name: "name",
    description: "description",
    url: (item) => `${baseUrl}/${item.id}`,
  });

  return (
    <LoadProvider checkIsNotUndefined={flows} error={error} tryAgainUrl={baseUrl}>
      {gridItemList.length > 0 ? (
        <GridList gridItemList={gridItemList}>
          <GridList.NewButton to={`${baseUrl}/new`} label={t("chatFlow.title")} />
        </GridList>
      ) : (
        <BlankSlate
          icon={IconSitemap}
          title={t("chatFlow.blankTitle")}
          description={t("chatFlow.blankDescription")}
          buttonText={t("chatFlow.newInstance")}
          urlNew={`${baseUrl}/new`}
        />
      )}
      <AiAuthoringTrigger to={`${baseUrl}/new/ai-chat`} />
    </LoadProvider>
  );
}
