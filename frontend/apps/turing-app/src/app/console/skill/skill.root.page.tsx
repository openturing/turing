import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconPackages } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

/**
 * T117 / §IX.4.b — root of the Skills marketplace section. Renders the page
 * header + breadcrumb; the index child route renders the storefront grid.
 *
 * @since 2026.3.1
 */
export default function SkillRootPage() {
    const { t } = useTranslation();
    useSubPageBreadcrumb({ label: t("skill.title"), href: ROUTES.SKILL_ROOT });
    return <Page turIcon={IconPackages} title={t("skill.title")} urlBase={ROUTES.SKILL_ROOT} />;
}
