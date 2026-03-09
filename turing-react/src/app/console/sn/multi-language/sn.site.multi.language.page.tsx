import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteLocaleForm } from "@/components/sn/locales/sn.site.locale.form";
import { SubPageHeader } from "@/components/sub.page.header";
import type { TurSNSiteLocale } from "@/models/sn/sn-site-locale.model";
import { TurSNSiteLocaleService } from "@/services/sn/sn.site.locale.service";
import { IconLanguage } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";

const turSNSiteLocaleService = new TurSNSiteLocaleService();

export default function SNSiteMultiLanguagePage() {
  const navigate = useNavigate();
  const { id, localeId } = useParams() as { id: string, localeId: string };
  const [snLocale, setSnLocale] = useState<TurSNSiteLocale>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    if (localeId === "new") {
      turSNSiteLocaleService.query(id).then(() => setSnLocale({} as TurSNSiteLocale)).catch(() => setError("Connection error or timeout while fetching Multi Language data."));
    } else {
      turSNSiteLocaleService.get(id, localeId).then(setSnLocale).catch(() => setError("Connection error or timeout while fetching locale details."));
      setIsNew(false);
    }
  }, [id, localeId])
  async function onDelete() {
    if (!snLocale) return;
    try {
      if (await turSNSiteLocaleService.delete(snLocale)) {
        toast.success(`The ${snLocale.language} Language was deleted`);
        navigate(`${ROUTES.SN_INSTANCE}/${id}/locale`);
      } else {
        toast.error(`The ${snLocale.language} Language was not deleted`);
      }

    } catch (error) {
      console.error("Form submission error", error);
      toast.error(`The ${snLocale.language} Language was not deleted`);
    }
    setOpen(false);
  }
  return (
    <LoadProvider checkIsNotUndefined={snLocale} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}/locale`}>
      {isNew && <SubPageHeader icon={IconLanguage} name="Language" feature="Language" description="Define Multi Languages." />}
      {!isNew && snLocale && <SubPageHeader
        icon={IconLanguage}
        name={snLocale.language}
        feature="Language"
        description={snLocale.core}
        onDelete={onDelete}
        open={open}
        setOpen={setOpen} />}
      {snLocale && <SNSiteLocaleForm
        snSiteId={id}
        snLocale={snLocale}
        isNew={isNew}
      />}
    </LoadProvider>
  )
}
