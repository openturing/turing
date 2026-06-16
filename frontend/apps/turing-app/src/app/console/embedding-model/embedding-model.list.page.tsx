import { useEmbeddingModels } from "@/api/queries/embedding-model.queries";
import { useFeatures } from "@/api/queries/features.queries";
import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import { IconCube } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function EmbeddingModelListPage() {
  const { t } = useTranslation();
  const { data: models, isError } = useEmbeddingModels();
  const error = isError ? t("common.connectionError", { resource: "embedding models" }) : null;
  const { data: features } = useFeatures();
  const tenancyEnabled = features?.tenancyEnabled === true;
  // T372 — label shared GLOBAL pool rows (tenantId == null) when tenancy is on.
  const globalLabel = t("forms.common.global", { defaultValue: "Global" });

  const gridItemList = useGridAdapter(models, {
    name: "modelName",
    description: "description",
    url: (item) => `${ROUTES.EMBEDDING_MODEL_INSTANCE}/${item.id}`,
    icon: "icon",
    badge: (item) => {
      if (tenancyEnabled && item.tenantId == null) return globalLabel;
      return null;
    },
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
