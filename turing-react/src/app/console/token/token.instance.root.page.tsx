import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import { IconCode } from "@tabler/icons-react";
import { useEffect } from "react";

export default function TokenInstanceRootPage() {
  const { pushItem, popItem } = useBreadcrumb();
  useEffect(() => {
    pushItem({ label: "API Token", href: `${ROUTES.TOKEN_INSTANCE}` });
    return () => popItem();
  }, []);
  return (
    <Page turIcon={IconCode} title="API Token" urlBase={ROUTES.TOKEN_INSTANCE} urlNew={`${ROUTES.TOKEN_INSTANCE}/new`} />
  )
}


