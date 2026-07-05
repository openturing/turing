import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteMergeForm } from "@/components/sn/merge/sn.site.merge.form";
import type { TurSNSiteMerge } from "@/models/sn/sn-site-merge.model";
import { TurSNSiteMergeService } from "@/services/sn/sn.site.merge.service";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const service = new TurSNSiteMergeService();

/** Bento SN merge-providers detail — T576. */
export default function BentoSNMergeProvidersPage() {
  const { id, mergeProviderId } = useParams() as { id: string; mergeProviderId: string };
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [value, setValue] = useState<TurSNSiteMerge>();
  const [isNew, setIsNew] = useState(true);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const listRoute = `${ROUTES.BENTO_SN_INSTANCE}/${id}/merge-providers`;

  useEffect(() => {
    if (mergeProviderId === "new") {
      service.getStructure(id).then((s) => { setValue(s); setIsNew(true); })
        .catch(() => setError(t("common.connectionError", { resource: t("sn.mergeProviders.title") })));
    } else {
      service.get(id, mergeProviderId).then((m) => { setValue(m); setIsNew(false); })
        .catch(() => setError(t("common.connectionError", { resource: t("sn.mergeProviders.title") })));
    }
  }, [id, mergeProviderId, t]);

  async function onDelete() {
    if (!value) return;
    try {
      if (await service.delete(value)) {
        toast.success(t("sn.mergeProviders.deleted"));
        navigate(listRoute);
      } else {
        toast.error(t("sn.mergeProviders.notDeleted"));
      }
    } catch (err) {
      console.error("Delete error", err);
      toast.error(t("sn.mergeProviders.notDeleted"));
    }
    setOpen(false);
  }

  return (
    <LoadProvider checkIsNotUndefined={value} error={error} tryAgainUrl={`${listRoute}/${mergeProviderId}`}>
      {value && (
        <SNSiteMergeForm
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
