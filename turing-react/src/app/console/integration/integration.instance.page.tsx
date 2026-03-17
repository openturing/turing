import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SubPage } from "@/components/sub.page";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import type { TurIntegrationInstance } from "@/models/integration/integration-instance.model";
import { TurIntegrationInstanceService } from "@/services/integration/integration.service";
import {
    IconAdjustmentsSearch,
    IconGitCommit,
    IconGraph,
    IconListCheck,
    IconPlugConnectedX,
    IconSettings,
    IconTools
} from "@tabler/icons-react";
import { useEffect, useRef, useState } from "react";
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
            title: "Double Check",
            url: "/double-check",
            icon: IconListCheck,
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
    const { pushItem, popItem } = useBreadcrumb();
    const breadcrumbAdded = useRef(false);

    useEffect(() => {
        if (id === "new") {
            turIntegrationInstanceService.query().then(() => {
                setIntegration({} as TurIntegrationInstance);
                if (!breadcrumbAdded.current) {
                    pushItem({ label: "New Integration" });
                    breadcrumbAdded.current = true;
                }
            }).catch(() => setError("Connection error or timeout while fetching Integration instances."));
            setIsNew(true);
        } else {
            turIntegrationInstanceService.get(id).then((integration) => {
                setIntegration(integration);
                if (!breadcrumbAdded.current) {
                    pushItem({ label: integration.title, href: `${ROUTES.INTEGRATION_INSTANCE}/${integration.id}` });
                    breadcrumbAdded.current = true;
                }
            }).catch(() => setError("Connection error or timeout while fetching Integration instance."));
            setIsNew(false);
        }
    }, []);

    useEffect(() => {
        return () => {
            if (breadcrumbAdded.current) {
                popItem();
                breadcrumbAdded.current = false;
            }
        };
    }, []);



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
