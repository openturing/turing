import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import { IconSettings } from "@tabler/icons-react";
import { useEffect } from "react";

export default function GlobalSettingsRootPage() {
    const { pushItem, popItem } = useBreadcrumb();
    useEffect(() => {
        pushItem({ label: "Global Settings", href: ROUTES.GLOBAL_SETTINGS });
        return () => popItem();
    }, []);
    return (
        <Page turIcon={IconSettings} title="Global Settings" urlBase={ROUTES.GLOBAL_SETTINGS} />
    );
}
