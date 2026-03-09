import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import { IconFileImport } from "@tabler/icons-react";
import { useEffect } from "react";

export default function ExchangeImportRootPage() {
    const { pushItem, popItem } = useBreadcrumb();
    useEffect(() => {
        pushItem({ label: "Import", href: `${ROUTES.EXCHANGE_IMPORT}` });
        return () => popItem();
    }, []);
    return (
        <Page turIcon={IconFileImport} title="Import" urlBase={ROUTES.EXCHANGE_IMPORT} />
    );
}
