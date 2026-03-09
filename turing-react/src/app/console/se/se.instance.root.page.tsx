import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import { IconZoomCode } from "@tabler/icons-react";
import { useEffect } from "react";

export default function SEInstanceRootPage() {
  const { pushItem, popItem } = useBreadcrumb();
  useEffect(() => {
    pushItem({ label: "Search Engine", href: `${ROUTES.SE_INSTANCE}` });
    return () => popItem();
  }, []);
  return (
    <Page turIcon={IconZoomCode} title="Search Engine" urlBase={ROUTES.SE_INSTANCE} urlNew={`${ROUTES.SE_INSTANCE}/new`} />
  )
}
