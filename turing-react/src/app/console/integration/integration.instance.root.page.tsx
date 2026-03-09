import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import { IconPlugConnectedX } from "@tabler/icons-react";
import { useEffect } from "react";

export default function IntegrationInstanceRootPage() {
  const { pushItem, popItem } = useBreadcrumb();
  useEffect(() => {
    pushItem({ label: "Integration", href: `${ROUTES.INTEGRATION_INSTANCE}` });
    return () => popItem();
  }, []);
  return (
    <Page turIcon={IconPlugConnectedX} title="Integration" urlBase={ROUTES.INTEGRATION_INSTANCE} urlNew={`${ROUTES.INTEGRATION_INSTANCE}/new`} />
  )
}