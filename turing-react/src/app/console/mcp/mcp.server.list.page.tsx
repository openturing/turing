import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurMcpServer } from "@/models/mcp/mcp-server.model.ts";
import { TurMcpServerService } from "@/services/mcp/mcp-server.service";
import { IconServer2 } from "@tabler/icons-react";
import { useEffect, useState } from "react";

const turMcpServerService = new TurMcpServerService();

export default function McpServerListPage() {
  const [mcpServers, setMcpServers] = useState<TurMcpServer[]>();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    turMcpServerService.query().then(setMcpServers).catch(() => setError("Connection error or timeout while fetching MCP servers."));
  }, [])
  const gridItemList = useGridAdapter(mcpServers, {
    name: "title",
    description: "description",
    url: (item) => `${ROUTES.MCP_INSTANCE}/${item.id}`,
    icon: "icon",
  });
  return (
    <LoadProvider checkIsNotUndefined={mcpServers} error={error} tryAgainUrl={`${ROUTES.MCP_INSTANCE}`}>
      {gridItemList.length > 0 ? (
        <GridList gridItemList={gridItemList}>
          <GridList.NewButton to={`${ROUTES.MCP_INSTANCE}/new`} label="MCP Server" />
        </GridList>
      ) : (
        <BlankSlate
          icon={IconServer2}
          title="You don't seem to have any MCP server."
          description="Create a new MCP server to integrate with external tools and services."
          buttonText="New MCP server"
          urlNew={`${ROUTES.MCP_INSTANCE}/new`} />
      )}
    </LoadProvider>
  )
}
