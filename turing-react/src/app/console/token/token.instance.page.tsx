import { LoadProvider } from "@/components/loading-provider";
import { TokenInstanceForm } from "@/components/token/token.instance.form";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import type { TurTokenInstance } from "@/models/token/token-instance.model.ts";
import { TurTokenInstanceService } from "@/services/token/token.service";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";

const turTokenInstanceService = new TurTokenInstanceService();

export default function TokenInstancePage() {
  const { id } = useParams() as { id: string };
  const [tokenInstance, setTokenInstance] = useState<TurTokenInstance>({} as TurTokenInstance);
  const [isNew, setIsNew] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const { pushItem, popItem } = useBreadcrumb();
  useEffect(() => {
    let added = false;
    if (id === "new") {
      pushItem({ label: "New Token" });
      setIsNew(true);
      added = true;
    } else {
      turTokenInstanceService.get(id).then((tokenInstance) => {
        setTokenInstance(tokenInstance);
        pushItem({ label: tokenInstance.title, href: `/console/token/${tokenInstance.id}` });
        added = true;
      }).catch(() => setError("Connection error or timeout while fetching Token instance."));
      setIsNew(false);
    }
    return () => {
      if (added) popItem();
    };
  }, [id])

  return (
    <LoadProvider checkIsNotUndefined={tokenInstance} error={error} tryAgainUrl={`/console/token/${id}`}>
      {tokenInstance && <TokenInstanceForm value={tokenInstance} isNew={isNew} />}
    </LoadProvider>
  )
}
