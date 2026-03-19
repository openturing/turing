import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconCode } from "@tabler/icons-react";

export default function TokenInstanceRootPage() {
  useSubPageBreadcrumb({ label: "API Token", href: `${ROUTES.TOKEN_INSTANCE}` });
  return (
    <Page turIcon={IconCode} title="API Token" urlBase={ROUTES.TOKEN_INSTANCE} />
  )
}
