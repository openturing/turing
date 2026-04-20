import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteGenAiForm } from "@/components/sn/genai/sn.site.genai.form";
import type { TurSNSite } from "@/models/sn/sn-site.model";
import { TurSNSiteService } from "@/services/sn/sn.service";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const turSNSiteService = new TurSNSiteService();

export default function SNSiteGenAIPage() {
  const { id } = useParams() as { id: string };
  const [snSite, setSnSite] = useState<TurSNSite>();
  const [error, setError] = useState<string | null>(null);
  const { t } = useTranslation();
  useSubPageBreadcrumb(t("sn.genai.title"));

  useEffect(() => {
    turSNSiteService.get(id).then(setSnSite).catch(() => setError("Connection error or timeout while fetching site data."));
  }, [id]);

  return (
    <LoadProvider checkIsNotUndefined={snSite} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}/genai`}>
      {snSite && <SNSiteGenAiForm snSite={snSite} />}
    </LoadProvider>
  );
}
