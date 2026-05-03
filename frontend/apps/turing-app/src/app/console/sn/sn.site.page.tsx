import { useDeleteSnSite } from "@/api/queries/sn-site.queries";
import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SubPage } from "@/components/sub.page";
import { type BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurSNSiteStatus } from "@/models/sn/sn-site-monitoring.model.ts";
import type { TurSNSite } from "@/models/sn/sn-site.model.ts";
import { TurSNSiteService } from "@/services/sn/sn.service";
import { TurGlobalSettingsService } from "@/services/system/global-settings.service";
import {
    IconAlignBoxCenterStretch,
    IconArrowsSort,
    IconCpu2,
    IconDashboard,
    IconDatabase,
    IconFilter,
    IconGitMerge,
    IconInbox,
    IconLanguage,
    IconNumber123,
    IconGavel,
    IconScale,
    IconSearch,
    IconSettings,
    IconSparkles,
    IconSpeakerphone
} from "@tabler/icons-react";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";
import { DialogExport } from "./dialog.export";
const turSNSiteService = new TurSNSiteService();
const turGlobalSettingsService = new TurGlobalSettingsService();

export default function SNSitePage() {
    const { id } = useParams() as { id: string };
    const [snSite, setSnSite] = useState<TurSNSite>({} as TurSNSite);
    const [snStatus, setSnStatus] = useState<TurSNSiteStatus | null>(null);
    const [isNew, setIsNew] = useState<boolean>(true);
    const [hasDefaultLlm, setHasDefaultLlm] = useState(false);
    const [open, setOpen] = useState(false);
    const [exportDialogOpen, setExportDialogOpen] = useState(false);
    const navigate = useNavigate()
    const { t } = useTranslation();
    const urlBase = `${ROUTES.SN_INSTANCE}/${id}`;
    const deleteMutation = useDeleteSnSite();
    const [error, setError] = useState<string | null>(null);
    const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>(
        id === "new" ? [{ label: t("sn.newSite") }] : [{ label: "…" }]
    );

    const data = useMemo(() => {
        if (isNew) {
            return {
                navMain: [
                    {
                        title: t("sn.settings.title"),
                        url: "/detail",
                        icon: IconSettings,
                    },
                ],
            };
        }

        const navItems = [];

        if (hasDefaultLlm) {
            navItems.push({
                title: t("sn.insights.title"),
                url: "/insights",
                icon: IconSparkles,
            });
        }

        navItems.push(
            {
                title: t("sn.settings.title"),
                url: "/detail",
                icon: IconSettings,
            },
            {
                title: t("sn.multiLanguage.title"),
                url: "/locale",
                icon: IconLanguage,
            },
            {
                title: t("sn.behavior.title"),
                url: "/behavior",
                icon: IconScale,
            },
            {
                title: t("sn.fields.title"),
                url: "/field",
                icon: IconAlignBoxCenterStretch,
            },
            {
                title: t("sn.facets.title"),
                url: "/facet",
                icon: IconFilter,
            },
            {
                title: t("sn.customSort.title"),
                url: "/custom-sort",
                icon: IconArrowsSort,
            },
            {
                title: t("sn.searchRule.title"),
                url: "/search-rule",
                icon: IconGavel,
            },
            {
                title: t("sn.genai.title"),
                url: "/ai",
                icon: IconCpu2,
            },
            {
                title: t("sn.resultRanking.title"),
                url: "/result-ranking",
                icon: IconNumber123,
            },
            {
                title: t("sn.mergeProviders.title"),
                url: "/merge-providers",
                icon: IconGitMerge,
            },
            {
                title: t("sn.spotlight.title"),
                url: "/spotlight",
                icon: IconSpeakerphone,
            },
            {
                title: t("sn.topSearchTerms.title"),
                url: "/top-terms",
                icon: IconDashboard,
            },
        );

        return {
            navMain: navItems,
            counts: [
                {
                    title: t("sn.queue"),
                    icon: IconInbox,
                    count: snStatus?.queue ?? 0,
                },
                {
                    title: t("sn.indexed"),
                    icon: IconDatabase,
                    count: snStatus?.documents ?? 0,
                },
            ],
        };
    }, [isNew, hasDefaultLlm, snStatus, t]);
    useEffect(() => {
        if (id === "new") {
            turSNSiteService.query().then(() => {
                setSnSite({} as TurSNSite);
                setSnStatus(null);
                setIsNew(true);
            }).catch(() => setError(t("common.connectionError", { resource: "Semantic Navigation sites" })));
        } else {
            turSNSiteService.get(id).then((site) => {
                setSnSite(site);
                setBreadcrumb([{ label: site.name, href: `${ROUTES.SN_INSTANCE}/${site.id}` }]);
            }).catch(() => setError(t("common.connectionError", { resource: "Semantic Navigation site details" })));

            turSNSiteService.getStatus(id)
                .then(setSnStatus)
                .catch(() => setError(t("common.connectionError", { resource: "Semantic Navigation site status" })));
            turGlobalSettingsService.query()
                .then((settings) => setHasDefaultLlm(!!settings.defaultLlmId))
                .catch(() => setHasDefaultLlm(false));
            setIsNew(false);
        }
    }, [id]);

    useSubPageBreadcrumb(breadcrumb);

    async function onDelete() {
        try {
            if (await deleteMutation.mutateAsync(snSite)) {
                toast.success(t("sn.siteDeleted", { name: snSite.name }));
                navigate(`${ROUTES.SN_INSTANCE}`);
            } else {
                toast.error(t("sn.siteNotDeleted", { name: snSite.name }));
            }

        } catch (error) {
            console.error("Form submission error", error);
            toast.error(t("sn.siteNotDeleted", { name: snSite.name }));
        }
        setOpen(false);
    }
    function onExport() {
        setExportDialogOpen(true);
    }

    return (
        <LoadProvider checkIsNotUndefined={snSite} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}`}>
            <SubPage icon={IconSearch} feature={t("sn.title")} name={snSite.name}
                onDelete={onDelete} data={data} isNew={isNew} urlBase={urlBase} open={open} setOpen={setOpen} onExport={onExport} />
            {snSite.id && (
                <DialogExport snSite={snSite} open={exportDialogOpen} onOpenChange={setExportDialogOpen} />
            )}
        </LoadProvider>
    )
}
