import { useMcpServers } from "@/api/queries/mcp-server.queries";
import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import { IconServer2 } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function McpServerListPage() {
  const { t } = useTranslation();
  const { data: mcpServers, isError } = useMcpServers();
  const error = isError ? t("common.connectionError", { resource: "MCP servers" }) : null;
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
          <GridList.NewButton to={`${ROUTES.MCP_INSTANCE}/new`} label={t("mcp.title")} />
        </GridList>
      ) : (
        <BlankSlate
          icon={IconServer2}
          title={t("mcp.blankTitle")}
          description={t("mcp.blankDescription")}
          buttonText={t("mcp.newInstance")}
          urlNew={`${ROUTES.MCP_INSTANCE}/new`} />
      )}
    </LoadProvider>
  )
}
