import { useDeleteTokenInstance } from "@/api/queries/token-instance.queries";
import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { TokenInstanceForm } from "@/components/token/token.instance.form";
import type { TurTokenInstance } from "@/models/token/token-instance.model.ts";
import { TurTokenInstanceService } from "@/services/token/token.service";
import { type BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const turTokenInstanceService = new TurTokenInstanceService();

export default function TokenInstancePage() {
  const { id } = useParams() as { id: string };
  const [tokenInstance, setTokenInstance] = useState<TurTokenInstance>({} as TurTokenInstance);
  const [isNew, setIsNew] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>();
  const deleteMutation = useDeleteTokenInstance();

  useEffect(() => {
    if (id === "new") {
      setIsNew(true);
      setBreadcrumb([{ label: "API Token", href: `${ROUTES.ADMIN_TOKENS}` }, { label: "New" }]);
    } else {
      turTokenInstanceService.get(id).then((token) => {
        setTokenInstance(token);
        setBreadcrumb([{ label: "API Token", href: `${ROUTES.ADMIN_TOKENS}` }, { label: token.title || id }]);
      }).catch(() => setError("Connection error or timeout while fetching Token instance."));
      setIsNew(false);
    }
  }, [id]);

  useSubPageBreadcrumb(breadcrumb);

  function onDelete() {
    deleteMutation.mutateAsync(tokenInstance).then((success) => {
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
      {tokenInstance && <TokenInstanceForm value={tokenInstance} isNew={isNew} onDelete={isNew ? undefined : onDelete} open={open} setOpen={setOpen} />}
    </LoadProvider>
  )
}
