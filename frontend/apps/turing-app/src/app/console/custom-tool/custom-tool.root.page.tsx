import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconBraces } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

/**
 * @since 2026.2.5
 */
export default function CustomToolRootPage() {
  const { t } = useTranslation();
  useSubPageBreadcrumb({ label: t("customTool.title"), href: `${ROUTES.CUSTOM_TOOL_INSTANCE}` });
  return (
    <Page turIcon={IconBraces} title={t("customTool.title")} urlBase={ROUTES.CUSTOM_TOOL_INSTANCE} />
  );
}
