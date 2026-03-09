import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import { IconDatabase } from "@tabler/icons-react";
import { useEffect } from "react";

export default function StoreInstanceListPage() {
  const { pushItem, popItem } = useBreadcrumb();
  useEffect(() => {
    pushItem({ label: "Embedding Store", href: `${ROUTES.STORE_INSTANCE}` });
    return () => popItem();
  }, []);
  return (
    <Page turIcon={IconDatabase} title="Embedding Store" urlBase={ROUTES.STORE_INSTANCE} urlNew={`${ROUTES.STORE_INSTANCE}/new`} />
  )
}


