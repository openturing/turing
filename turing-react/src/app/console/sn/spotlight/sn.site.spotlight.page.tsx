import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteSpotlightForm } from "@/components/sn/spotlight/sn.site.spotlight.form";
import { SubPageHeader } from "@/components/sub.page.header";
import type { TurSNSiteSpotlight } from "@/models/sn/sn-site-spotlight.model";
import { TurSNSiteSpotlightService } from "@/services/sn/sn.site.spotlight.service";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import { IconSpeakerphone } from "@tabler/icons-react";
import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";

const turSNSiteSpotlightService = new TurSNSiteSpotlightService();

export default function SNSiteSpotlightPage() {
    const navigate = useNavigate();
    const { id, spotlightId } = useParams() as { id: string; spotlightId: string };
    const [spotlight, setSpotlight] = useState<TurSNSiteSpotlight>();
    const [isNew, setIsNew] = useState<boolean>(true);
    const [open, setOpen] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const { pushItem, popItem } = useBreadcrumb();
    const breadcrumbAdded = useRef(false);

    useEffect(() => {
        if (spotlightId === "new") {
            turSNSiteSpotlightService.getStructure(id).then(setSpotlight).catch(() => setError("Connection error or timeout while fetching spotlight structure."));
            if (!breadcrumbAdded.current) {
                pushItem({ label: "Spotlight", href: `${ROUTES.SN_INSTANCE}/${id}/spotlight` });
                pushItem({ label: "New" });
                breadcrumbAdded.current = true;
            }
        } else {
            turSNSiteSpotlightService.get(id, spotlightId).then((s) => {
                setSpotlight(s);
                if (!breadcrumbAdded.current) {
                    pushItem({ label: "Spotlight", href: `${ROUTES.SN_INSTANCE}/${id}/spotlight` });
                    pushItem({ label: s.name || spotlightId });
                    breadcrumbAdded.current = true;
                }
            }).catch(() => setError("Connection error or timeout while fetching spotlight details."));
            setIsNew(false);
        }
    }, [id, spotlightId]);

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
        if (!spotlight) return;
        try {
            if (await turSNSiteSpotlightService.delete(spotlight)) {
                toast.success("The spotlight was deleted");
                navigate(`${ROUTES.SN_INSTANCE}/${id}/spotlight`);
            } else {
                toast.error("The spotlight was not deleted");
            }
        } catch (error) {
            console.error("Delete error", error);
            toast.error("The spotlight was not deleted");
        }
        setOpen(false);
    }

    const name = spotlight?.name || "New Spotlight";

    return (
        <LoadProvider checkIsNotUndefined={spotlight} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}/spotlight`}>
            <SubPageHeader
                icon={IconSpeakerphone}
                name={name}
                feature="Spotlight"
                description="Define content that will be featured in the term-based search."
                onDelete={isNew ? undefined : onDelete}
                open={open}
                setOpen={setOpen}
            />
            {spotlight && <SNSiteSpotlightForm snSiteId={id} value={spotlight} isNew={isNew} />}
        </LoadProvider>
    );
}
