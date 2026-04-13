import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SEInstanceForm } from "@/components/se/se.instance.form";
import type { TurSEInstance } from "@/models/se/se-instance.model.ts";
import { TurSEInstanceService } from "@/services/se/se.service";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const turSEInstanceService = new TurSEInstanceService();

export default function SEInstanceDetailPage() {
  const { t } = useTranslation();
  const { id } = useParams() as { id: string };
  const [seInstance, setSeInstance] = useState<TurSEInstance>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  useSubPageBreadcrumb(t("se.detail.title"));

  useEffect(() => {
    if (id === "new") {
      setSeInstance({} as TurSEInstance);
    } else {
      turSEInstanceService.get(id)
        .then(setSeInstance)
        .catch(() => setError(t("common.connectionError", { resource: "SE instance" })));
      setIsNew(false);
    }
  }, [id]);

  return (
    <LoadProvider checkIsNotUndefined={seInstance} error={error} tryAgainUrl={`${ROUTES.SE_INSTANCE}/${id}/detail`}>
      {seInstance && <SEInstanceForm value={seInstance} isNew={isNew} />}
    </LoadProvider>
  );
}
