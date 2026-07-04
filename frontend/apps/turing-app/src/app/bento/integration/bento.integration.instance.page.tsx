import { useDeleteIntegrationInstance, useIntegrationInstance, useUpdateIntegrationInstance } from "@/api/queries/integration-instance.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityShell } from "@/components/bento";
import { GlobalBadge, GlobalReadOnlyNotice } from "@/components/infra-global-notice";
import { LoadProvider } from "@/components/loading-provider";
import { useInfraReadOnly } from "@/hooks/use-infra-read-only";
import type { TurIntegrationInstance } from "@/models/integration/integration-instance.model.ts";
import { IconPlugConnectedX } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BENTO_INTEGRATION_FORM_ID, BentoIntegrationInstanceForm } from "./bento.integration.instance.form";

/**
 * Bento Integration detail — T553. BYO-infra: the shared GLOBAL pool (T372)
 * renders the instance read-only.
 */
export default function BentoIntegrationInstancePage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const isNew = id === "new";

  const { data: integration, isError } = useIntegrationInstance(isNew ? undefined : id);
  const error = isError ? t("common.connectionError", { resource: t("integration.title") }) : null;

  const updateMutation = useUpdateIntegrationInstance();
  const deleteMutation = useDeleteIntegrationInstance();

  const { isGlobal, readOnly } = useInfraReadOnly(integration?.tenantId);

  function shell(entity: TurIntegrationInstance, headlineFallback: string) {
    return (
      <BentoEntityShell
        entity={entity}
        isNew={isNew}
        headlineFallback={headlineFallback}
        eyebrow={t("integration.title")}
        listRoute={ROUTES.BENTO_INTEGRATION_INSTANCE}
        icon={IconPlugConnectedX}
        tone="amber"
        formId={BENTO_INTEGRATION_FORM_ID}
        feature={t("integration.title")}
        hasStatus
        readOnly={readOnly}
        badge={isGlobal ? <GlobalBadge /> : undefined}
        notice={readOnly ? <GlobalReadOnlyNotice /> : undefined}
        onUpdate={isNew || readOnly ? undefined : (next) => updateMutation.mutateAsync(next)}
        onDelete={isNew || readOnly ? undefined : () => deleteMutation.mutateAsync(entity)}
      >
        {({ staged, onStateChange }) => (
          <BentoIntegrationInstanceForm value={entity} isNew={isNew} readOnly={readOnly} staged={staged} onStateChange={onStateChange} />
        )}
      </BentoEntityShell>
    );
  }

  if (isNew) {
    return shell({} as TurIntegrationInstance, t("integration.newInstance"));
  }

  return (
    <LoadProvider checkIsNotUndefined={integration} error={error} tryAgainUrl={`${ROUTES.BENTO_INTEGRATION_INSTANCE}/${id}`}>
      {integration && shell(integration, integration.title)}
    </LoadProvider>
  );
}
