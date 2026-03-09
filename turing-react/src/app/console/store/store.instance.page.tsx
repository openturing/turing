import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { StoreInstanceForm } from "@/components/store/store.instance.form";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import type { TurStoreInstance } from "@/models/store/store-instance.model.ts";
import { TurStoreInstanceService } from "@/services/store/store.service";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";

const turStoreInstanceService = new TurStoreInstanceService();

export default function StoreInstancePage() {
  const { id } = useParams() as { id: string };
  const [storeInstance, setStoreInstance] = useState<TurStoreInstance>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const { pushItem, popItem } = useBreadcrumb();
  useEffect(() => {
    let added = false;
    if (id === "new") {
      turStoreInstanceService.query().then(() => {
        setStoreInstance({} as TurStoreInstance);
        pushItem({ label: "New Embedding Store" });
        added = true;
      }).catch(() => setError("Connection error or timeout while fetching Embedding Store instances."));
      setIsNew(true);
    } else {
      turStoreInstanceService.get(id).then((storeInstance) => {
        setStoreInstance(storeInstance);
        pushItem({ label: storeInstance.title, href: `${ROUTES.STORE_INSTANCE}/${storeInstance.id}` });
        added = true;
      }).catch(() => setError("Connection error or timeout while fetching Embedding Store instance."));
      setIsNew(false);
    }
    return () => {
      if (added) popItem();
    };
  }, [id])
  return (
    <LoadProvider checkIsNotUndefined={storeInstance} error={error} tryAgainUrl={`${ROUTES.STORE_INSTANCE}/${id}`}>
      {storeInstance && <StoreInstanceForm value={storeInstance} isNew={isNew} />}
    </LoadProvider>
  )
}
