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
import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";

const turIntegrationInstanceService = new TurIntegrationInstanceService();
const data = {
    navMain: [
        {
            title: "Settings",
            url: "/detail",
            icon: IconSettings,
        },
        {
            title: "Sources",
            url: "/source",
            icon: IconGitCommit,
        },
        {
            title: "Indexing Rules",
            url: "/indexing-rule",
            icon: IconTools,
        },
        {
            title: "Indexing Manager",
            url: "/indexing-manager",
            icon: IconAdjustmentsSearch
        },
        {
            title: "Monitoring",
            url: "/monitoring",
            icon: IconGraph,
        },
        {
            title: "Indexing Stats",
            url: "/indexing-stats",
            icon: IconChartBar,
        },
        {
            title: "Double Check",
            url: "/double-check",
            icon: IconListCheck,
        },
        {
            title: "System Information",
            url: "/system-info",
            icon: IconInfoCircle,
        },
    ],
}

export default function IntegrationInstancePage() {
    const { id } = useParams() as { id: string };
    const [integration, setIntegration] = useState<TurIntegrationInstance>();
    const [isNew, setIsNew] = useState<boolean>(true);
    const [open, setOpen] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const navigate = useNavigate()
    const urlBase = `${ROUTES.INTEGRATION_INSTANCE}/${id}`
    const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>(
        id === "new" ? [{ label: "New Integration" }] : [{ label: "…" }]
    );

    useEffect(() => {
        if (id === "new") {
            turIntegrationInstanceService.query().then(() => {
                setIntegration({} as TurIntegrationInstance);
            }).catch(() => setError("Connection error or timeout while fetching Integration instances."));
            setIsNew(true);
        } else {
            turIntegrationInstanceService.get(id).then((integration) => {
                setIntegration(integration);
                setBreadcrumb([{ label: integration.title, href: `${ROUTES.INTEGRATION_INSTANCE}/${integration.id}` }]);
            }).catch(() => setError("Connection error or timeout while fetching Integration instance."));
            setIsNew(false);
        }
    }, []);

    useSubPageBreadcrumb(breadcrumb);



    async function onDelete() {
        console.log("delete");
        if (!integration) {
            toast.error("Integration instance is not loaded.");
            setOpen(false);
            return;
        }
        try {
            if (await turIntegrationInstanceService.delete(integration)) {
                toast.success(`The ${integration.title} Integration Instance was deleted`);
                navigate(`${ROUTES.INTEGRATION_INSTANCE}`);
            } else {
                toast.error(`The ${integration.title} Integration Instance was not deleted`);
            }

        } catch (error) {
            console.error("Form submission error", error);
            toast.error(`The ${integration.title} Integration Instance was not deleted`);
        }
        setOpen(false);
    }

    return (
        <LoadProvider checkIsNotUndefined={integration} error={error} tryAgainUrl={`${ROUTES.INTEGRATION_INSTANCE}/${id}`}>
            {integration && <SubPage icon={IconPlugConnectedX} feature={"Integration"} name={integration.title}
                onDelete={onDelete} data={data} isNew={isNew} urlBase={urlBase} open={open} setOpen={setOpen} />}
        </LoadProvider>
    )
}
