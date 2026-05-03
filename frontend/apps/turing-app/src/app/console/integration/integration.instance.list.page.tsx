import { useIntegrationInstances } from "@/api/queries/integration-instance.queries";
import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import { IconPlugConnectedX } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function IntegrationInstanceListPage() {
  const { t } = useTranslation();
  const { data: integrationInstances, isError } = useIntegrationInstances();
  const error = isError ? t("common.connectionError", { resource: "instances" }) : null;
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


