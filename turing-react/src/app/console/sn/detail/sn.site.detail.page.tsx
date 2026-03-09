import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteForm } from "@/components/sn/sn.site.form";
import { SubPageHeader } from "@/components/sub.page.header";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import type { TurSNSite } from "@/models/sn/sn-site.model.ts";
import { TurSNSiteService } from "@/services/sn/sn.service";
import { IconSettings } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";

const turSNSiteService = new TurSNSiteService();

export default function SNSiteDetailPage() {
  const { id } = useParams() as { id: string };
  const [snSite, setSnSite] = useState<TurSNSite>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const { pushItem, popItem } = useBreadcrumb();
  useEffect(() => {
    let added = false;
    if (id === "new") {
      turSNSiteService.query().then(() => {
        setSnSite({} as TurSNSite);
        pushItem({ label: "Settings", href: `${ROUTES.SN_INSTANCE}/${id}/settings` });
        added = true;
      }).catch(() => setError("Connection error or timeout while fetching site service."));
    } else {
      turSNSiteService.get(id).then(setSnSite).catch(() => setError("Connection error or timeout while fetching site details."));
      setIsNew(false);
    }
    return () => {
      if (added) popItem();
    };
  }, [id])
  return (
    <LoadProvider checkIsNotUndefined={snSite} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}/settings`}>
      <SubPageHeader icon={IconSettings} name="Settings" feature="Settings" description="Configure the Semantic Navigation Site." />
      {snSite && <SNSiteForm value={snSite} isNew={isNew} />}
    </LoadProvider>
  )
}
