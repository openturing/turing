import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconCompass } from "@tabler/icons-react";

export default function SNSiteRootPage() {
  useSubPageBreadcrumb({ label: "Semantic Navigation", href: `${ROUTES.SN_INSTANCE}` });
  return (
    <Page turIcon={IconCompass} title="Semantic Navigation" urlBase={ROUTES.SN_INSTANCE} />
  )
}
