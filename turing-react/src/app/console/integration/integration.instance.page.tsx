import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SubPage } from "@/components/sub.page";
import { type BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurIntegrationInstance } from "@/models/integration/integration-instance.model";
import { TurIntegrationInstanceService } from "@/services/integration/integration.service";
import {
    IconAdjustmentsSearch,
    IconChartBar,
    IconGitCommit,
    IconGraph,
    IconInfoCircle,
    IconListCheck,
    IconPlugConnectedX,
    IconSettings,
    IconTools
} from "@tabler/icons-react";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";

const turIntegrationInstanceService = new TurIntegrationInstanceService();

export default function IntegrationInstancePage() {
    const { id } = useParams() as { id: string };
    const { t } = useTranslation();
    const [integration, setIntegration] = useState<TurIntegrationInstance>();
    const [isNew, setIsNew] = useState<boolean>(true);
    const [open, setOpen] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const navigate = useNavigate()
    const urlBase = `${ROUTES.INTEGRATION_INSTANCE}/${id}`
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
            {
                title: t("integration.nav.sources"),
                url: "/source",
                icon: IconGitCommit,
            },
            {
                title: t("integration.nav.indexingRules"),
                url: "/indexing-rule",
                icon: IconTools,
            },
            {
                title: t("integration.nav.indexingManager"),
                url: "/indexing-manager",
                icon: IconAdjustmentsSearch
            },
            {
                title: t("integration.nav.monitoring"),
                url: "/monitoring",
                icon: IconGraph,
            },
            {
                title: t("integration.nav.indexingStats"),
                url: "/indexing-stats",
                icon: IconChartBar,
            },
            {
                title: t("integration.nav.doubleCheck"),
                url: "/double-check",
                icon: IconListCheck,
            },
            {
                title: t("integration.nav.systemInfo"),
                url: "/system-info",
                icon: IconInfoCircle,
            },
        ],
    }), [t]);

    useEffect(() => {
        if (id === "new") {
            turIntegrationInstanceService.query().then(() => {
                setIntegration({} as TurIntegrationInstance);
            }).catch(() => setError(t("common.connectionError", { resource: "Integration instances" })));
            setIsNew(true);
        } else {
            turIntegrationInstanceService.get(id).then((integration) => {
                setIntegration(integration);
                setBreadcrumb([{ label: integration.title, href: `${ROUTES.INTEGRATION_INSTANCE}/${integration.id}` }]);
            }).catch(() => setError(t("common.connectionError", { resource: "Integration instance" })));
            setIsNew(false);
        }
    }, []);

    useSubPageBreadcrumb(breadcrumb);



    async function onDelete() {
        console.log("delete");
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
    )
}
