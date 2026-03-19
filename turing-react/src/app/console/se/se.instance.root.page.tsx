import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconZoomCode } from "@tabler/icons-react";

export default function SEInstanceRootPage() {
  useSubPageBreadcrumb({ label: "Search Engine", href: `${ROUTES.SE_INSTANCE}` });
  return (
    <Page turIcon={IconZoomCode} title="Search Engine" urlBase={ROUTES.SE_INSTANCE} />
  )
}
