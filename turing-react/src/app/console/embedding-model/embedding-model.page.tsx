import { ROUTES } from "@/app/routes.const";
import { EmbeddingModelForm } from "@/components/embedding/embedding-model.form";
import { LoadProvider } from "@/components/loading-provider";
import type { BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurEmbeddingModel } from "@/models/embedding/embedding-model.model.ts";
import { TurEmbeddingModelService } from "@/services/embedding/embedding-model.service";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const turEmbeddingModelService = new TurEmbeddingModelService();

export default function EmbeddingModelPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const [model, setModel] = useState<TurEmbeddingModel>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[] | undefined>(
    id === "new" ? [{ label: t("embeddingModel.newEmbeddingModel") }] : undefined
  );
  useSubPageBreadcrumb(breadcrumb);

  useEffect(() => {
    if (id === "new") {
      turEmbeddingModelService.structure().then(() => {
        setModel({} as TurEmbeddingModel);
      }).catch(() => setError(t("common.connectionError", { resource: t("embeddingModel.title") })));
    } else {
      turEmbeddingModelService.get(id).then((result) => {
        setModel(result);
        setBreadcrumb([{ label: result.modelName, href: `${ROUTES.EMBEDDING_MODEL_INSTANCE}/${result.id}` }]);
      }).catch(() => setError(t("common.connectionError", { resource: t("embeddingModel.title") })));
      setIsNew(false);
    }
  }, [id]);

  return (
    <LoadProvider checkIsNotUndefined={model} error={error} tryAgainUrl={`${ROUTES.EMBEDDING_MODEL_INSTANCE}/${id}`}>
      {model && <EmbeddingModelForm value={model} isNew={isNew} />}
    </LoadProvider>
  )
}
