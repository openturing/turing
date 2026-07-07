import { useDeleteStoreInstance, useStoreInstance, useUpdateStoreInstance } from "@/api/queries/store-instance.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityShell } from "@/components/bento";
import { GlobalBadge, GlobalReadOnlyNotice } from "@/components/infra-global-notice";
import { LoadProvider } from "@/components/loading-provider";
import { useInfraReadOnly } from "@/hooks/use-infra-read-only";
import type { TurStoreInstance } from "@/models/store/store-instance.model.ts";
import { IconDatabase } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BENTO_STORE_FORM_ID, BentoStoreInstanceForm } from "./bento.store.instance.form";

/**
 * Bento Embedding Store detail — T552. Thin wrapper over the shared
 * {@link BentoEntityShell}. BYO-infra: the shared GLOBAL pool (T372) renders
 * the instance read-only.
 */
export default function BentoStoreInstancePage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const isNew = id === "new";

  const { data: store, isError } = useStoreInstance(isNew ? undefined : id);
  const error = isError ? t("common.connectionError", { resource: t("store.title") }) : null;

  const updateMutation = useUpdateStoreInstance();
  const deleteMutation = useDeleteStoreInstance();

  const { isGlobal, readOnly } = useInfraReadOnly(store?.tenantId);

  function shell(entity: TurStoreInstance, headlineFallback: string) {
    return (
      <BentoEntityShell
        entity={entity}
        isNew={isNew}
        headlineFallback={headlineFallback}
        eyebrow={t("store.title")}
        listRoute={ROUTES.BENTO_STORE_INSTANCE}
        icon={IconDatabase}
        tone="violet"
        formId={BENTO_STORE_FORM_ID}
        feature={t("store.title")}
        hasStatus
        readOnly={readOnly}
        badge={isGlobal ? <GlobalBadge /> : undefined}
        notice={readOnly ? <GlobalReadOnlyNotice /> : undefined}
        onUpdate={isNew || readOnly ? undefined : (next) => updateMutation.mutateAsync(next)}
        onDelete={isNew || readOnly ? undefined : () => deleteMutation.mutateAsync(entity)}
      >
        {({ staged, onStateChange }) => (
          <BentoStoreInstanceForm value={entity} isNew={isNew} readOnly={readOnly} staged={staged} onStateChange={onStateChange} />
        )}
      </BentoEntityShell>
    );
  }

  if (isNew) {
    return shell({} as TurStoreInstance, t("store.newInstance"));
  }

  return (
    <LoadProvider checkIsNotUndefined={store} error={error} tryAgainUrl={`${ROUTES.BENTO_STORE_INSTANCE}/${id}`}>
      {store && shell(store, store.title)}
    </LoadProvider>
  );
}
