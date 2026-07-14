import { useFeatures } from "@/api/queries/features.queries";
import { useMcpServers } from "@/api/queries/mcp-server.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityTile, BentoListPage } from "@/components/bento";
import { GlobalBadge } from "@/components/infra-global-notice";
import { IconServer2 } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function BentoMcpServerListPage() {
  const { t } = useTranslation();
  const { data: servers, isError } = useMcpServers();
  const { data: features } = useFeatures();
  const error = isError ? t("common.connectionError", { resource: t("mcp.title") }) : null;
  const tenancyEnabled = features?.tenancyEnabled === true;

  return (
    <BentoListPage
      items={servers}
      error={error}
      tryAgainUrl={ROUTES.BENTO_MCP_INSTANCE}
      backTo={ROUTES.BENTO_AREA_GENERATIVE_AI}
      backLabel={t("home.sections.generativeAi.label")}
      heroIcon={IconServer2}
      tone="amber"
      title={t("mcp.title")}
      subtitle={t("mcp.blankDescription")}
      newRoute={`${ROUTES.BENTO_MCP_INSTANCE}/new`}
      newLabel={t("mcp.newMcpServer")}
      newSubtitle="HTTP · Command"
      itemKey={(server) => server.id}
      emptyTitle={t("mcp.blankTitle")}
      emptyDescription={t("mcp.blankDescription")}
      listId="mcpServer"
      renderTile={(server, emphasis) => (
        <BentoEntityTile
          to={`${ROUTES.BENTO_MCP_INSTANCE}/${server.id}`}
          emphasis={emphasis}
          defaultIcon={IconServer2}
          icon={server.icon}
          tone="amber"
          title={server.title}
          description={server.description}
          hasStatus
          enabled={server.enabled}
          meta={
            <>
              <span className="rounded-full border border-border/60 bg-card/40 px-2 py-0.5 backdrop-blur">
                {server.connectionType}
              </span>
              {tenancyEnabled && server.tenantId == null && <GlobalBadge />}
            </>
          }
        />
      )}
    />
  );
}
