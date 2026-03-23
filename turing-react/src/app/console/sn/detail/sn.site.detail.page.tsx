import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteForm } from "@/components/sn/sn.site.form";
import { SubPageHeader } from "@/components/sub.page.header";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurSNSite } from "@/models/sn/sn-site.model.ts";
import { TurSNSiteService } from "@/services/sn/sn.service";
import { IconSettings } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const turSNSiteService = new TurSNSiteService();

export default function SNSiteDetailPage() {
  const { id } = useParams() as { id: string };
  const [snSite, setSnSite] = useState<TurSNSite>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const { t } = useTranslation();
  useSubPageBreadcrumb(t("sn.settings.title"));

  useEffect(() => {
    if (id === "new") {
      turSNSiteService.query().then(() => {
        setSnSite({} as TurSNSite);
      }).catch(() => setError("Connection error or timeout while fetching site service."));
    } else {
      turSNSiteService.get(id).then(setSnSite).catch(() => setError("Connection error or timeout while fetching site details."));
      setIsNew(false);
    }
  }, [id]);
  return (
    <LoadProvider checkIsNotUndefined={snSite} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}/settings`}>
      <SubPageHeader icon={IconSettings} name={t("sn.settings.title")} feature={t("sn.settings.title")} description={t("sn.settings.description")} />
      {snSite && <SNSiteForm value={snSite} isNew={isNew} />}
    </LoadProvider>
  )
}
