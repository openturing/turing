import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import { IconShieldCog } from "@tabler/icons-react";
import { useEffect } from "react";

export default function AdminSettingsRootPage() {
    const { pushItem, popItem } = useBreadcrumb();
    useEffect(() => {
        pushItem({ label: "Administration", href: ROUTES.ADMIN_ROOT });
        return () => popItem();
    }, []);
    return (
        <Page turIcon={IconShieldCog} title="Administration" urlBase={ROUTES.ADMIN_ROOT} />
    );
}
