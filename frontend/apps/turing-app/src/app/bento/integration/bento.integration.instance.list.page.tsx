import { useFeatures } from "@/api/queries/features.queries";
import { useIntegrationInstances } from "@/api/queries/integration-instance.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityTile, BentoListPage } from "@/components/bento";
import { GlobalBadge } from "@/components/infra-global-notice";
import { IconPlugConnectedX } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function BentoIntegrationInstanceListPage() {
  const { t } = useTranslation();
  const { data: integrations, isError } = useIntegrationInstances();
  const { data: features } = useFeatures();
  const error = isError ? t("common.connectionError", { resource: t("integration.title") }) : null;
  const tenancyEnabled = features?.tenancyEnabled === true;

  return (
    <BentoListPage
      items={integrations}
      error={error}
      tryAgainUrl={ROUTES.BENTO_INTEGRATION_INSTANCE}
      backTo={ROUTES.BENTO_AREA_ENTERPRISE_SEARCH}
      backLabel={t("home.sections.enterpriseSearch.label")}
      heroIcon={IconPlugConnectedX}
      tone="amber"
      title={t("integration.title")}
      subtitle={t("integration.blankDescription")}
      newRoute={`${ROUTES.BENTO_INTEGRATION_INSTANCE}/new`}
      newLabel={t("integration.newInstance")}
      itemKey={(integration) => integration.id}
      emptyTitle={t("integration.blankTitle")}
      emptyDescription={t("integration.blankDescription")}
      listId="integration"
      renderTile={(integration, emphasis) => (
        <BentoEntityTile
          to={`${ROUTES.BENTO_INTEGRATION_INSTANCE}/${integration.id}`}
          emphasis={emphasis}
          defaultIcon={IconPlugConnectedX}
          icon={integration.icon}
          tone="amber"
          title={integration.title}
          description={integration.description}
          hasStatus
          enabled={integration.enabled}
          meta={tenancyEnabled && integration.tenantId == null ? <GlobalBadge /> : undefined}
        />
      )}
    />
  );
}
