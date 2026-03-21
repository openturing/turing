import { ROUTES } from "@/app/routes.const";
import { SubPage } from "@/components/sub.page";
import { useCurrentUser } from "@/contexts/user.context";
import {
    IconCode,
    IconInfoCircle,
    IconSettings,
    IconShieldCog,
    IconUsers,
    IconUsersGroup,
    IconUserShield,
} from "@tabler/icons-react";
import { useMemo } from "react";

export default function AdminSettingsPage() {
    const { user } = useCurrentUser();
    const isAdmin = !!user?.admin;

    const showAuthManagement = isAdmin;

    const data = useMemo(() => ({
        navMain: [
            {
                title: "Settings",
                url: "/settings",
                icon: IconSettings,
            },
            ...(showAuthManagement ? [
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
            ] : []),
            {
                title: "API Token",
                url: "/tokens",
                icon: IconCode,
            },
            {
                title: "System Information",
                url: "/system-info",
                icon: IconInfoCircle,
            },
        ],
    }), [showAuthManagement]);

    return (
        <SubPage
            icon={IconShieldCog}
            feature="Administration"
            name="Administration"
            urlBase={ROUTES.ADMIN_ROOT}
            data={data}
        />
    );
}
