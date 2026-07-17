import { useFeatures } from "@/api/queries/features.queries";
import { useDeleteSeInstance, useSeInstance, useUpdateSeInstance } from "@/api/queries/se-instance.queries";
import { ROUTES } from "@/app/routes.const";
import SEInstanceCoresPage from "@/app/console/se/cores/se.instance.cores.page";
import SEInstanceSystemInfoPage from "@/app/console/se/system-info/se.instance.system-info.page";
import { BentoEntityShell, type BentoIdentity, type BentoShellFormState } from "@/components/bento";
import { GlobalBadge, GlobalReadOnlyNotice } from "@/components/infra-global-notice";
import { LoadProvider } from "@/components/loading-provider";
import { useInfraReadOnly } from "@/hooks/use-infra-read-only";
import type { TurSEInstance } from "@/models/se/se-instance.model.ts";
import { IconAdjustments, IconDatabase, IconInfoCircle, IconLock, IconZoomCode, type Icon as TablerIcon } from "@tabler/icons-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BENTO_SE_FORM_ID, BentoSEInstanceForm } from "./bento.se.instance.form";

type SEDetailTab = "settings" | "cores" | "systemInfo";

/** A single frosted pill in the SE detail tab-bar (see turing-bento-ui skill). */
function SETabPill({
  active,
  icon: Icon,
  label,
  onSelect,
}: Readonly<{ active: boolean; icon: TablerIcon; label: string; onSelect: () => void }>) {
  return (
    <button
      type="button"
      onClick={onSelect}
      className={`bento-tile bento-tile-clickable inline-flex items-center gap-1.5 rounded-full border px-3.5 py-1.5 text-sm backdrop-blur transition-colors ${
        active
          ? "border-primary/40 bg-primary text-primary-foreground"
          : "border-border/60 bg-card/60 text-muted-foreground hover:text-foreground"
      }`}
    >
      <Icon size={16} />
      {label}
    </button>
  );
}

/**
 * Detail body for an existing search engine: a frosted pill tab-bar splitting
 * the connection form ("Settings") from the Solr cores ("Cores") and the
 * read-only diagnostics ("System Information").
 *
 * The form stays mounted on every tab — hidden with `hidden` rather than
 * unmounted — so the hero-anchored Save button keeps its `form="…"` submit
 * target and the react-hook-form dirty state survives a tab switch. The other
 * tabs mount lazily so they fetch fresh each time they are opened. On a new
 * instance there are no cores or diagnostics yet, so the tab-bar is suppressed
 * and only the form renders (its Save creates the entity).
 */
function BentoSEInstanceBody({
  entity,
  isNew,
  readOnly,
  staged,
  onStateChange,
}: Readonly<{
  entity: TurSEInstance;
  isNew: boolean;
  readOnly: boolean;
  staged: BentoIdentity;
  onStateChange: (state: BentoShellFormState) => void;
}>) {
  const { t } = useTranslation();
  const [tab, setTab] = useState<SEDetailTab>("settings");
  const showTabs = !isNew && !!entity.id;

  return (
    <>
      {showTabs && (
        <nav className="mb-6 flex flex-wrap gap-2">
          <SETabPill
            active={tab === "settings"}
            icon={IconAdjustments}
            label={t("se.nav.settings")}
            onSelect={() => setTab("settings")}
          />
          <SETabPill
            active={tab === "cores"}
            icon={IconDatabase}
            label={t("se.nav.cores")}
            onSelect={() => setTab("cores")}
          />
          <SETabPill
            active={tab === "systemInfo"}
            icon={IconInfoCircle}
            label={t("se.nav.systemInfo")}
            onSelect={() => setTab("systemInfo")}
          />
        </nav>
      )}

      {/* Settings tab. Kept in the DOM on every tab (hidden, not unmounted) so
          the form="…" submit target and dirty state persist across switches. */}
      <div className={showTabs && tab !== "settings" ? "hidden" : undefined}>
        <BentoSEInstanceForm
          value={entity}
          isNew={isNew}
          readOnly={readOnly}
          staged={staged}
          onStateChange={onStateChange}
        />
      </div>

      {/* Cores tab — mounted lazily; keeps "New Core"/try-again links in /bento. */}
      {showTabs && entity.id && tab === "cores" && (
        <SEInstanceCoresPage header={null} baseRoute={ROUTES.BENTO_SE_INSTANCE} />
      )}

      {/* System Information tab — the console body with the shell hero
          suppressed (header={null} keeps it in its frosted bento layout). */}
      {showTabs && entity.id && tab === "systemInfo" && (
        <SEInstanceSystemInfoPage header={null} />
      )}
    </>
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
          <BentoSEInstanceBody
            entity={entity}
            isNew={isNew}
            readOnly={readOnly}
            staged={staged}
            onStateChange={onStateChange}
          />
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
