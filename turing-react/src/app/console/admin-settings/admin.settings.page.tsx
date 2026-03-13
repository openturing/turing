import { ROUTES } from "@/app/routes.const";
import { SubPage } from "@/components/sub.page";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import {
    IconShieldCog,
    IconUsers,
    IconUsersGroup,
    IconUserShield,
} from "@tabler/icons-react";
import { useEffect, useMemo } from "react";

export default function AdminSettingsPage() {
    const { pushItem, popItem } = useBreadcrumb();

    const data = useMemo(() => ({
        navMain: [
            {
                title: "Users",
                url: "/users",
                icon: IconUsers,
            },
            {
                title: "Groups",
                url: "/groups",
                icon: IconUsersGroup,
            },
            {
                title: "Roles",
                url: "/roles",
                icon: IconUserShield,
            },
        ],
    }), []);

    useEffect(() => {
        pushItem({ label: "Administration", href: ROUTES.ADMIN_ROOT });
        return () => popItem();
    }, []);

    return (
        <SubPage
            icon={IconShieldCog}
            feature="Administration"
            name="User Management"
            urlBase={ROUTES.ADMIN_ROOT}
            data={data}
        />
    );
}
