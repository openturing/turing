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

const turIntentService = new TurIntentService();

export default function IntentListPage() {
  const { t } = useTranslation();
  const [intents, setIntents] = useState<TurIntent[]>();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    turIntentService.query().then(setIntents).catch(() => setError(t("common.connectionError", { resource: "intents" })));
  }, [])

  const gridItemList = useGridAdapter(intents, {
    name: "title",
    description: "description",
    url: (item) => `${ROUTES.INTENT_INSTANCE}/${item.id}`,
    icon: "icon",
  });

  return (
    <LoadProvider checkIsNotUndefined={intents} error={error} tryAgainUrl={ROUTES.INTENT_INSTANCE}>
      {gridItemList.length > 0 ? (
        <GridList gridItemList={gridItemList}>
          <GridList.NewButton to={`${ROUTES.INTENT_INSTANCE}/new`} label={t("intent.title")} />
        </GridList>
      ) : (
        <BlankSlate
          icon={IconBulb}
          title={t("intent.blankTitle")}
          description={t("intent.blankDescription")}
          buttonText={t("intent.newInstance")}
          urlNew={`${ROUTES.INTENT_INSTANCE}/new`} />
      )}
    </LoadProvider>
  )
}
