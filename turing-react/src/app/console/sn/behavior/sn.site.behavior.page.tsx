import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteBehaviorForm } from "@/components/sn/sn.site.behavior.form";
import { SubPageHeader } from "@/components/sub.page.header";
import type { TurSNSite } from "@/models/sn/sn-site.model.ts";
import { TurSNSiteService } from "@/services/sn/sn.service";
import { IconScale } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";

const turSNSiteService = new TurSNSiteService();

export default function SNSiteBehaviorPage() {
  const { id } = useParams() as { id: string };
  const [snSite, setSnSite] = useState<TurSNSite>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    if (id !== "new") {
      turSNSiteService.get(id).then(setSnSite).catch(() => setError("Connection error or timeout while fetching SN site behavior data."));
      setIsNew(false);
    }
  }, [id])
  return (
    <LoadProvider checkIsNotUndefined={snSite} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}/behavior`}>
      <SubPageHeader icon={IconScale} name="Behavior" feature="Behavior" description="How the search will behave during the search." />
      {snSite && <SNSiteBehaviorForm value={snSite} isNew={isNew} />}
    </LoadProvider>
  )
}
