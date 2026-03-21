import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconBulb } from "@tabler/icons-react";

export default function IntentRootPage() {
  useSubPageBreadcrumb({ label: "Intent", href: `${ROUTES.INTENT_INSTANCE}` });
  return (
    <Page turIcon={IconBulb} title="Intent" urlBase={ROUTES.INTENT_INSTANCE} />
  )
}
