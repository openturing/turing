import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconFileImport } from "@tabler/icons-react";

export default function ExchangeImportRootPage() {
    useSubPageBreadcrumb({ label: "Import", href: `${ROUTES.EXCHANGE_IMPORT}` });
    return (
        <Page turIcon={IconFileImport} title="Import" urlBase={ROUTES.EXCHANGE_IMPORT} />
    );
}
