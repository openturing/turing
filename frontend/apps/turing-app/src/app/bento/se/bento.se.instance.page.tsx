import { useFeatures } from "@/api/queries/features.queries";
import { useDeleteSeInstance, useSeInstance, useUpdateSeInstance } from "@/api/queries/se-instance.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityShell } from "@/components/bento";
import { GlobalBadge, GlobalReadOnlyNotice } from "@/components/infra-global-notice";
import { LoadProvider } from "@/components/loading-provider";
import { useInfraReadOnly } from "@/hooks/use-infra-read-only";
import type { TurSEInstance } from "@/models/se/se-instance.model.ts";
import { IconDatabase, IconInfoCircle, IconLock, IconServer, IconZoomCode, type Icon as TablerIcon } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router-dom";
import { BENTO_SE_FORM_ID, BentoSEInstanceForm } from "./bento.se.instance.form";

interface SESectionLink {
  labelKey: string;
  icon: TablerIcon;
  path: string;
}

const SE_SECTIONS: readonly SESectionLink[] = [
  { labelKey: "se.nav.cores", icon: IconDatabase, path: "cores" },
  { labelKey: "se.nav.systemInfo", icon: IconInfoCircle, path: "system-info" },
];

function SESectionsPanel({ seId }: Readonly<{ seId: string }>) {
  const { t } = useTranslation();
  return (
    <section className="mt-2">
      <h2 className="mb-3 flex items-center gap-2 text-sm font-medium text-muted-foreground">
        <IconServer size={16} className="text-emerald-500" />
        {t("se.title")}
      </h2>
      <div className="grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-4">
        {SE_SECTIONS.map(({ labelKey, icon: Icon, path }) => (
          <Link
            key={path}
            to={`${ROUTES.BENTO_SE_INSTANCE}/${seId}/${path}`}
            className="bento-tile bento-tile-clickable bento-glass flex items-center gap-3 rounded-2xl p-4"
          >
            <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-linear-to-br from-emerald-500 to-teal-600 text-white shadow-sm">
              <Icon size={18} />
            </span>
            <span className="text-sm font-medium">{t(labelKey)}</span>
          </Link>
        ))}
      </div>
    </section>
  );
}

/**
 * Bento Search Engine (SE) detail — T551. Thin wrapper over the shared
 * {@link BentoEntityShell}. BYO-infra rules apply: the shared GLOBAL pool
 * (T372) and the Solr-property catalog lock both render the instance
 * read-only (identity + form fields), matching the console.
 */
export default function BentoSEInstancePage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const isNew = id === "new";

  const { data: se, isError } = useSeInstance(isNew ? undefined : id);
  const { data: features } = useFeatures();
  const error = isError ? t("common.connectionError", { resource: t("se.title") }) : null;

  const updateMutation = useUpdateSeInstance();
  const deleteMutation = useDeleteSeInstance();

  // Hooks are hoisted out of `shell()` (rules-of-hooks). A new instance has
  // no tenantId, so it's never global; the Solr-property lock is global.
  const { isGlobal, readOnly: globalReadOnly } = useInfraReadOnly(se?.tenantId);
  const catalogReadOnly = features?.seInstanceReadOnly ?? false;
  const readOnly = globalReadOnly || catalogReadOnly;

  function shell(entity: TurSEInstance, headlineFallback: string) {
    return (
      <BentoEntityShell
        entity={entity}
        isNew={isNew}
        headlineFallback={headlineFallback}
        eyebrow={t("se.title")}
        listRoute={ROUTES.BENTO_SE_INSTANCE}
        icon={IconZoomCode}
        tone="emerald"
        formId={BENTO_SE_FORM_ID}
        feature={t("se.title")}
        readOnly={readOnly}
        badge={isGlobal ? <GlobalBadge /> : undefined}
        notice={readOnly ? (
          globalReadOnly ? (
            <GlobalReadOnlyNotice />
          ) : (
            <div className="flex items-center gap-2 rounded-lg border border-amber-300/60 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-600/40 dark:bg-amber-950/30 dark:text-amber-200">
              <IconLock className="size-4 shrink-0" />
              <span>
                {t("se.readOnlyNotice", {
                  defaultValue:
                    "Solr is configured via turing.solr.endpoint. The search engine catalog is read-only.",
                })}
              </span>
            </div>
          )
        ) : undefined}
        onUpdate={isNew || readOnly ? undefined : (next) => updateMutation.mutateAsync(next)}
        onDelete={isNew || readOnly ? undefined : () => deleteMutation.mutateAsync(entity)}
      >
        {({ staged, onStateChange }) => (
          <>
            <BentoSEInstanceForm value={entity} isNew={isNew} readOnly={readOnly} staged={staged} onStateChange={onStateChange} />
            {!isNew && entity.id && <SESectionsPanel seId={entity.id} />}
          </>
        )}
      </BentoEntityShell>
    );
  }

  if (isNew) {
    return shell({} as TurSEInstance, t("se.newSearchEngine"));
  }

  return (
    <LoadProvider checkIsNotUndefined={se} error={error} tryAgainUrl={`${ROUTES.BENTO_SE_INSTANCE}/${id}`}>
      {se && shell(se, se.title)}
    </LoadProvider>
  );
}
