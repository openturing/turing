import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconDatabase } from "@tabler/icons-react";

export default function StoreInstanceListPage() {
  useSubPageBreadcrumb({ label: "Embedding Store", href: `${ROUTES.STORE_INSTANCE}` });
  return (
    <Page turIcon={IconDatabase} title="Embedding Store" urlBase={ROUTES.STORE_INSTANCE} urlNew={`${ROUTES.STORE_INSTANCE}/new`} />
  )
}
