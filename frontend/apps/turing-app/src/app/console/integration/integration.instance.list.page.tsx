import { useFeatures } from "@/api/queries/features.queries";
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
  const { data: features } = useFeatures();
  const tenancyEnabled = features?.tenancyEnabled === true;
  // T372 — label shared GLOBAL pool rows (tenantId == null) when tenancy is on.
  const globalLabel = t("forms.common.global", { defaultValue: "Global" });
  const gridItemList = useGridAdapter(integrationInstances, {
    name: "title",
    description: "description",
    url: (item) => `${ROUTES.INTEGRATION_INSTANCE}/${item.id}`,
    icon: "icon",
    badge: (item) => {
      if (tenancyEnabled && item.tenantId == null) return globalLabel;
      return null;
    },
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


