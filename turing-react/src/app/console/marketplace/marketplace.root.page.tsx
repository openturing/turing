import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconBuildingStore } from "@tabler/icons-react";

export default function MarketplaceRootPage() {
    useSubPageBreadcrumb({ label: "Marketplace", href: ROUTES.MARKETPLACE_ROOT });
    return (
        <Page turIcon={IconBuildingStore} title="Marketplace" urlBase={ROUTES.MARKETPLACE_ROOT} />
    );
}
