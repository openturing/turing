import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SubPage } from "@/components/sub.page";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import type { TurSNSiteStatus } from "@/models/sn/sn-site-monitoring.model.ts";
import type { TurSNSite } from "@/models/sn/sn-site.model.ts";
import { TurSNSiteService } from "@/services/sn/sn.service";
import {
    IconAlignBoxCenterStretch,
    IconCpu2,
    IconDashboard,
    IconDatabase,
    IconFilter,
    IconGitMerge,
    IconInbox,
    IconLanguage,
    IconNumber123,
    IconScale,
    IconSearch,
    IconSettings,
    IconSpeakerphone
} from "@tabler/icons-react";
import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";
const turSNSiteService = new TurSNSiteService();

export default function SNSitePage() {
    const { id } = useParams() as { id: string };
    const [snSite, setSnSite] = useState<TurSNSite>({} as TurSNSite);
    const [snStatus, setSnStatus] = useState<TurSNSiteStatus | null>(null);
    const [isNew, setIsNew] = useState<boolean>(true);
    const [open, setOpen] = useState(false);
    const navigate = useNavigate()
    const urlBase = `${ROUTES.SN_INSTANCE}/${id}`;
    const [error, setError] = useState<string | null>(null);
    const { pushItem, popItem } = useBreadcrumb();


    const data = useMemo(() => {
        if (isNew) {
            return {
                navMain: [
                    {
                        title: "Settings",
                        url: "/detail",
                        icon: IconSettings,
                    },
                ],
            };
        }

        return {
            navMain: [
                {
                    title: "Settings",
                    url: "/detail",
                    icon: IconSettings,
                },
                {
                    title: "Multi Languages",
                    url: "/locale",
                    icon: IconLanguage,
                },
                {
                    title: "Behavior",
                    url: "/behavior",
                    icon: IconScale,
                },
                {
                    title: "Fields",
                    url: "/field",
                    icon: IconAlignBoxCenterStretch,
                },
                {
                    title: "Facets",
                    url: "/facet",
                    icon: IconFilter,
                },
                {
                    title: "Generative AI",
                    url: "/ai",
                    icon: IconCpu2,
                },
                {
                    title: "Result Ranking",
                    url: "/result-ranking",
                    icon: IconNumber123,
                },
                {
                    title: "Merge Providers",
                    url: "/merge-providers",
                    icon: IconGitMerge,
                },
                {
                    title: "Spotlight",
                    url: "/spotlight",
                    icon: IconSpeakerphone,
                },
                {
                    title: "Top Search Terms",
                    url: "/top-terms",
                    icon: IconDashboard,
                },
            ],
            counts: [
                {
                    title: "Queue",
                    icon: IconInbox,
                    count: snStatus?.queue ?? 0,
                },
                {
                    title: "Indexed",
                    icon: IconDatabase,
                    count: snStatus?.documents ?? 0,
                },
            ],
        };
    }, [isNew, snStatus]);
    useEffect(() => {
        let added = false;

        if (id === "new") {
            turSNSiteService.query().then(() => {
                setSnSite({} as TurSNSite);
                setSnStatus(null);
                setIsNew(true);
                pushItem({ label: "New Site" });
                added = true;
            }).catch(() => setError("Connection error or timeout while fetching Semantic Navigation sites."));
        } else {
            turSNSiteService.get(id).then((site) => {
                setSnSite(site);
                pushItem({ label: site.name, href: `${ROUTES.SN_INSTANCE}/${site.id}` });
                added = true;
            }).catch(() => setError("Connection error or timeout while fetching Semantic Navigation site details."));

            turSNSiteService.getStatus(id)
                .then(setSnStatus)
                .catch(() => setError("Connection error or timeout while fetching Semantic Navigation site status."));
            setIsNew(false);
        }

        return () => {
            if (added) popItem();
        };
    }, [id]);

    async function onDelete() {
        try {
            if (await turSNSiteService.delete(snSite)) {
                toast.success(`The ${snSite.name} Semantic Navigation was deleted`);
                navigate(`${ROUTES.SN_INSTANCE}`);
            } else {
                toast.error(`The ${snSite.name} Semantic Navigation was not deleted`);
            }

        } catch (error) {
            console.error("Form submission error", error);
            toast.error(`The ${snSite.name} Semantic Navigation was not deleted`);
        }
        setOpen(false);
    }
    async function onExport() {
        try {
            const response: Blob | null = await turSNSiteService.export(snSite);
            if (response) {
                // If the Blob has a name property, use it; otherwise fallback
                const fileName = (response as any).name || `sn-site-${snSite.name}-${new Date().toISOString()}.zip`;
                const url = globalThis.URL.createObjectURL(response);
                const a = document.createElement('a');
                a.href = url;
                a.download = fileName;
                document.body.appendChild(a);
                a.click();
                globalThis.URL.revokeObjectURL(url);
                a.remove();
                toast.success(`The ${snSite.name} Semantic Navigation was exported`);
            } else {
                toast.error(`The ${snSite.name} Semantic Navigation was not exported`);
            }
        } catch (error) {
            console.error("Form submission error", error);
            toast.error(`The ${snSite.name} Semantic Navigation was not exported`);
        }
        setOpen(false);
    }

    return (
        <LoadProvider checkIsNotUndefined={snSite} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}`}>
            <SubPage icon={IconSearch} feature={"Semantic Navigation"} name={snSite.name}
                onDelete={onDelete} data={data} isNew={isNew} urlBase={urlBase} open={open} setOpen={setOpen} onExport={onExport} />
        </LoadProvider>
    )
}
