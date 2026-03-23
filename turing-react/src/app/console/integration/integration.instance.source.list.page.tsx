import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { type BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurIntegrationAemSource } from "@/models/integration/integration-aem-source.model";
import { TurIntegrationAemSourceService } from "@/services/integration/integration-aem-source.service";
import { IconGitCommit } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

export default function IntegrationInstanceSourceListPage() {
  const { id } = useParams() as { id: string };
  const [integrationAemSources, setIntegrationAemSources] = useState<TurIntegrationAemSource[]>();
  const turIntegrationAemSourceService = new TurIntegrationAemSourceService(id);
  const [error, setError] = useState<string | null>(null);
  const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>();
  const { t } = useTranslation();

  useEffect(() => {
    turIntegrationAemSourceService.query().then((sources) => {
      setIntegrationAemSources(sources);
      setBreadcrumb([{ label: t("integration.sources.title") }]);
    }).catch(() => setError(t("common.connectionError", { resource: "Integration service" })));
  }, [id]);

  useSubPageBreadcrumb(breadcrumb);
  const gridItemList = useGridAdapter(integrationAemSources, {
    name: "name",
    description: "endpoint",
    url: (item) => `${ROUTES.INTEGRATION_INSTANCE}/${id}/source/${item.id}`
  });
  return (
    <LoadProvider checkIsNotUndefined={integrationAemSources} error={error} tryAgainUrl={`${ROUTES.INTEGRATION_INSTANCE}/${id}/source`}>
      {gridItemList.length > 0 ? (<>
        <SubPageHeader icon={IconGitCommit} name={t("integration.sources.title")}
          feature={t("integration.sources.feature")}
          description={t("integration.sources.description")} />
        <GridList gridItemList={gridItemList}>
          <GridList.NewButton to={`${ROUTES.INTEGRATION_INSTANCE}/${id}/source/new`} label={t("integration.sources.feature")} />
        </GridList>

      </>) : (
        <BlankSlate
          icon={IconGitCommit}
          title={t("integration.sources.blankTitle")}
          description={t("integration.sources.blankDescription")}
          buttonText={t("integration.sources.newSource")}
          urlNew={`${ROUTES.INTEGRATION_INSTANCE}/${id}/source/new`} />
      )}
    </LoadProvider>
  )
}
