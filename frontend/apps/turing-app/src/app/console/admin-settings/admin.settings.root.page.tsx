import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconShieldCog } from "@tabler/icons-react";

export default function AdminSettingsRootPage() {
    useSubPageBreadcrumb({ label: "Administration", href: ROUTES.ADMIN_ROOT });
    return (
        <Page turIcon={IconShieldCog} title="Administration" urlBase={ROUTES.ADMIN_ROOT} />
    );
}
