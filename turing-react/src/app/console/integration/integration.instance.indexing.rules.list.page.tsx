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
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

export default function IntegrationInstanceIndexingRulesListPage() {
  const { id } = useParams<{ id: string }>();
  const [integrationIndexingRules, setIntegrationIndexingRules] = useState<TurIntegrationIndexingRule[]>();
  const turIntegrationIndexingRuleService = new TurIntegrationIndexingRuleService(id || "");
  const [error, setError] = useState<string | null>(null);
  const { pushItem, popItem } = useBreadcrumb();
  const { t } = useTranslation();
  useEffect(() => {
    let added = false;
    turIntegrationIndexingRuleService.query().then((rules) => {
      setIntegrationIndexingRules(rules);
      pushItem({ label: t("integration.indexingRules.title"), href: `${ROUTES.INTEGRATION_INSTANCE}/${id}/indexing-rule` });
      added = true;
    }).catch(() => setError(t("common.connectionError", { resource: "Integration service" })));
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
        <SubPageHeader icon={IconGitCommit} name={t("integration.indexingRules.title")} feature={t("integration.indexingRules.feature")}
          description={t("integration.indexingRules.description")} />
        <GridList gridItemList={gridItemList}>
          <GridList.NewButton to={`${ROUTES.INTEGRATION_INSTANCE}/${id}/indexing-rule/new`} label={t("integration.indexingRules.feature")} />
        </GridList>
      </>) : (
        <BlankSlate
          icon={IconGitCommit}
          title={t("integration.indexingRules.blankTitle")}
          description={t("integration.indexingRules.blankDescription")}
          buttonText={t("integration.indexingRules.newRule")}
          urlNew={`${ROUTES.INTEGRATION_INSTANCE}/${id}/indexing-rule/new`} />
      )}
    </LoadProvider>
  )
}
