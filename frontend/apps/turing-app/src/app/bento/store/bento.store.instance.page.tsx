import { useDeleteStoreInstance, useStoreInstance, useUpdateStoreInstance } from "@/api/queries/store-instance.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityShell } from "@/components/bento";
import { GlobalBadge, GlobalReadOnlyNotice } from "@/components/infra-global-notice";
import { LoadProvider } from "@/components/loading-provider";
import { useInfraReadOnly } from "@/hooks/use-infra-read-only";
import type { TurStoreInstance } from "@/models/store/store-instance.model.ts";
import { IconDatabase, IconInfoCircle } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router-dom";
import { BentoStoreCollections } from "./bento.store.instance.collections";
import { BENTO_STORE_FORM_ID, BentoStoreInstanceForm } from "./bento.store.instance.form";

/** Tiles linking to the store's read-only diagnostic sub-pages (system info). */
function StoreSectionsPanel({ storeId }: Readonly<{ storeId: string }>) {
  const { t } = useTranslation();
  return (
    <section className="mt-2">
      <h2 className="mb-3 flex items-center gap-2 text-sm font-medium text-muted-foreground">
        <IconDatabase size={16} className="text-violet-500" />
        {t("store.title")}
      </h2>
      <div className="grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-4">
        <Link
          to={`${ROUTES.BENTO_STORE_INSTANCE}/${storeId}/system-info`}
          className="bento-tile bento-tile-clickable bento-glass flex items-center gap-3 rounded-2xl p-4"
        >
          <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-linear-to-br from-violet-500 to-purple-600 text-white shadow-sm">
            <IconInfoCircle size={18} />
          </span>
          <span className="text-sm font-medium">{t("store.nav.systemInfo")}</span>
        </Link>
      </div>
    </section>
  );
}

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
          <>
            <BentoStoreInstanceForm value={entity} isNew={isNew} readOnly={readOnly} staged={staged} onStateChange={onStateChange} />
            {!isNew && entity.id && (
              <>
                <BentoStoreCollections storeId={entity.id} readOnly={readOnly} />
                <StoreSectionsPanel storeId={entity.id} />
              </>
            )}
          </>
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
