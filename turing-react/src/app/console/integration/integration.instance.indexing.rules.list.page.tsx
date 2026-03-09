import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurIntegrationIndexingRule } from "@/models/integration/integration-indexing-rule.model";
import { TurIntegrationIndexingRuleService } from "@/services/integration/integration-indexing-rule.service";
import { IconGitCommit } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";

export default function IntegrationInstanceIndexingRulesListPage() {
  const { id } = useParams<{ id: string }>();
  const [integrationIndexingRules, setIntegrationIndexingRules] = useState<TurIntegrationIndexingRule[]>();
  const turIntegrationIndexingRuleService = new TurIntegrationIndexingRuleService(id || "");
  const [error, setError] = useState<string | null>(null);
  const { pushItem, popItem } = useBreadcrumb();
  useEffect(() => {
    let added = false;
    turIntegrationIndexingRuleService.query().then((rules) => {
      setIntegrationIndexingRules(rules);
      pushItem({ label: "Indexing Rules", href: `${ROUTES.INTEGRATION_INSTANCE}/${id}/indexing-rule` });
      added = true;
    }).catch(() => setError("Connection error or timeout while fetching Integration service."));
    return () => {
      if (added) popItem();
    };
  }, [id])
  const gridItemList = useGridAdapter(integrationIndexingRules, {
    name: "name",
    description: "description",
    url: (item) => `${ROUTES.INTEGRATION_INSTANCE}/${id}/indexing-rule/${item.id}`
  });
  return (
    <LoadProvider checkIsNotUndefined={integrationIndexingRules} error={error} tryAgainUrl={`${ROUTES.INTEGRATION_INSTANCE}/${id}/indexing-rule`}>
      {gridItemList.length > 0 ? (<>
        <SubPageHeader icon={IconGitCommit} name="Indexing Rules" feature="Indexing Rules"
          description="Establish guidelines for how the indexing process will operate."
          urlNew={`${ROUTES.INTEGRATION_INSTANCE}/${id}/indexing-rule/new`} />
        <GridList gridItemList={gridItemList} />
      </>) : (
        <BlankSlate
          icon={IconGitCommit}
          title="You don’t seem to have any Indexing Rules."
          description="Create a new indexing rule to define how AEM sources are indexed."
          buttonText="New Indexing Rule"
          urlNew={`${ROUTES.INTEGRATION_INSTANCE}/${id}/indexing-rule/new`} />
      )}
    </LoadProvider>
  )
}
