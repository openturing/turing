import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteLocaleForm } from "@/components/sn/locales/sn.site.locale.form";
import type { TurSNSiteLocale } from "@/models/sn/sn-site-locale.model";
import { TurSNSiteLocaleService } from "@/services/sn/sn.site.locale.service";
import { type BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const turSNSiteLocaleService = new TurSNSiteLocaleService();

export default function SNSiteMultiLanguagePage() {
  const navigate = useNavigate();
  const { id, localeId } = useParams() as { id: string, localeId: string };
  const [snLocale, setSnLocale] = useState<TurSNSiteLocale>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>();
  const { t } = useTranslation();

  useEffect(() => {
    if (localeId === "new") {
      turSNSiteLocaleService.query(id).then(() => setSnLocale({} as TurSNSiteLocale)).catch(() => setError("Connection error or timeout while fetching Multi Language data."));
      setBreadcrumb([{ label: t("sn.multiLanguage.title"), href: `${ROUTES.SN_INSTANCE}/${id}/locale` }, { label: t("common.new") }]);
    } else {
      turSNSiteLocaleService.get(id, localeId).then((locale) => {
        setSnLocale(locale);
        setBreadcrumb([{ label: t("sn.multiLanguage.title"), href: `${ROUTES.SN_INSTANCE}/${id}/locale` }, { label: locale.language || localeId }]);
      }).catch(() => setError("Connection error or timeout while fetching locale details."));
      setIsNew(false);
    }
  }, [id, localeId]);

  useSubPageBreadcrumb(breadcrumb);
  async function onDelete() {
    if (!snLocale) return;
    try {
      if (await turSNSiteLocaleService.delete(snLocale)) {
        toast.success(t("sn.multiLanguage.deleted", { name: snLocale.language }));
        navigate(`${ROUTES.SN_INSTANCE}/${id}/locale`);
      } else {
        toast.error(t("sn.multiLanguage.notDeleted", { name: snLocale.language }));
      }

    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("sn.multiLanguage.notDeleted", { name: snLocale.language }));
    }
    setOpen(false);
  }
  return (
    <LoadProvider checkIsNotUndefined={snLocale} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}/locale`}>
      {snLocale && <SNSiteLocaleForm
        snSiteId={id}
        snLocale={snLocale}
        isNew={isNew}
        onDelete={isNew ? undefined : onDelete}
        open={open}
        setOpen={setOpen}
      />}
    </LoadProvider>
  )
}
