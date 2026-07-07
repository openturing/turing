import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteCustomSortForm } from "@/components/sn/custom-sort/sn.site.custom.sort.form";
import type { TurSNSiteCustomSort } from "@/models/sn/sn-site-custom-sort.model";
import { TurSNSiteCustomSortService } from "@/services/sn/sn.site.custom.sort.service";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const service = new TurSNSiteCustomSortService();

/** Bento SN custom-sort detail — T576. Reuses `SNSiteCustomSortForm` with the
 *  bento base route so save/cancel/delete stay inside the shell. */
export default function BentoSNCustomSortPage() {
  const { id, customSortId } = useParams() as { id: string; customSortId: string };
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [value, setValue] = useState<TurSNSiteCustomSort>();
  const [isNew, setIsNew] = useState(true);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const listRoute = `${ROUTES.BENTO_SN_INSTANCE}/${id}/custom-sort`;

  useEffect(() => {
    if (customSortId === "new") {
      setValue({ name: "", items: [] });
      setIsNew(true);
    } else {
      service.get(id, customSortId).then((s) => { setValue(s); setIsNew(false); })
        .catch(() => setError(t("common.connectionError", { resource: t("sn.customSort.title") })));
    }
  }, [id, customSortId, t]);

  async function onDelete() {
    if (!value?.id) return;
    try {
      if (await service.delete(id, value.id)) {
        toast.success(t("sn.customSort.deleted"));
        navigate(listRoute);
      } else {
        toast.error(t("sn.customSort.notDeleted"));
      }
    } catch (err) {
      console.error("Delete error", err);
      toast.error(t("sn.customSort.notDeleted"));
    }
    setOpen(false);
  }

  return (
    <LoadProvider checkIsNotUndefined={value} error={error} tryAgainUrl={`${listRoute}/${customSortId}`}>
      {value && (
        <SNSiteCustomSortForm
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
