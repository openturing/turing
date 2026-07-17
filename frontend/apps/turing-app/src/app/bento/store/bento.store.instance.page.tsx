import { useDeleteStoreInstance, useStoreInstance, useUpdateStoreInstance } from "@/api/queries/store-instance.queries";
import { ROUTES } from "@/app/routes.const";
import StoreInstanceSystemInfoPage from "@/app/console/store/system-info/store.instance.system-info.page";
import { BentoEntityShell, type BentoIdentity, type BentoShellFormState } from "@/components/bento";
import { GlobalBadge, GlobalReadOnlyNotice } from "@/components/infra-global-notice";
import { LoadProvider } from "@/components/loading-provider";
import { useInfraReadOnly } from "@/hooks/use-infra-read-only";
import type { TurStoreInstance } from "@/models/store/store-instance.model.ts";
import { IconAdjustments, IconDatabase, IconInfoCircle, IconLayersSubtract } from "@tabler/icons-react";
import type { Icon as TablerIcon } from "@tabler/icons-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BentoStoreCollections } from "./bento.store.instance.collections";
import { BENTO_STORE_FORM_ID, BentoStoreInstanceForm } from "./bento.store.instance.form";

type StoreDetailTab = "settings" | "collections" | "systemInfo";

/** A single frosted pill in the store detail tab-bar (see turing-bento-ui skill). */
function StoreTabPill({
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
 * Detail body for an existing store: a frosted pill tab-bar splitting the
 * connection form ("Settings") from the vector collections ("Collections") and
 * the read-only diagnostics ("System Information").
 *
 * The form stays mounted on every tab — hidden with `hidden` rather than
 * unmounted — so the hero-anchored Save button keeps its `form="…"` submit
 * target and the react-hook-form dirty state survives a tab switch. The other
 * tabs mount lazily so they fetch fresh each time they are opened. On a new
 * instance there are no collections or diagnostics yet, so the tab-bar is
 * suppressed and only the form renders (its Save creates the entity).
 */
function BentoStoreInstanceBody({
  entity,
  isNew,
  readOnly,
  staged,
  onStateChange,
}: Readonly<{
  entity: TurStoreInstance;
  isNew: boolean;
  readOnly: boolean;
  staged: BentoIdentity;
  onStateChange: (state: BentoShellFormState) => void;
}>) {
  const { t } = useTranslation();
  const [tab, setTab] = useState<StoreDetailTab>("settings");
  const showTabs = !isNew && !!entity.id;

  return (
    <>
      {showTabs && (
        <nav className="mb-6 flex flex-wrap gap-2">
          <StoreTabPill
            active={tab === "settings"}
            icon={IconAdjustments}
            label={t("store.nav.settings")}
            onSelect={() => setTab("settings")}
          />
          <StoreTabPill
            active={tab === "collections"}
            icon={IconLayersSubtract}
            label={t("store.nav.collections")}
            onSelect={() => setTab("collections")}
          />
          <StoreTabPill
            active={tab === "systemInfo"}
            icon={IconInfoCircle}
            label={t("store.nav.systemInfo")}
            onSelect={() => setTab("systemInfo")}
          />
        </nav>
      )}

      {/* Settings tab. Kept in the DOM on every tab (hidden, not unmounted) so
          the form="…" submit target and dirty state persist across switches. */}
      <div className={showTabs && tab !== "settings" ? "hidden" : undefined}>
        <BentoStoreInstanceForm
          value={entity}
          isNew={isNew}
          readOnly={readOnly}
          staged={staged}
          onStateChange={onStateChange}
        />
      </div>

      {/* Collections tab — mounted lazily, refetches its list each time. */}
      {showTabs && entity.id && tab === "collections" && (
        <BentoStoreCollections storeId={entity.id} readOnly={readOnly} />
      )}

      {/* System Information tab — the console body with the shell hero
          suppressed (header={null} keeps it in its frosted bento layout). */}
      {showTabs && entity.id && tab === "systemInfo" && (
        <StoreInstanceSystemInfoPage header={null} />
      )}
    </>
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
          <BentoStoreInstanceBody
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
    return shell({} as TurStoreInstance, t("store.newInstance"));
  }

  return (
    <LoadProvider checkIsNotUndefined={store} error={error} tryAgainUrl={`${ROUTES.BENTO_STORE_INSTANCE}/${id}`}>
      {store && shell(store, store.title)}
    </LoadProvider>
  );
}
