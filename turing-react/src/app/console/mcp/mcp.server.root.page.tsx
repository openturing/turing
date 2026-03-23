import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconServer2 } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function McpServerRootPage() {
  const { t } = useTranslation();
  useSubPageBreadcrumb({ label: t("mcp.title"), href: `${ROUTES.MCP_INSTANCE}` });
  return (
    <Page turIcon={IconServer2} title={t("mcp.title")} urlBase={ROUTES.MCP_INSTANCE} />
  )
}
