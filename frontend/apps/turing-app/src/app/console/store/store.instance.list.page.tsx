import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurStoreInstance } from "@/models/store/store-instance.model.ts";
import { TurStoreInstanceService } from "@/services/store/store.service";
import { IconDatabase } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

const turStoreInstanceService = new TurStoreInstanceService();

export default function StoreInstanceListPage() {
  const { t } = useTranslation();
  const [storeInstances, setStoreInstances] = useState<TurStoreInstance[]>();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    turStoreInstanceService.query().then(setStoreInstances).catch(() => setError(t("common.connectionError", { resource: "instances" })));
  }, [])
  const gridItemList = useGridAdapter(storeInstances, {
    name: "title",
    description: "description",
    url: (item) => `${ROUTES.STORE_INSTANCE}/${item.id}`,
    icon: "icon",
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


