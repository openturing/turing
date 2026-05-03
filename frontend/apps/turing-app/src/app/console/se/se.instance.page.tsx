import { useDeleteSeInstance } from "@/api/queries/se-instance.queries";
import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SubPage } from "@/components/sub.page";
import { type BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurSEInstance } from "@/models/se/se-instance.model.ts";
import { TurSEInstanceService } from "@/services/se/se.service";
import { TurFeaturesService } from "@/services/system/features.service";
import { IconDatabase, IconInfoCircle, IconSettings, IconZoomCode } from "@tabler/icons-react";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const turSEInstanceService = new TurSEInstanceService();
const turFeaturesService = new TurFeaturesService();

export default function SEInstancePage() {
  const { t } = useTranslation();
  const { id } = useParams() as { id: string };
  const [seInstance, setSeInstance] = useState<TurSEInstance>({} as TurSEInstance);
  const [isNew, setIsNew] = useState<boolean>(true);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [readOnly, setReadOnly] = useState<boolean>(false);
  const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>(
    id === "new" ? [{ label: t("se.newSearchEngine") }] : [{ label: "…" }]
  );
  const navigate = useNavigate();
  const urlBase = `${ROUTES.SE_INSTANCE}/${id}`;
  const deleteMutation = useDeleteSeInstance();

  const data = useMemo(() => {
    if (isNew) {
      return {
        navMain: [
          { title: t("se.nav.settings"), url: "/detail", icon: IconSettings },
        ],
      };
    }
    return {
      navMain: [
        { title: t("se.nav.settings"), url: "/detail", icon: IconSettings },
        { title: t("se.nav.cores"), url: "/cores", icon: IconDatabase },
        { title: t("se.nav.systemInfo"), url: "/system-info", icon: IconInfoCircle },
      ],
    };
  }, [isNew, t]);

  useEffect(() => {
    if (id === "new") {
      setSeInstance({} as TurSEInstance);
      setIsNew(true);
    } else {
      turSEInstanceService.get(id).then((instance) => {
        setSeInstance(instance);
        setBreadcrumb([{ label: instance.title, href: `${ROUTES.SE_INSTANCE}/${instance.id}` }]);
      }).catch(() => setError(t("common.connectionError", { resource: "SE instance" })));
      setIsNew(false);
    }
    turFeaturesService.getFeatures()
      .then((f) => setReadOnly(f.seInstanceReadOnly))
      .catch(() => setReadOnly(false));
  }, [id]);

  useSubPageBreadcrumb(breadcrumb);

  async function onDelete() {
    try {
      if (await deleteMutation.mutateAsync(seInstance)) {
        toast.success(t("se.deleted", { name: seInstance.title }));
        navigate(ROUTES.SE_INSTANCE);
      } else {
        toast.error(t("se.notDeleted", { name: seInstance.title }));
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("se.notDeleted", { name: seInstance.title }));
    }
    setOpen(false);
  }

  return (
    <LoadProvider checkIsNotUndefined={seInstance} error={error} tryAgainUrl={`${ROUTES.SE_INSTANCE}/${id}`}>
      <SubPage icon={IconZoomCode} feature={t("se.title")} name={seInstance.title}
        onDelete={readOnly ? undefined : onDelete} data={data} isNew={isNew} urlBase={urlBase} open={open} setOpen={setOpen} />
    </LoadProvider>
  );
}
