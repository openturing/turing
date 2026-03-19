import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SubPage } from "@/components/sub.page";
import { type BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurSEInstance } from "@/models/se/se-instance.model.ts";
import { TurSEInstanceService } from "@/services/se/se.service";
import { IconDatabase, IconInfoCircle, IconSettings, IconZoomCode } from "@tabler/icons-react";
import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";

const turSEInstanceService = new TurSEInstanceService();

export default function SEInstancePage() {
  const { id } = useParams() as { id: string };
  const [seInstance, setSeInstance] = useState<TurSEInstance>({} as TurSEInstance);
  const [isNew, setIsNew] = useState<boolean>(true);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>();
  const navigate = useNavigate();
  const urlBase = `${ROUTES.SE_INSTANCE}/${id}`;

  const data = useMemo(() => {
    if (isNew) {
      return {
        navMain: [
          { title: "Settings", url: "/detail", icon: IconSettings },
        ],
      };
    }
    return {
      navMain: [
        { title: "Settings", url: "/detail", icon: IconSettings },
        { title: "Cores", url: "/cores", icon: IconDatabase },
        { title: "System Information", url: "/system-info", icon: IconInfoCircle },
      ],
    };
  }, [isNew]);

  useEffect(() => {
    if (id === "new") {
      setSeInstance({} as TurSEInstance);
      setIsNew(true);
      setBreadcrumb([{ label: "New Search Engine" }]);
    } else {
      turSEInstanceService.get(id).then((instance) => {
        setSeInstance(instance);
        setBreadcrumb([{ label: instance.title, href: `${ROUTES.SE_INSTANCE}/${instance.id}` }]);
      }).catch(() => setError("Connection error or timeout while fetching SE instance."));
      setIsNew(false);
    }
  }, [id]);

  useSubPageBreadcrumb(breadcrumb);

  async function onDelete() {
    try {
      if (await turSEInstanceService.delete(seInstance)) {
        toast.success(`The ${seInstance.title} Search Engine was deleted`);
        navigate(ROUTES.SE_INSTANCE);
      } else {
        toast.error(`The ${seInstance.title} Search Engine was not deleted`);
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error(`The ${seInstance.title} Search Engine was not deleted`);
    }
    setOpen(false);
  }

  return (
    <LoadProvider checkIsNotUndefined={seInstance} error={error} tryAgainUrl={`${ROUTES.SE_INSTANCE}/${id}`}>
      <SubPage icon={IconZoomCode} feature="Search Engine" name={seInstance.title}
        onDelete={onDelete} data={data} isNew={isNew} urlBase={urlBase} open={open} setOpen={setOpen} />
    </LoadProvider>
  );
}
