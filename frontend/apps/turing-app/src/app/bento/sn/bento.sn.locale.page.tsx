import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteLocaleForm } from "@/components/sn/locales/sn.site.locale.form";
import type { TurSNSiteLocale } from "@/models/sn/sn-site-locale.model";
import { TurSNSiteLocaleService } from "@/services/sn/sn.site.locale.service";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const service = new TurSNSiteLocaleService();

/** Bento SN locale detail — T576. */
export default function BentoSNLocalePage() {
  const { id, localeId } = useParams() as { id: string; localeId: string };
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [snLocale, setSnLocale] = useState<TurSNSiteLocale>();
  const [isNew, setIsNew] = useState(true);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const listRoute = `${ROUTES.BENTO_SN_INSTANCE}/${id}/locale`;

  useEffect(() => {
    if (localeId === "new") {
      service.query(id).then(() => setSnLocale({} as TurSNSiteLocale))
        .catch(() => setError(t("common.connectionError", { resource: t("sn.multiLanguage.title") })));
      setIsNew(true);
    } else {
      service.get(id, localeId).then((l) => { setSnLocale(l); setIsNew(false); })
        .catch(() => setError(t("common.connectionError", { resource: t("sn.multiLanguage.title") })));
    }
  }, [id, localeId, t]);

  async function onDelete() {
    if (!snLocale) return;
    try {
      if (await service.delete(snLocale)) {
        toast.success(t("sn.multiLanguage.deleted", { name: snLocale.language }));
        navigate(listRoute);
      } else {
        toast.error(t("sn.multiLanguage.notDeleted", { name: snLocale.language }));
      }
    } catch (err) {
      console.error("Delete error", err);
      toast.error(t("sn.multiLanguage.notDeleted", { name: snLocale.language }));
    }
    setOpen(false);
  }

  return (
    <LoadProvider checkIsNotUndefined={snLocale} error={error} tryAgainUrl={`${listRoute}/${localeId}`}>
      {snLocale && (
        <SNSiteLocaleForm
          snSiteId={id}
          snLocale={snLocale}
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
