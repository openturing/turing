import { ROUTES } from "@/app/routes.const";
import { EmbeddingModelForm } from "@/components/embedding/embedding-model.form";
import { LoadProvider } from "@/components/loading-provider";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
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
  const { pushItem, popItem } = useBreadcrumb();

  useEffect(() => {
    let added = false;
    if (id === "new") {
      turEmbeddingModelService.structure().then(() => {
        pushItem({ label: t("embeddingModel.newEmbeddingModel") });
        setModel({} as TurEmbeddingModel);
        added = true;
      }).catch(() => setError(t("common.connectionError", { resource: t("embeddingModel.title") })));
    } else {
      turEmbeddingModelService.get(id).then((result) => {
        setModel(result);
        pushItem({ label: result.modelName, href: `${ROUTES.EMBEDDING_MODEL_INSTANCE}/${result.id}` });
        added = true;
      }).catch(() => setError(t("common.connectionError", { resource: t("embeddingModel.title") })));
      setIsNew(false);
    }
    return () => {
      if (added) popItem();
    };
  }, [id]);

  return (
    <LoadProvider checkIsNotUndefined={model} error={error} tryAgainUrl={`${ROUTES.EMBEDDING_MODEL_INSTANCE}/${id}`}>
      {model && <EmbeddingModelForm value={model} isNew={isNew} />}
    </LoadProvider>
  )
}
