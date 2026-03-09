import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import { IconSearch } from "@tabler/icons-react";
import { useEffect } from "react";

export default function SNSiteRootPage() {
  const { pushItem, popItem } = useBreadcrumb();
  useEffect(() => {
    pushItem({ label: "Semantic Navigation", href: `${ROUTES.SN_INSTANCE}` });
    return () => popItem();
  }, []);
  return (
    <Page turIcon={IconSearch} title="Semantic Navigation" urlBase={ROUTES.SN_INSTANCE} urlNew={`${ROUTES.SN_INSTANCE}/new`} />
  )
}


