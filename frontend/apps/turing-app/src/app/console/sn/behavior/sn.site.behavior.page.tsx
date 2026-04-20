import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteBehaviorForm } from "@/components/sn/sn.site.behavior.form";
import type { TurSNSite } from "@/models/sn/sn-site.model.ts";
import { TurSNSiteService } from "@/services/sn/sn.service";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const turSNSiteService = new TurSNSiteService();

export default function SNSiteBehaviorPage() {
  const { id } = useParams() as { id: string };
  const [snSite, setSnSite] = useState<TurSNSite>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const { t } = useTranslation();
  useSubPageBreadcrumb(t("sn.behavior.title"));

  useEffect(() => {
    if (id !== "new") {
      turSNSiteService.get(id).then(setSnSite).catch(() => setError("Connection error or timeout while fetching SN site behavior data."));
      setIsNew(false);
    }
  }, [id]);
  return (
    <LoadProvider checkIsNotUndefined={snSite} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}/behavior`}>
      {snSite && <SNSiteBehaviorForm value={snSite} isNew={isNew} />}
    </LoadProvider>
  )
}
