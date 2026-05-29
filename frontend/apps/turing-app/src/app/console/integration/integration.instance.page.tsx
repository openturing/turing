import { useDeleteIntegrationInstance } from "@/api/queries/integration-instance.queries";
import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SubPage } from "@/components/sub.page";
import { type BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { resolveIcon } from "@/lib/icon-resolver";
import type { TurIntegrationInstance } from "@/models/integration/integration-instance.model";
import { TurIntegrationInstanceService } from "@/services/integration/integration.service";
import { loadRemote, registerRemotes } from "@module-federation/runtime";
import {
    IconInbox,
    IconPlugConnectedX,
    IconSettings,
} from "@tabler/icons-react";
import axios from "axios";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const turIntegrationInstanceService = new TurIntegrationInstanceService();

const registeredEndpoints = new Set<string>();

function registerDumontRemote(integrationId: string) {
    if (registeredEndpoints.has(integrationId)) return;

    registerRemotes(
        [
            {
                name: "dumont_react",
                type: "module",
                entry: `/api/v2/integration/${integrationId}/federation/remoteEntry.js`,
            },
        ],
        { force: true }
    );
    registeredEndpoints.add(integrationId);
}

interface DumontNavItem {
    titleKey: string;
    url: string;
    icon: string;
    order: number;
    provider?: string;
}

export default function IntegrationInstancePage() {
    const { id } = useParams() as { id: string };
    const { t } = useTranslation();
    const [integration, setIntegration] = useState<TurIntegrationInstance>();
    const [dumontNavItems, setDumontNavItems] = useState<{ title: string; url: string; icon: React.ElementType }[]>([]);
    const [queueSize, setQueueSize] = useState<number | undefined>(undefined);
    const [isNew, setIsNew] = useState<boolean>(true);
    const [open, setOpen] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const navigate = useNavigate();
    const urlBase = `${ROUTES.INTEGRATION_INSTANCE}/${id}`;
    const deleteMutation = useDeleteIntegrationInstance();
    const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>(
        id === "new" ? [{ label: t("integration.newIntegration") }] : [{ label: "…" }]
    );

    const data = useMemo(() => ({
        navMain: [
            {
                title: t("integration.nav.settings"),
                url: "/detail",
                icon: IconSettings,
            },
            ...dumontNavItems,
        ],
        counts: [
            {
                title: t("integration.nav.queue"),
                icon: IconInbox,
                count: queueSize ?? 0,
            },
        ],
    }), [t, dumontNavItems, queueSize]);

    useEffect(() => {
        if (id === "new") {
            turIntegrationInstanceService.query().then(() => {
                setIntegration({} as TurIntegrationInstance);
            }).catch(() => setError(t("common.connectionError", { resource: "Integration instances" })));
            setIsNew(true);
        } else {
            turIntegrationInstanceService.get(id).then((instance) => {
                setIntegration(instance);
                setBreadcrumb([{ label: instance.title, href: `${ROUTES.INTEGRATION_INSTANCE}/${instance.id}` }]);

                // Load dumont manifest dynamically via backend proxy
                registerDumontRemote(instance.id);

                // Fetch provider, translations and manifest, then build nav
                const providerPromise = axios
                    .get<{ provider?: string }>(`/v2/integration/${id}/connector/status`)
                    .then(({ data }) => data.provider ?? null)
                    .catch(() => null as string | null);

                const i18nPromise = loadRemote("dumont_react/register")
                    .then((mod) => {
                        (mod as { registerDumontTranslations: () => void }).registerDumontTranslations();
                    })
                    .catch(() => { /* dumont i18n unavailable */ });

                const manifestPromise = loadRemote("dumont_react/manifest")
                    .then((mod) => mod as {
                        default: { navItems: DumontNavItem[] };
                        resolveIcon?: (name: string) => React.ElementType;
                    })
                    .catch(() => null);

                Promise.all([providerPromise, i18nPromise, manifestPromise])
                    .then(([activeProvider, , manifestMod]) => {
                        if (!manifestMod) {
                            console.warn("Dumont manifest unavailable; showing turing-only nav");
                            return;
                        }
                        // Prefer Dumont's own icon resolver (it ships the icons), so new
                        // connector icons need no change here. Fall back to the local map.
                        const resolveDumontIcon = manifestMod.resolveIcon ?? resolveIcon;
                        const items = manifestMod.default.navItems
                            .filter((item) => !activeProvider || !item.provider || item.provider === activeProvider)
                            .sort((a, b) => a.order - b.order)
                            .map((item) => ({
                                title: t(item.titleKey),
                                url: item.url,
                                icon: resolveDumontIcon(item.icon),
                            }));
                        setDumontNavItems(items);
                    });
            }).catch(() => setError(t("common.connectionError", { resource: "Integration instance" })));
            setIsNew(false);
        }
    }, []);

    useEffect(() => {
        if (id === "new") return;
        let cancelled = false;

        const fetchQueue = () => {
            axios
                .get<{ queue: number }>(`/v2/integration/${id}/connector/queue/status`)
                .then(({ data }) => {
                    if (!cancelled) setQueueSize(data.queue);
                })
                .catch(() => { /* keep last known value on transient errors */ });
        };

        fetchQueue();
        const intervalId = window.setInterval(fetchQueue, 10_000);
        return () => {
            cancelled = true;
            window.clearInterval(intervalId);
        };
    }, [id]);

    useSubPageBreadcrumb(breadcrumb);

    async function onDelete() {
        if (!integration) {
            toast.error(t("integration.notLoaded"));
            setOpen(false);
            return;
        }
        try {
            if (await deleteMutation.mutateAsync(integration)) {
                toast.success(t("integration.deleted", { name: integration.title }));
                navigate(`${ROUTES.INTEGRATION_INSTANCE}`);
            } else {
                toast.error(t("integration.notDeleted", { name: integration.title }));
            }
        } catch (error) {
            console.error("Form submission error", error);
            toast.error(t("integration.notDeleted", { name: integration.title }));
        }
        setOpen(false);
    }

    return (
        <LoadProvider checkIsNotUndefined={integration} error={error} tryAgainUrl={`${ROUTES.INTEGRATION_INSTANCE}/${id}`}>
            {integration && <SubPage icon={IconPlugConnectedX} feature={t("integration.title")} name={integration.title}
                onDelete={onDelete} data={data} isNew={isNew} urlBase={urlBase} open={open} setOpen={setOpen} />}
        </LoadProvider>
    );
}
