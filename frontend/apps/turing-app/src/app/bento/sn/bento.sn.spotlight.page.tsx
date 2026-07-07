import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteSpotlightForm } from "@/components/sn/spotlight/sn.site.spotlight.form";
import type { TurSNSiteSpotlight } from "@/models/sn/sn-site-spotlight.model";
import { TurSNSiteSpotlightService } from "@/services/sn/sn.site.spotlight.service";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const service = new TurSNSiteSpotlightService();

/** Bento SN spotlight detail — T576. */
export default function BentoSNSpotlightPage() {
  const { id, spotlightId } = useParams() as { id: string; spotlightId: string };
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [value, setValue] = useState<TurSNSiteSpotlight>();
  const [isNew, setIsNew] = useState(true);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const listRoute = `${ROUTES.BENTO_SN_INSTANCE}/${id}/spotlight`;

  useEffect(() => {
    if (spotlightId === "new") {
      service.getStructure(id).then((s) => { setValue(s); setIsNew(true); })
        .catch(() => setError(t("common.connectionError", { resource: t("sn.spotlight.title") })));
    } else {
      service.get(id, spotlightId).then((s) => { setValue(s); setIsNew(false); })
        .catch(() => setError(t("common.connectionError", { resource: t("sn.spotlight.title") })));
    }
  }, [id, spotlightId, t]);

  async function onDelete() {
    if (!value) return;
    try {
      if (await service.delete(value)) {
        toast.success(t("sn.spotlight.deleted"));
        navigate(listRoute);
      } else {
        toast.error(t("sn.spotlight.notDeleted"));
      }
    } catch (err) {
      console.error("Delete error", err);
      toast.error(t("sn.spotlight.notDeleted"));
    }
    setOpen(false);
  }

  return (
    <LoadProvider checkIsNotUndefined={value} error={error} tryAgainUrl={`${listRoute}/${spotlightId}`}>
      {value && (
        <SNSiteSpotlightForm
          snSiteId={id}
          value={value}
          isNew={isNew}
          onDelete={isNew ? undefined : onDelete}
          open={open}
          setOpen={setOpen}
          baseRoute={ROUTES.BENTO_SN_INSTANCE}
          chrome="bento"
        />
      )}
    </LoadProvider>
  );
}
