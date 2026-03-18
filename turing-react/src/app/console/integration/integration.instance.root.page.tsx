import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconPlugConnectedX } from "@tabler/icons-react";

export default function IntegrationInstanceRootPage() {
  useSubPageBreadcrumb({ label: "Integration", href: `${ROUTES.INTEGRATION_INSTANCE}` });
  return (
    <Page turIcon={IconPlugConnectedX} title="Integration" urlBase={ROUTES.INTEGRATION_INSTANCE} />
  )
}
