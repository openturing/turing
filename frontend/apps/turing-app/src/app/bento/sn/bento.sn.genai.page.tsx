import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteGenAiForm } from "@/components/sn/genai/sn.site.genai.form";
import type { TurSNSite } from "@/models/sn/sn-site.model";
import { TurSNSiteService } from "@/services/sn/sn.service";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const service = new TurSNSiteService();

/** Bento SN GenAI — T576. Reuses `SNSiteGenAiForm` verbatim (saves in place,
 *  no navigation) with its own sticky header. */
export default function BentoSNGenAiPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const [snSite, setSnSite] = useState<TurSNSite>();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    service.get(id).then(setSnSite).catch(() => setError(t("common.connectionError", { resource: t("sn.genai.title") })));
  }, [id, t]);

  return (
    <LoadProvider checkIsNotUndefined={snSite} error={error} tryAgainUrl={`${ROUTES.BENTO_SN_INSTANCE}/${id}/ai`}>
      {snSite && <SNSiteGenAiForm snSite={snSite} baseRoute={ROUTES.BENTO_SN_INSTANCE} chrome="bento" />}
    </LoadProvider>
  );
}
