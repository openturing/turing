import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SubPage } from "@/components/sub.page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { useCurrentUser } from "@/contexts/user.context";
import { IconSettings, IconUser, IconWorld } from "@tabler/icons-react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";

export default function UserAccountPage() {
    const { t } = useTranslation();
    const { user } = useCurrentUser();
    const urlBase = ROUTES.USER_ACCOUNT;

    useSubPageBreadcrumb({ label: t("account.title") });

    const data = useMemo(() => ({
        navMain: [
            { title: t("account.nav.profile"), url: "/profile", icon: IconUser },
            { title: t("account.nav.preferences"), url: "/preferences", icon: IconWorld },
        ],
    }), [t]);

    return (
        <LoadProvider checkIsNotUndefined={user} tryAgainUrl={ROUTES.USER_ACCOUNT}>
            <SubPage
                icon={IconSettings}
                feature={t("account.feature")}
                name={t("account.title")}
                data={data}
                urlBase={urlBase}
            />
        </LoadProvider>
    );
}
