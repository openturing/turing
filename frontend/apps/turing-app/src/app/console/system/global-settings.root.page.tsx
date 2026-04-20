import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconSettings } from "@tabler/icons-react";

export default function GlobalSettingsRootPage() {
    useSubPageBreadcrumb({ label: "Global Settings", href: ROUTES.GLOBAL_SETTINGS });
    return (
        <Page turIcon={IconSettings} title="Global Settings" urlBase={ROUTES.GLOBAL_SETTINGS} />
    );
}
