import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { TokenInstanceForm } from "@/components/token/token.instance.form";
import type { TurTokenInstance } from "@/models/token/token-instance.model.ts";
import { TurTokenInstanceService } from "@/services/token/token.service";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import { IconCode } from "@tabler/icons-react";
import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";

const turTokenInstanceService = new TurTokenInstanceService();

export default function TokenInstancePage() {
  const { id } = useParams() as { id: string };
  const [tokenInstance, setTokenInstance] = useState<TurTokenInstance>({} as TurTokenInstance);
  const [isNew, setIsNew] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const { pushItem, popItem } = useBreadcrumb();
  const breadcrumbAdded = useRef(false);

  useEffect(() => {
    if (id === "new") {
      setIsNew(true);
      if (!breadcrumbAdded.current) {
        pushItem({ label: "API Token", href: `${ROUTES.ADMIN_TOKENS}` });
        pushItem({ label: "New" });
        breadcrumbAdded.current = true;
      }
    } else {
      turTokenInstanceService.get(id).then((token) => {
        setTokenInstance(token);
        if (!breadcrumbAdded.current) {
          pushItem({ label: "API Token", href: `${ROUTES.ADMIN_TOKENS}` });
          pushItem({ label: token.title || id });
          breadcrumbAdded.current = true;
        }
      }).catch(() => setError("Connection error or timeout while fetching Token instance."));
      setIsNew(false);
    }
  }, [id]);

  useEffect(() => {
    return () => {
      if (breadcrumbAdded.current) {
        popItem();
        popItem();
        breadcrumbAdded.current = false;
      }
    };
  }, []);

  function onDelete() {
    turTokenInstanceService.delete(tokenInstance).then((success) => {
      if (success) {
        toast.success(`The ${tokenInstance.title} API Token was deleted`);
        navigate(ROUTES.ADMIN_TOKENS);
      } else {
        toast.error(`The ${tokenInstance.title} API Token was not deleted`);
      }
    }).catch((error) => {
      console.error("Delete error", error);
      toast.error(`The ${tokenInstance.title} API Token was not deleted`);
    }).finally(() => setOpen(false));
  }

  return (
    <LoadProvider checkIsNotUndefined={tokenInstance} error={error} tryAgainUrl={`${ROUTES.ADMIN_TOKENS}/${id}`}>
      <SubPageHeader
        icon={IconCode}
        name={tokenInstance.title ?? "API Token"}
        feature="API Token"
        description={isNew ? "Create a new API token." : "Manage API token details and credentials."}
        {...(isNew ? {} : { onDelete, open, setOpen })}
      />
      {tokenInstance && <TokenInstanceForm value={tokenInstance} isNew={isNew} />}
    </LoadProvider>
  )
}
