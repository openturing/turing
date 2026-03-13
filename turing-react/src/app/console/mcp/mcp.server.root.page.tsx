import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import { IconServer2 } from "@tabler/icons-react";
import { useEffect } from "react";

export default function McpServerRootPage() {
  const { pushItem, popItem } = useBreadcrumb();
  useEffect(() => {
    pushItem({ label: "MCP Server", href: `${ROUTES.MCP_INSTANCE}` });
    return () => popItem();
  }, []);
  return (
    <Page turIcon={IconServer2} title="MCP Server" urlBase={ROUTES.MCP_INSTANCE} urlNew={`${ROUTES.MCP_INSTANCE}/new`} />
  )
}
