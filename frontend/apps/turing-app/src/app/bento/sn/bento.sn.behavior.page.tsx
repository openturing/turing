import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteBehaviorForm } from "@/components/sn/sn.site.behavior.form";
import type { TurSNSite } from "@/models/sn/sn-site.model.ts";
import { TurSNSiteService } from "@/services/sn/sn.service";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const service = new TurSNSiteService();

/** Bento SN behavior — T576. Reuses `SNSiteBehaviorForm` in `chrome="bento"`
 *  so it renders the BentoHero + frosted BentoFormSection tiles (not the
 *  console sticky header / SectionCard), with the bento base route so
 *  cancel/save stay inside the shell. */
export default function BentoSNBehaviorPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const [snSite, setSnSite] = useState<TurSNSite>();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    service.get(id).then(setSnSite).catch(() => setError(t("common.connectionError", { resource: t("sn.behavior.title") })));
  }, [id, t]);

  return (
    <LoadProvider checkIsNotUndefined={snSite} error={error} tryAgainUrl={`${ROUTES.BENTO_SN_INSTANCE}/${id}/behavior`}>
      {snSite && <SNSiteBehaviorForm value={snSite} isNew={false} baseRoute={ROUTES.BENTO_SN_INSTANCE} chrome="bento" />}
    </LoadProvider>
  );
}
