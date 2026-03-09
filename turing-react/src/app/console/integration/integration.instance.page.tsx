import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SubPage } from "@/components/sub.page";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import type { TurIntegrationInstance } from "@/models/integration/integration-instance.model";
import { TurIntegrationInstanceService } from "@/services/integration/integration.service";
import {
    IconAdjustmentsSearch,
    IconCloudDownload,
    IconCloudUpload,
    IconGitCommit,
    IconGraph,
    IconPlugConnectedX,
    IconPlus,
    IconSettings,
    IconTools,
    IconTrash
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
            icon: IconAdjustmentsSearch,
            children: [
                {
                    title: "Indexing",
                    url: "/indexing-manager/indexing",
                    icon: IconPlus,
                },
                {
                    title: "Deindexing",
                    url: "/indexing-manager/deindexing",
                    icon: IconTrash,
                },
                {
                    title: "Publishing",
                    url: "/indexing-manager/publishing",
                    icon: IconCloudUpload,
                },
                {
                    title: "Unpublishing",
                    url: "/indexing-manager/unpublishing",
                    icon: IconCloudDownload,
                }
            ]
        },
        {
            title: "Monitoring",
            url: "/monitoring",
            icon: IconGraph,
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
    useEffect(() => {
        let added = false;
        if (id === "new") {
            turIntegrationInstanceService.query().then(() => {
                setIntegration({} as TurIntegrationInstance);
                pushItem({ label: "New Integration" });
                added = true;
            }).catch(() => setError("Connection error or timeout while fetching Integration instances."));
            setIsNew(true);
        } else {
            turIntegrationInstanceService.get(id).then((integration) => {
                setIntegration(integration);
                pushItem({ label: integration.title, href: `${ROUTES.INTEGRATION_INSTANCE}/${integration.id}` });
                added = true;
            }).catch(() => setError("Connection error or timeout while fetching Integration instance."));
            setIsNew(false);
        }
        return () => {
            if (added) popItem();
        };
    }, [])



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
