import { ROUTES } from "@/app/routes.const";
import { AiAuthoringTrigger } from "@/components/ai-authoring/ai-authoring-trigger";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurChatFlow } from "@/models/agent/chat-flow.model";
import { TurChatFlowService } from "@/services/agent/chat-flow.service";
import { IconSitemap } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

/**
 * Lists chat flows that belong to the AI agent currently in the URL.
 *
 * @since 2026.2.5
 */
const turChatFlowService = new TurChatFlowService();

export default function ChatFlowListPage() {
  const { t } = useTranslation();
  const { id: agentId } = useParams() as { id: string };
  const [flows, setFlows] = useState<TurChatFlow[]>();
  const [error, setError] = useState<string | null>(null);
  const baseUrl = `${ROUTES.AI_AGENT_INSTANCE}/${agentId}/chat-flow`;

  useEffect(() => {
    if (!agentId) return;
    turChatFlowService
      .query(agentId)
      .then(setFlows)
      .catch(() => setError(t("common.connectionError", { resource: "chat flows" })));
  }, [agentId, t]);

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
