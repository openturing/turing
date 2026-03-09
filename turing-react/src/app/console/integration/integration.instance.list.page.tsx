import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurIntegrationInstance } from "@/models/integration/integration-instance.model.ts";
import { TurIntegrationInstanceService } from "@/services/integration/integration.service";
import { IconPlugConnectedX } from "@tabler/icons-react";
import { useEffect, useState } from "react";

const turIntegrationInstanceService = new TurIntegrationInstanceService();

export default function IntegrationInstanceListPage() {
  const [integrationInstances, setIntegrationInstances] = useState<TurIntegrationInstance[]>();
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    turIntegrationInstanceService.query().then(setIntegrationInstances).catch(() => setError("Connection error or timeout while fetching instances."));
  }, [])
  const gridItemList = useGridAdapter(integrationInstances, {
    name: "title",
    description: "description",
    url: (item) => `${ROUTES.INTEGRATION_INSTANCE}/${item.id}`
  });
  return (
    <LoadProvider checkIsNotUndefined={integrationInstances} error={error} tryAgainUrl={`${ROUTES.INTEGRATION_INSTANCE}`}>
      {gridItemList.length > 0 ? (
        <GridList gridItemList={gridItemList} />
      ) : (
        <BlankSlate
          icon={IconPlugConnectedX}
          title="You don’t seem to have any integration instance."
          description="Create a new integration to index from many external data sources."
          buttonText="New integration"
          urlNew={`${ROUTES.INTEGRATION_INSTANCE}/new`} />
      )}
    </LoadProvider>
  )
}


