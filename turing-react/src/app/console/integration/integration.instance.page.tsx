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
    IconPlugConnectedX,
    IconSettings,
} from "@tabler/icons-react";
import axios from "axios";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";

const turIntegrationInstanceService = new TurIntegrationInstanceService();

const registeredEndpoints = new Set<string>();

function registerDumontRemote(endpoint: string) {
    const normalizedEndpoint = endpoint.replace(/\/$/, "");
    if (registeredEndpoints.has(normalizedEndpoint)) return;

    registerRemotes(
        [
            {
                name: "dumont_react",
                type: "module",
                entry: `${normalizedEndpoint}/dumont/remoteEntry.js`,
            },
        ],
        { force: true }
    );
    registeredEndpoints.add(normalizedEndpoint);
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
    const [isNew, setIsNew] = useState<boolean>(true);
    const [open, setOpen] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const navigate = useNavigate();
    const urlBase = `${ROUTES.INTEGRATION_INSTANCE}/${id}`;
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
    }), [t, dumontNavItems]);

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

                // Load dumont manifest dynamically from the connector endpoint
                registerDumontRemote(instance.endpoint);

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
                    .then((mod) => (mod as { default: { navItems: DumontNavItem[] } }).default)
                    .catch(() => null);

                Promise.all([providerPromise, i18nPromise, manifestPromise])
                    .then(([activeProvider, , manifest]) => {
                        if (!manifest) {
                            console.warn("Dumont manifest unavailable; showing turing-only nav");
                            return;
                        }
                        const items = manifest.navItems
                            .filter((item) => !activeProvider || !item.provider || item.provider === activeProvider)
                            .sort((a, b) => a.order - b.order)
                            .map((item) => ({
                                title: t(item.titleKey),
                                url: item.url,
                                icon: resolveIcon(item.icon),
                            }));
                        setDumontNavItems(items);
                    });
            }).catch(() => setError(t("common.connectionError", { resource: "Integration instance" })));
            setIsNew(false);
        }
    }, []);

    useSubPageBreadcrumb(breadcrumb);

    async function onDelete() {
        if (!integration) {
            toast.error(t("integration.notLoaded"));
            setOpen(false);
            return;
        }
        try {
            if (await turIntegrationInstanceService.delete(integration)) {
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
