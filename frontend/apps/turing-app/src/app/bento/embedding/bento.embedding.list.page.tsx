import { useEmbeddingModels } from "@/api/queries/embedding-model.queries";
import { useFeatures } from "@/api/queries/features.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityTile, BentoListPage } from "@/components/bento";
import { GlobalBadge } from "@/components/infra-global-notice";
import { IconCube } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function BentoEmbeddingModelListPage() {
  const { t } = useTranslation();
  const { data: models, isError } = useEmbeddingModels();
  const { data: features } = useFeatures();
  const error = isError ? t("common.connectionError", { resource: t("embeddingModel.title") }) : null;
  const tenancyEnabled = features?.tenancyEnabled === true;

  return (
    <BentoListPage
      items={models}
      error={error}
      tryAgainUrl={ROUTES.BENTO_EMBEDDING_MODEL_INSTANCE}
      backTo={ROUTES.BENTO_AREA_GENERATIVE_AI}
      backLabel={t("home.sections.generativeAi.label")}
      heroIcon={IconCube}
      tone="violet"
      title={t("embeddingModel.title")}
      subtitle={t("embeddingModel.blankDescription")}
      newRoute={`${ROUTES.BENTO_EMBEDDING_MODEL_INSTANCE}/new`}
      newLabel={t("embeddingModel.newEmbeddingModel")}
      itemKey={(model) => model.id}
      emptyTitle={t("embeddingModel.blankTitle")}
      emptyDescription={t("embeddingModel.blankDescription")}
      listId="embeddingModel"
      renderTile={(model, emphasis) => (
        <BentoEntityTile
          to={`${ROUTES.BENTO_EMBEDDING_MODEL_INSTANCE}/${model.id}`}
          emphasis={emphasis}
          defaultIcon={IconCube}
          icon={model.icon}
          tone="violet"
          title={model.modelName}
          description={model.description}
          hasStatus
          enabled={model.enabled}
          meta={
            <>
              {model.providerType && (
                <span className="rounded-full border border-border/60 bg-card/40 px-2 py-0.5 backdrop-blur">
                  {model.turLLMInstance?.title ?? model.providerType}
                </span>
              )}
              {tenancyEnabled && model.tenantId == null && <GlobalBadge />}
            </>
          }
        />
      )}
    />
  );
}
