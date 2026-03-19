import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconServer2 } from "@tabler/icons-react";

export default function McpServerRootPage() {
  useSubPageBreadcrumb({ label: "MCP Server", href: `${ROUTES.MCP_INSTANCE}` });
  return (
    <Page turIcon={IconServer2} title="MCP Server" urlBase={ROUTES.MCP_INSTANCE} />
  )
}
