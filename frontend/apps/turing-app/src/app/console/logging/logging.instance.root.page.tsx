import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconReceiptRupee } from "@tabler/icons-react";

export default function LoggingInstanceRootPage() {
  useSubPageBreadcrumb({ label: "Logging", href: `${ROUTES.LOGGING_INSTANCE}` });
  return (
    <Page turIcon={IconReceiptRupee} title="Logging" urlBase={ROUTES.LOGGING_INSTANCE} />
  )
}
