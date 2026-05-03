import { useIntents } from "@/api/queries/intent.queries";
import { ROUTES } from "@/app/routes.const";
import { AiAuthoringTrigger } from "@/components/ai-authoring/ai-authoring-trigger";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import { IconBulb } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

export default function IntentListPage() {
  const { t } = useTranslation();
  const { id: agentId } = useParams();
  const { data: intents, isError } = useIntents(agentId);
  const error = isError ? t("common.connectionError", { resource: "intents" }) : null;
  const baseUrl = `${ROUTES.AI_AGENT_INSTANCE}/${agentId}/intent`;

  const gridItemList = useGridAdapter(intents, {
    name: "title",
    description: "description",
    url: (item) => `${baseUrl}/${item.id}`,
    icon: "icon",
  });

  return (
    <LoadProvider checkIsNotUndefined={intents} error={error} tryAgainUrl={baseUrl}>
      {gridItemList.length > 0 ? (
        <GridList gridItemList={gridItemList}>
          <GridList.NewButton to={`${baseUrl}/new`} label={t("intent.title")} />
        </GridList>
      ) : (
        <BlankSlate
          icon={IconBulb}
          title={t("intent.blankTitle")}
          description={t("intent.blankDescription")}
          buttonText={t("intent.newInstance")}
          urlNew={`${baseUrl}/new`} />
      )}
      <AiAuthoringTrigger to={`${baseUrl}/new/ai-chat`} />
    </LoadProvider>
  )
}
