import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import type { BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
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
  const { t } = useTranslation();
  const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>();
  useSubPageBreadcrumb(breadcrumb);

  useEffect(() => {
    turIntegrationIndexingRuleService.query().then((rules) => {
      setIntegrationIndexingRules(rules);
      setBreadcrumb([{ label: t("integration.indexingRules.title"), href: `${ROUTES.INTEGRATION_INSTANCE}/${id}/indexing-rule` }]);
    }).catch(() => setError(t("common.connectionError", { resource: "Integration service" })));
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
