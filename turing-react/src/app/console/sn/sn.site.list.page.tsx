import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurSNSite } from "@/models/sn/sn-site.model.ts";
import { TurSNSiteService } from "@/services/sn/sn.service";
import { IconSearch } from "@tabler/icons-react";
import { useEffect, useState } from "react";

const turSNSiteService = new TurSNSiteService();

export default function SNSiteListPage() {
  const [snInstances, setSnInstances] = useState<TurSNSite[]>();
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    turSNSiteService.query().then(setSnInstances).catch(() => setError("Connection error or timeout while fetching instances."));
  }, [])
  const gridItemList = useGridAdapter(snInstances, {
    name: "name",
    description: "description",
    url: (item) => `${ROUTES.SN_INSTANCE}/${item.id}`
  });
  return (
    <LoadProvider checkIsNotUndefined={snInstances} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}`}>
      {gridItemList.length > 0 ? (
        <GridList gridItemList={gridItemList ?? []} />
      ) : (
        <BlankSlate
          icon={IconSearch}
          title="You don’t seem to have any semantic navigation instance."
          description="Create a new instance to search and navigate through your documents."
          buttonText="New semantic navigation instance"
          urlNew={`${ROUTES.SN_INSTANCE}/new`} />
      )}
    </LoadProvider>
  )
}


