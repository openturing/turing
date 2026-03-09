import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SEInstanceForm } from "@/components/se/se.instance.form";
import { SubPageHeader } from "@/components/sub.page.header";
import type { TurSEInstance } from "@/models/se/se-instance.model.ts";
import { TurSEInstanceService } from "@/services/se/se.service";
import { IconSettings } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";

const turSEInstanceService = new TurSEInstanceService();

export default function SEInstanceDetailPage() {
  const { id } = useParams() as { id: string };
  const [seInstance, setSeInstance] = useState<TurSEInstance>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (id === "new") {
      setSeInstance({} as TurSEInstance);
    } else {
      turSEInstanceService.get(id)
        .then(setSeInstance)
        .catch(() => setError("Connection error or timeout while fetching SE instance."));
      setIsNew(false);
    }
  }, [id]);

  return (
    <LoadProvider checkIsNotUndefined={seInstance} error={error} tryAgainUrl={`${ROUTES.SE_INSTANCE}/${id}/detail`}>
      <SubPageHeader icon={IconSettings} name="Settings" feature="Settings"
        description="Configure the Search Engine instance." />
      {seInstance && <SEInstanceForm value={seInstance} isNew={isNew} />}
    </LoadProvider>
  );
}
