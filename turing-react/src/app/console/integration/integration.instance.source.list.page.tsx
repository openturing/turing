import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurIntegrationAemSource } from "@/models/integration/integration-aem-source.model";
import { TurIntegrationAemSourceService } from "@/services/integration/integration-aem-source.service";
import { IconGitCommit } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";

export default function IntegrationInstanceSourceListPage() {
  const { id } = useParams() as { id: string };
  const [integrationAemSources, setIntegrationAemSources] = useState<TurIntegrationAemSource[]>();
  const turIntegrationAemSourceService = new TurIntegrationAemSourceService(id);
  const [error, setError] = useState<string | null>(null);
  const { pushItem, popItem } = useBreadcrumb();

  useEffect(() => {
    let added = false;
    turIntegrationAemSourceService.query().then((sources) => {
      setIntegrationAemSources(sources);
      pushItem({ label: "Sources", href: `${ROUTES.INTEGRATION_INSTANCE}/${id}/source` });
      added = true;
    }).catch(() => setError("Connection error or timeout while fetching Integration service."));
    return () => {
      if (added) popItem();
    };
  }, [id])
  const gridItemList = useGridAdapter(integrationAemSources, {
    name: "name",
    description: "endpoint",
    url: (item) => `${ROUTES.INTEGRATION_INSTANCE}/${id}/source/${item.id}`
  });
  return (
    <LoadProvider checkIsNotUndefined={integrationAemSources} error={error} tryAgainUrl={`${ROUTES.INTEGRATION_INSTANCE}/${id}/source`}>
      {gridItemList.length > 0 ? (<>
        <SubPageHeader icon={IconGitCommit} name="Sources"
          feature="Source"
          description="Available AEM sources for indexing and configuration."
          urlNew={`${ROUTES.INTEGRATION_INSTANCE}/${id}/source/new`} />
        <GridList gridItemList={gridItemList} />

      </>) : (
        <BlankSlate
          icon={IconGitCommit}
          title="You don’t seem to have any AEM sources."
          description="Create a new AEM source to index from many AEM external data sources."
          buttonText="New AEM source"
          urlNew={`${ROUTES.INTEGRATION_INSTANCE}/${id}/source/new`} />
      )}
    </LoadProvider>
  )
}
