import { ROUTES } from "@/app/routes.const";
import { IntegrationInstanceForm } from "@/components/integration/integration.instance.form";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import type { TurIntegrationInstance } from "@/models/integration/integration-instance.model";
import { TurIntegrationInstanceService } from "@/services/integration/integration.service";
import { IconSettings } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
const turIntegrationInstanceService = new TurIntegrationInstanceService();

export default function IntegrationInstanceDetailPage() {
  const { id } = useParams() as { id: string };
  const [integrationInstance, setIntegrationInstance] = useState<TurIntegrationInstance>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const { pushItem, popItem } = useBreadcrumb();

  useEffect(() => {
    let added = false;
    if (id === "new") {
      turIntegrationInstanceService.query().then(() => {
        setIntegrationInstance({} as TurIntegrationInstance)
        pushItem({ label: "Settings", href: `${ROUTES.INTEGRATION_INSTANCE}/${id}/detail` });
        added = true;
      }).catch(() => setError("Connection error or timeout while fetching Integration service."));
    } else {
      turIntegrationInstanceService.get(id).then((integration) => {
        setIntegrationInstance(integration);
        pushItem({ label: "Settings", href: `${ROUTES.INTEGRATION_INSTANCE}/${id}/detail` });
        added = true;
      }).catch(() => setError("Connection error or timeout while fetching integration instance."));
      setIsNew(false);
    }
    return () => {
      if (added) popItem();
    };
  }, [id])
  return (
    <LoadProvider checkIsNotUndefined={integrationInstance} error={error} tryAgainUrl={`${ROUTES.INTEGRATION_INSTANCE}/${id}/detail`}>
      <SubPageHeader icon={IconSettings} name="Settings" feature="Settings" description="Integration settings." />
      {integrationInstance && <IntegrationInstanceForm value={integrationInstance} isNew={isNew} />}
    </LoadProvider>
  )
}
