import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurEmbeddingModel } from "@/models/embedding/embedding-model.model.ts";
import { TurEmbeddingModelService } from "@/services/embedding/embedding-model.service";
import { IconCube } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

const turEmbeddingModelService = new TurEmbeddingModelService();

export default function EmbeddingModelListPage() {
  const { t } = useTranslation();
  const [models, setModels] = useState<TurEmbeddingModel[]>();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    turEmbeddingModelService.query().then(setModels).catch(() => setError(t("common.connectionError", { resource: "embedding models" })));
  }, []);

  const gridItemList = useGridAdapter(models, {
    name: "modelName",
    description: "description",
    url: (item) => `${ROUTES.EMBEDDING_MODEL_INSTANCE}/${item.id}`,
    icon: "icon",
  });

  return (
    <LoadProvider checkIsNotUndefined={models} error={error} tryAgainUrl={`${ROUTES.EMBEDDING_MODEL_INSTANCE}`}>
      {gridItemList.length > 0 ? (
        <GridList gridItemList={gridItemList}>
          <GridList.NewButton to={`${ROUTES.EMBEDDING_MODEL_INSTANCE}/new`} label={t("embeddingModel.title")} />
        </GridList>
      ) : (
        <BlankSlate
          icon={IconCube}
          title={t("embeddingModel.blankTitle")}
          description={t("embeddingModel.blankDescription")}
          buttonText={t("embeddingModel.newInstance")}
          urlNew={`${ROUTES.EMBEDDING_MODEL_INSTANCE}/new`} />
      )}
    </LoadProvider>
  )
}
