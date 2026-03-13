import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { McpServerForm } from "@/components/mcp/mcp.server.form";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import type { TurMcpServer } from "@/models/mcp/mcp-server.model.ts";
import { TurMcpServerService } from "@/services/mcp/mcp-server.service";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";

const turMcpServerService = new TurMcpServerService();

export default function McpServerPage() {
  const { id } = useParams() as { id: string };
  const [mcpServer, setMcpServer] = useState<TurMcpServer>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const { pushItem, popItem } = useBreadcrumb();
  useEffect(() => {
    let added = false;
    if (id === "new") {
      turMcpServerService.query().then(() => {
        setMcpServer({} as TurMcpServer);
        pushItem({ label: "New MCP Server" });
        added = true;
      }).catch(() => setError("Connection error or timeout while fetching MCP servers."));
      setIsNew(true);
    } else {
      turMcpServerService.get(id).then((mcpServer) => {
        setMcpServer(mcpServer);
        pushItem({ label: mcpServer.title, href: `${ROUTES.MCP_INSTANCE}/${mcpServer.id}` });
        added = true;
      }).catch(() => setError("Connection error or timeout while fetching MCP server."));
      setIsNew(false);
    }
    return () => {
      if (added) popItem();
    };
  }, [id])
  return (
    <LoadProvider checkIsNotUndefined={mcpServer} error={error} tryAgainUrl={`${ROUTES.MCP_INSTANCE}/${id}`}>
      {mcpServer && <McpServerForm value={mcpServer} isNew={isNew} />}
    </LoadProvider>
  )
}
