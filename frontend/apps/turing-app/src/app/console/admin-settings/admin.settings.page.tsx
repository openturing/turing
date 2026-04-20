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
import { useTranslation } from "react-i18next";

export default function AdminSettingsPage() {
    const { t } = useTranslation();
    const { user } = useCurrentUser();
    const isAdmin = !!user?.admin;

    const showAuthManagement = isAdmin;

    const data = useMemo(() => ({
        navMain: [
            {
                title: t("admin.settings"),
                url: "/settings",
                icon: IconSettings,
            },
            ...(showAuthManagement ? [
                {
                    title: t("admin.users.title"),
                    url: "/users",
                    icon: IconUsers,
                },
                {
                    title: t("admin.groups.title"),
                    url: "/groups",
                    icon: IconUsersGroup,
                },
                {
                    title: t("admin.roles.title"),
                    url: "/roles",
                    icon: IconUserShield,
                },
            ] : []),
            {
                title: t("apiToken.title"),
                url: "/tokens",
                icon: IconCode,
            },
            {
                title: t("admin.systemInfo.title"),
                url: "/system-info",
                icon: IconInfoCircle,
            },
        ],
    }), [showAuthManagement, t]);

    return (
        <SubPage
            icon={IconShieldCog}
            feature={t("admin.title")}
            name={t("admin.title")}
            urlBase={ROUTES.ADMIN_ROOT}
            data={data}
        />
    );
}
