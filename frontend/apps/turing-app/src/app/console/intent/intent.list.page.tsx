import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurIntent } from "@/models/intent/intent.model";
import { TurIntentService } from "@/services/intent/intent.service";
import { IconBulb } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const turIntentService = new TurIntentService();

export default function IntentListPage() {
  const { t } = useTranslation();
  const { id: agentId } = useParams();
  const [intents, setIntents] = useState<TurIntent[]>();
  const [error, setError] = useState<string | null>(null);
  const baseUrl = `${ROUTES.AI_AGENT_INSTANCE}/${agentId}/intent`;

  useEffect(() => {
    if (!agentId) return;
    turIntentService.query(agentId).then(setIntents).catch(() => setError(t("common.connectionError", { resource: "intents" })));
  }, [agentId])

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
    </LoadProvider>
  )
}
