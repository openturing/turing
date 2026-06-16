import { useFeatures } from "@/api/queries/features.queries";
import { useStoreInstances } from "@/api/queries/store-instance.queries";
import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import { IconDatabase } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function StoreInstanceListPage() {
  const { t } = useTranslation();
  const { data: storeInstances, isError } = useStoreInstances();
  const error = isError ? t("common.connectionError", { resource: "instances" }) : null;
  const { data: features } = useFeatures();
  const tenancyEnabled = features?.tenancyEnabled === true;
  // T372 — label shared GLOBAL pool rows (tenantId == null) when tenancy is on.
  const globalLabel = t("forms.common.global", { defaultValue: "Global" });
  const gridItemList = useGridAdapter(storeInstances, {
    name: "title",
    description: "description",
    url: (item) => `${ROUTES.STORE_INSTANCE}/${item.id}`,
    icon: "icon",
    badge: (item) => {
      if (tenancyEnabled && item.tenantId == null) return globalLabel;
      return null;
    },
  });
  return (
    <LoadProvider checkIsNotUndefined={storeInstances} error={error} tryAgainUrl={`${ROUTES.STORE_INSTANCE}`}>
      {gridItemList.length > 0 ? (
        <GridList gridItemList={gridItemList}>
          <GridList.NewButton to={`${ROUTES.STORE_INSTANCE}/new`} label={t("store.title")} />
        </GridList>
      ) : (
        <BlankSlate
          icon={IconDatabase}
          title={t("store.blankTitle")}
          description={t("store.blankDescription")}
          buttonText={t("store.newInstance")}
          urlNew={`${ROUTES.STORE_INSTANCE}/new`} />
      )}
    </LoadProvider>
  )
}


