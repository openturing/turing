import { useDeleteIntegrationInstance, useIntegrationInstance, useUpdateIntegrationInstance } from "@/api/queries/integration-instance.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityShell } from "@/components/bento";
import { GlobalBadge, GlobalReadOnlyNotice } from "@/components/infra-global-notice";
import { LoadProvider } from "@/components/loading-provider";
import { SidebarProvider } from "@/components/ui/sidebar";
import { useInfraReadOnly } from "@/hooks/use-infra-read-only";
import { resolveIcon } from "@/lib/icon-resolver";
import type { TurIntegrationInstance } from "@/models/integration/integration-instance.model.ts";
import { loadRemote, registerRemotes } from "@module-federation/runtime";
import { IconInbox, IconPlugConnectedX, IconSettings } from "@tabler/icons-react";
import axios from "axios";
import { lazy, Suspense, useEffect, useMemo, useState, type ElementType } from "react";
import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router-dom";
import { BENTO_INTEGRATION_FORM_ID, BentoIntegrationInstanceForm } from "./bento.integration.instance.form";

/**
 * Bento Integration detail — T553 + Dumont remote wiring.
 *
 * The integration surface is Turing-owned **settings** plus a large set of
 * connector screens (sources, indexing, monitoring, …) served by the **Dumont**
 * micro-frontend over Module Federation. This page is a layout: it registers
 * the Dumont remote, loads its manifest nav, and renders a bento sub-nav
 * (Settings + Dumont items). The `:id/*` splat lets Dumont's relative routes
 * match under the Bento shell — Dumont derives the `/bento` prefix from the URL
 * (see its `routes.const.ts`), so its internal links stay inside Bento.
 */

const RemoteDumontRoutes = lazy(() =>
  loadRemote("dumont_react/DumontRoutes").then((mod) => ({
    default: (mod as { default: React.ComponentType }).default,
  })),
);

interface DumontNavItem {
  titleKey: string;
  url: string;
  icon: string;
  order: number;
  provider?: string;
}

const registeredIntegrations = new Set<string>();

function registerDumontRemote(integrationId: string) {
  if (registeredIntegrations.has(integrationId)) return;
  registerRemotes(
    [{ name: "dumont_react", type: "module", entry: `/api/v2/integration/${integrationId}/federation/remoteEntry.js` }],
    { force: true },
  );
  registeredIntegrations.add(integrationId);
}

interface ResolvedNavItem {
  title: string;
  /** Splat path relative to the instance base, e.g. "source". */
  path: string;
  icon: ElementType;
}

