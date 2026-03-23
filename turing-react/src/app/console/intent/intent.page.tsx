import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SubPage } from "@/components/sub.page";
import type { BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurIntent } from "@/models/intent/intent.model";
import { TurIntentService } from "@/services/intent/intent.service";
import { IconBulb, IconSettings } from "@tabler/icons-react";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";

const turIntentService = new TurIntentService();

export default function IntentPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const [intent, setIntent] = useState<TurIntent>({} as TurIntent);
  const [isNew, setIsNew] = useState<boolean>(true);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[] | undefined>(
    id === "new" ? [{ label: t("intent.newInstance") }] : undefined
  );
  useSubPageBreadcrumb(breadcrumb);
  const navigate = useNavigate();
  const urlBase = `${ROUTES.INTENT_INSTANCE}/${id}`;

  const data = useMemo(() => ({
    navMain: [
      { title: t("intent.nav.settings"), url: "/settings", icon: IconSettings },
    ],
  }), [t]);

  useEffect(() => {
    if (id === "new") {
      setIntent({ actions: [] } as unknown as TurIntent);
      setIsNew(true);
    } else {
      turIntentService.get(id).then((i) => {
        setIntent(i);
        setBreadcrumb([{ label: i.title, href: `${ROUTES.INTENT_INSTANCE}/${i.id}` }]);
      }).catch(() => setError(t("common.connectionError", { resource: t("intent.title").toLowerCase() })));
      setIsNew(false);
    }
  }, [id]);

  async function onDelete() {
    try {
      if (await turIntentService.delete(intent)) {
        toast.success(t("intent.deleted", { name: intent.title }));
        navigate(ROUTES.INTENT_INSTANCE);
      } else {
        toast.error(t("intent.notDeleted", { name: intent.title }));
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("intent.notDeleted", { name: intent.title }));
    }
    setOpen(false);
  }

  return (
    <LoadProvider checkIsNotUndefined={intent} error={error} tryAgainUrl={`${ROUTES.INTENT_INSTANCE}/${id}`}>
      <SubPage icon={IconBulb} feature={t("intent.title")} name={intent.title}
        onDelete={onDelete} data={data} isNew={isNew} urlBase={urlBase} open={open} setOpen={setOpen} />
    </LoadProvider>
  );
}
