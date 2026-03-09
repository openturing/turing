import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurStoreInstance } from "@/models/store/store-instance.model.ts";
import { TurStoreInstanceService } from "@/services/store/store.service";
import { IconDatabase } from "@tabler/icons-react";
import { useEffect, useState } from "react";

const turStoreInstanceService = new TurStoreInstanceService();

export default function StoreInstanceListPage() {
  const [storeInstances, setStoreInstances] = useState<TurStoreInstance[]>();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    turStoreInstanceService.query().then(setStoreInstances).catch(() => setError("Connection error or timeout while fetching instances."));
  }, [])
  const gridItemList = useGridAdapter(storeInstances, {
    name: "title",
    description: "description",
    url: (item) => `${ROUTES.STORE_INSTANCE}/${item.id}`
  });
  return (
    <LoadProvider checkIsNotUndefined={storeInstances} error={error} tryAgainUrl={`${ROUTES.STORE_INSTANCE}`}>
      {gridItemList.length > 0 ? (
        <GridList gridItemList={gridItemList} />
      ) : (
        <BlankSlate
          icon={IconDatabase}
          title="You don’t seem to have any embedding store instance."
          description="Create a new instance and use it in semantic navigation and chatbot."
          buttonText="New embedding store instance"
          urlNew={`${ROUTES.STORE_INSTANCE}/new`} />
      )}
    </LoadProvider>
  )
}