export default function BentoIntegrationInstancePage() {
  const params = useParams();
  const id = params.id as string;
  const splat = params["*"] ?? "";
  const { t } = useTranslation();
  const isNew = id === "new";
  const isSettings = splat === "" || splat === "settings";

  const { data: integration, isError } = useIntegrationInstance(isNew ? undefined : id);
  const error = isError ? t("common.connectionError", { resource: t("integration.title") }) : null;

  const updateMutation = useUpdateIntegrationInstance();
  const deleteMutation = useDeleteIntegrationInstance();
  const { isGlobal, readOnly } = useInfraReadOnly(integration?.tenantId);

  const [dumontNavItems, setDumontNavItems] = useState<ResolvedNavItem[]>([]);
  const [queueSize, setQueueSize] = useState<number | undefined>(undefined);

  const instanceBase = `${ROUTES.BENTO_INTEGRATION_INSTANCE}/${id}`;

  // Register the Dumont remote + load its manifest nav (existing instances only).
  useEffect(() => {
    if (isNew) return;
    registerDumontRemote(id);

    const providerPromise = axios
      .get<{ provider?: string }>(`/v2/integration/${id}/connector/status`)
      .then(({ data }) => data.provider ?? null)
      .catch(() => null as string | null);

    const i18nPromise = loadRemote("dumont_react/register")
      .then((mod) => (mod as { registerDumontTranslations: () => void }).registerDumontTranslations())
      .catch(() => { /* dumont i18n unavailable */ });

    const manifestPromise = loadRemote("dumont_react/manifest")
      .then((mod) => mod as { default: { navItems: DumontNavItem[] }; resolveIcon?: (name: string) => ElementType })
      .catch(() => null);

    Promise.all([providerPromise, i18nPromise, manifestPromise]).then(([activeProvider, , manifestMod]) => {
      if (!manifestMod) {
        console.warn("Dumont manifest unavailable; showing settings only");
        return;
      }
      const resolveDumontIcon = manifestMod.resolveIcon ?? resolveIcon;
      const items = manifestMod.default.navItems
        .filter((item) => !activeProvider || !item.provider || item.provider === activeProvider)
        .sort((a, b) => a.order - b.order)
        .map((item) => ({ title: t(item.titleKey), path: item.url.replace(/^\//, ""), icon: resolveDumontIcon(item.icon) }));
      setDumontNavItems(items);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, isNew]);

  // Poll the connector queue size (existing instances only).
  useEffect(() => {
    if (isNew) return;
    let cancelled = false;
    const fetchQueue = () => {
      axios
        .get<{ queue: number }>(`/v2/integration/${id}/connector/queue/status`)
        .then(({ data }) => { if (!cancelled) setQueueSize(data.queue); })
        .catch(() => { /* keep last known value */ });
    };
    fetchQueue();
    const intervalId = window.setInterval(fetchQueue, 10_000);
    return () => { cancelled = true; window.clearInterval(intervalId); };
  }, [id, isNew]);

  const activePath = splat.split("/")[0];

  const subNav = useMemo(() => {
    if (isNew) return null;
    return (
      <nav aria-label={t("integration.title")} className="mb-6 flex flex-wrap items-center gap-2">
        <SubNavPill to={instanceBase} icon={IconSettings} label={t("integration.nav.settings")} active={isSettings} />
        {dumontNavItems.map((item) => (
          <SubNavPill
            key={item.path}
            to={`${instanceBase}/${item.path}`}
            icon={item.icon}
            label={item.title}
            active={activePath === item.path}
            badge={item.path === "monitoring" && queueSize ? queueSize : undefined}
          />
        ))}
      </nav>
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isNew, dumontNavItems, isSettings, activePath, queueSize, instanceBase, t]);

  // --- Dumont remote sub-page ---
  if (!isSettings) {
    return (
      <>
        {subNav}
        {/*
         * Dumont's remote pages render the design-system `SubPageHeader`, whose
         * `SidebarTrigger` calls `useSidebar()` — so they need a `SidebarProvider`
         * ancestor. The console root provides one; the Bento shell uses its own
         * nav rail and does not, so we scope a provider here. With no `<Sidebar>`
         * mounted the trigger is a harmless no-op toggle.
         */}
        <SidebarProvider defaultOpen={false} className="min-h-0 block">
          <Suspense fallback={<div className="flex items-center justify-center py-12 text-muted-foreground">{t("common.loading", { defaultValue: "Loading…" })}</div>}>
            <RemoteDumontRoutes />
          </Suspense>
        </SidebarProvider>
      </>
    );
  }

  // --- Settings sub-page (Turing-owned; hero + form) ---
  function settingsShell(entity: TurIntegrationInstance, headlineFallback: string) {
    return (
      <>
        {subNav}
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
      </>
    );
  }

  if (isNew) {
    return settingsShell({} as TurIntegrationInstance, t("integration.newInstance"));
  }

  return (
    <LoadProvider checkIsNotUndefined={integration} error={error} tryAgainUrl={instanceBase}>
      {integration && settingsShell(integration, integration.title)}
    </LoadProvider>
  );
}

function SubNavPill({
  to,
  icon: Icon,
  label,
  active,
  badge,
}: Readonly<{ to: string; icon: ElementType; label: string; active: boolean; badge?: number }>) {
  return (
    <Link
      to={to}
      aria-current={active ? "page" : undefined}
      className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-sm transition-colors ${
        active
          ? "border-amber-500/40 bg-amber-500/10 text-amber-700 dark:text-amber-300"
          : "border-border bg-card/40 text-muted-foreground backdrop-blur hover:bg-card/70"
      }`}
    >
      <Icon className="size-4" />
      {label}
      {badge != null && badge > 0 && (
        <span className="ml-0.5 inline-flex items-center gap-0.5 rounded-full bg-amber-500/20 px-1.5 text-[10px] font-semibold text-amber-700 dark:text-amber-300">
          <IconInbox className="size-3" />
          {badge}
        </span>
      )}
    </Link>
  );
}
