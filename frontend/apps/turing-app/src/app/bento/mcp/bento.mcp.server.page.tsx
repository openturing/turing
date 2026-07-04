import { useDeleteMcpServer, useMcpServer, useUpdateMcpServer } from "@/api/queries/mcp-server.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityShell } from "@/components/bento";
import { GlobalBadge, GlobalReadOnlyNotice } from "@/components/infra-global-notice";
import { LoadProvider } from "@/components/loading-provider";
import { useInfraReadOnly } from "@/hooks/use-infra-read-only";
import type { TurMcpServer } from "@/models/mcp/mcp-server.model.ts";
import { IconServer2 } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BENTO_MCP_FORM_ID, BentoMcpServerForm } from "./bento.mcp.server.form";

/**
 * Bento MCP Server detail — T554. BYO-infra: the shared GLOBAL pool (T372)
 * renders the instance read-only.
 */
export default function BentoMcpServerPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const isNew = id === "new";

  const { data: server, isError } = useMcpServer(isNew ? undefined : id);
  const error = isError ? t("common.connectionError", { resource: t("mcp.title") }) : null;

  const updateMutation = useUpdateMcpServer();
  const deleteMutation = useDeleteMcpServer();

  const { isGlobal, readOnly } = useInfraReadOnly(server?.tenantId);

  function shell(entity: TurMcpServer, headlineFallback: string) {
    return (
      <BentoEntityShell
        entity={entity}
        isNew={isNew}
        headlineFallback={headlineFallback}
        eyebrow={t("mcp.title")}
        listRoute={ROUTES.BENTO_MCP_INSTANCE}
        icon={IconServer2}
        tone="amber"
        formId={BENTO_MCP_FORM_ID}
        feature={t("mcp.title")}
        hasStatus
        readOnly={readOnly}
        badge={isGlobal ? <GlobalBadge /> : undefined}
        notice={readOnly ? <GlobalReadOnlyNotice /> : undefined}
        onUpdate={isNew || readOnly ? undefined : (next) => updateMutation.mutateAsync(next)}
        onDelete={isNew || readOnly ? undefined : () => deleteMutation.mutateAsync(entity)}
      >
        {({ staged, onStateChange }) => (
          <BentoMcpServerForm value={entity} isNew={isNew} readOnly={readOnly} staged={staged} onStateChange={onStateChange} />
        )}
      </BentoEntityShell>
    );
  }

  if (isNew) {
    return shell({} as TurMcpServer, t("mcp.newMcpServer"));
  }

  return (
    <LoadProvider checkIsNotUndefined={server} error={error} tryAgainUrl={`${ROUTES.BENTO_MCP_INSTANCE}/${id}`}>
      {server && shell(server, server.title)}
    </LoadProvider>
  );
}
