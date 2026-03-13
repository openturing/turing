import { ROUTES } from "@/app/routes.const";
import { SubPage } from "@/components/sub.page";
import { TurAuthorizationService } from "@/services/auth/authorization.service";
import {
    IconCode,
    IconSettings,
    IconShieldCog,
    IconUsers,
    IconUsersGroup,
    IconUserShield,
} from "@tabler/icons-react";
import { useEffect, useMemo, useState } from "react";

const turAuthorizationService = new TurAuthorizationService();

export default function AdminSettingsPage() {
    const [isKeycloak, setIsKeycloak] = useState(true);

    useEffect(() => {
        turAuthorizationService.discovery().then((d) => {
            setIsKeycloak(d.keycloak);
        });
    }, []);

    const data = useMemo(() => ({
        navMain: [
            {
                title: "Settings",
                url: "/settings",
                icon: IconSettings,
            },
            ...(isKeycloak ? [] : [
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
            ]),
            {
                title: "API Token",
                url: "/tokens",
                icon: IconCode,
            },

        ],
    }), [isKeycloak]);

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
