import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SubPage } from "@/components/sub.page";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import type { TurIntent } from "@/models/intent/intent.model";
import { TurIntentService } from "@/services/intent/intent.service";
import { IconBulb, IconSettings } from "@tabler/icons-react";
import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";

const turIntentService = new TurIntentService();

export default function IntentPage() {
  const { id } = useParams() as { id: string };
  const [intent, setIntent] = useState<TurIntent>({} as TurIntent);
  const [isNew, setIsNew] = useState<boolean>(true);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { pushItem, popItem } = useBreadcrumb();
  const navigate = useNavigate();
  const urlBase = `${ROUTES.INTENT_INSTANCE}/${id}`;

  const data = useMemo(() => ({
    navMain: [
      { title: "Settings", url: "/settings", icon: IconSettings },
    ],
  }), []);

  useEffect(() => {
    let added = false;
    if (id === "new") {
      setIntent({ actions: [] } as unknown as TurIntent);
      setIsNew(true);
      pushItem({ label: "New Intent" });
      added = true;
    } else {
      turIntentService.get(id).then((i) => {
        setIntent(i);
        pushItem({ label: i.title, href: `${ROUTES.INTENT_INSTANCE}/${i.id}` });
        added = true;
      }).catch(() => setError("Connection error or timeout while fetching intent."));
      setIsNew(false);
    }
    return () => {
      if (added) popItem();
    };
  }, [id]);

  async function onDelete() {
    try {
      if (await turIntentService.delete(intent)) {
        toast.success(`The "${intent.title}" intent was deleted`);
        navigate(ROUTES.INTENT_INSTANCE);
      } else {
        toast.error(`The "${intent.title}" intent was not deleted`);
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error(`The "${intent.title}" intent was not deleted`);
    }
    setOpen(false);
  }

  return (
    <LoadProvider checkIsNotUndefined={intent} error={error} tryAgainUrl={`${ROUTES.INTENT_INSTANCE}/${id}`}>
      <SubPage icon={IconBulb} feature="Intent" name={intent.title}
        onDelete={onDelete} data={data} isNew={isNew} urlBase={urlBase} open={open} setOpen={setOpen} />
    </LoadProvider>
  );
}
