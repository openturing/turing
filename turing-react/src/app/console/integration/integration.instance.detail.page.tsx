import { ROUTES } from "@/app/routes.const";
import { IntegrationInstanceForm } from "@/components/integration/integration.instance.form";
import { LoadProvider } from "@/components/loading-provider";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurIntegrationInstance } from "@/models/integration/integration-instance.model";
import { TurIntegrationInstanceService } from "@/services/integration/integration.service";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
const turIntegrationInstanceService = new TurIntegrationInstanceService();

export default function IntegrationInstanceDetailPage() {
  const { id } = useParams() as { id: string };
  const [integrationInstance, setIntegrationInstance] = useState<TurIntegrationInstance>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const { t } = useTranslation();
  useSubPageBreadcrumb(t("integration.detail.title"));

  useEffect(() => {
    if (id === "new") {
      turIntegrationInstanceService.query().then(() => {
        setIntegrationInstance({} as TurIntegrationInstance)
      }).catch(() => setError(t("common.connectionError", { resource: "Integration service" })));
    } else {
      turIntegrationInstanceService.get(id).then((integration) => {
        setIntegrationInstance(integration);
      }).catch(() => setError(t("common.connectionError", { resource: "Integration instance" })));
      setIsNew(false);
    }
  }, [id])
  return (
    <LoadProvider checkIsNotUndefined={integrationInstance} error={error} tryAgainUrl={`${ROUTES.INTEGRATION_INSTANCE}/${id}/detail`}>
      {integrationInstance && <IntegrationInstanceForm value={integrationInstance} isNew={isNew} />}
    </LoadProvider>
  )
}
