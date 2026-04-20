import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { McpServerForm } from "@/components/mcp/mcp.server.form";
import type { BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurMcpServer } from "@/models/mcp/mcp-server.model.ts";
import { TurMcpServerService } from "@/services/mcp/mcp-server.service";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const turMcpServerService = new TurMcpServerService();

export default function McpServerPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const [mcpServer, setMcpServer] = useState<TurMcpServer>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[] | undefined>(
    id === "new" ? [{ label: t("mcp.newMcpServer") }] : undefined
  );
  useSubPageBreadcrumb(breadcrumb);

  useEffect(() => {
    if (id === "new") {
      turMcpServerService.query().then(() => {
        setMcpServer({} as TurMcpServer);
      }).catch(() => setError(t("common.connectionError", { resource: t("mcp.title") })));
      setIsNew(true);
    } else {
      turMcpServerService.get(id).then((mcpServer) => {
        setMcpServer(mcpServer);
        setBreadcrumb([{ label: mcpServer.title, href: `${ROUTES.MCP_INSTANCE}/${mcpServer.id}` }]);
      }).catch(() => setError(t("common.connectionError", { resource: t("mcp.title") })));
      setIsNew(false);
    }
  }, [id])
  return (
    <LoadProvider checkIsNotUndefined={mcpServer} error={error} tryAgainUrl={`${ROUTES.MCP_INSTANCE}/${id}`}>
      {mcpServer && <McpServerForm value={mcpServer} isNew={isNew} />}
    </LoadProvider>
  )
}
