import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteMergeForm } from "@/components/sn/merge/sn.site.merge.form";
import { SubPageHeader } from "@/components/sub.page.header";
import type { TurSNSiteMerge } from "@/models/sn/sn-site-merge.model";
import { TurSNSiteMergeService } from "@/services/sn/sn.site.merge.service";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import { IconGitMerge } from "@tabler/icons-react";
import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";

const turSNSiteMergeService = new TurSNSiteMergeService();

export default function SNSiteMergeProvidersPage() {
    const navigate = useNavigate();
    const { id, mergeProviderId } = useParams() as { id: string; mergeProviderId: string };
    const [mergeProvider, setMergeProvider] = useState<TurSNSiteMerge>();
    const [isNew, setIsNew] = useState<boolean>(true);
    const [open, setOpen] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const { pushItem, popItem } = useBreadcrumb();
    const breadcrumbAdded = useRef(false);

    useEffect(() => {
        if (mergeProviderId === "new") {
            turSNSiteMergeService.getStructure(id).then(setMergeProvider).catch(() => setError("Connection error or timeout while fetching merge provider structure."));
            if (!breadcrumbAdded.current) {
                pushItem({ label: "Merge Providers", href: `${ROUTES.SN_INSTANCE}/${id}/merge-providers` });
                pushItem({ label: "New" });
                breadcrumbAdded.current = true;
            }
        } else {
            turSNSiteMergeService.get(id, mergeProviderId).then((merge) => {
                setMergeProvider(merge);
                if (!breadcrumbAdded.current) {
                    const label = merge.providerFrom && merge.providerTo
                        ? `${merge.providerFrom} → ${merge.providerTo}`
                        : mergeProviderId;
                    pushItem({ label: "Merge Providers", href: `${ROUTES.SN_INSTANCE}/${id}/merge-providers` });
                    pushItem({ label });
                    breadcrumbAdded.current = true;
                }
            }).catch(() => setError("Connection error or timeout while fetching merge provider details."));
            setIsNew(false);
        }
    }, [id, mergeProviderId]);

    useEffect(() => {
        return () => {
            if (breadcrumbAdded.current) {
                popItem();
                popItem();
                breadcrumbAdded.current = false;
            }
        };
    }, []);

    async function onDelete() {
        if (!mergeProvider) return;
        try {
            if (await turSNSiteMergeService.delete(mergeProvider)) {
                toast.success(`The merge provider was deleted`);
                navigate(`${ROUTES.SN_INSTANCE}/${id}/merge-providers`);
            } else {
                toast.error(`The merge provider was not deleted`);
            }
        } catch (error) {
            console.error("Delete error", error);
            toast.error(`The merge provider was not deleted`);
        }
        setOpen(false);
    }

    const name = mergeProvider?.providerFrom && mergeProvider?.providerTo
        ? `${mergeProvider.providerFrom} → ${mergeProvider.providerTo}`
        : "New Merge Provider";

    return (
        <LoadProvider checkIsNotUndefined={mergeProvider} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}/merge-providers`}>
            <SubPageHeader
                icon={IconGitMerge}
                name={name}
                feature="Merge Provider"
                description="Unify different sources contents."
                onDelete={isNew ? undefined : onDelete}
                open={open}
                setOpen={setOpen}
            />
            {mergeProvider && <SNSiteMergeForm snSiteId={id} value={mergeProvider} isNew={isNew} />}
        </LoadProvider>
    );
}
