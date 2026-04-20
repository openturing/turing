import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurIntegrationInstance } from "@/models/integration/integration-instance.model.ts";
import { TurIntegrationInstanceService } from "@/services/integration/integration.service";
import { IconPlugConnectedX } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

const turIntegrationInstanceService = new TurIntegrationInstanceService();

export default function IntegrationInstanceListPage() {
  const { t } = useTranslation();
  const [integrationInstances, setIntegrationInstances] = useState<TurIntegrationInstance[]>();
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    turIntegrationInstanceService.query().then(setIntegrationInstances).catch(() => setError(t("common.connectionError", { resource: "instances" })));
  }, [])
  const gridItemList = useGridAdapter(integrationInstances, {
    name: "title",
    description: "description",
    url: (item) => `${ROUTES.INTEGRATION_INSTANCE}/${item.id}`,
    icon: "icon",
  });
  return (
    <LoadProvider checkIsNotUndefined={integrationInstances} error={error} tryAgainUrl={`${ROUTES.INTEGRATION_INSTANCE}`}>
      {gridItemList.length > 0 ? (
        <GridList gridItemList={gridItemList}>
          <GridList.NewButton to={`${ROUTES.INTEGRATION_INSTANCE}/new`} label={t("integration.title")} />
        </GridList>
      ) : (
        <BlankSlate
          icon={IconPlugConnectedX}
          title={t("integration.blankTitle")}
          description={t("integration.blankDescription")}
          buttonText={t("integration.newInstance")}
          urlNew={`${ROUTES.INTEGRATION_INSTANCE}/new`} />
      )}
    </LoadProvider>
  )
}


