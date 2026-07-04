import { useDeleteEmbeddingModel, useEmbeddingModel, useUpdateEmbeddingModel } from "@/api/queries/embedding-model.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityShell } from "@/components/bento";
import { GlobalBadge, GlobalReadOnlyNotice } from "@/components/infra-global-notice";
import { LoadProvider } from "@/components/loading-provider";
import { useInfraReadOnly } from "@/hooks/use-infra-read-only";
import type { TurEmbeddingModel } from "@/models/embedding/embedding-model.model.ts";
import { IconCube } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BENTO_EMBEDDING_FORM_ID, BentoEmbeddingModelForm } from "./bento.embedding.form";

/**
 * Embedding Model uses `modelName` as its identity (no `title` field), so the
 * shared shell's `title` is aliased to `modelName` on the way in and mapped
 * back on the way out. See T552.
 */
type EmbeddingIdentityView = TurEmbeddingModel & { title: string };

const toView = (model: TurEmbeddingModel): EmbeddingIdentityView => ({ ...model, title: model.modelName ?? "" });

/** Strip the synthetic `title` alias and fold it back into `modelName`. */
const fromView = (view: EmbeddingIdentityView): TurEmbeddingModel => {
  const { title, ...rest } = view;
  return { ...rest, modelName: title ?? rest.modelName };
};

export default function BentoEmbeddingModelPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const isNew = id === "new";

  const { data: model, isError } = useEmbeddingModel(isNew ? undefined : id);
  const error = isError ? t("common.connectionError", { resource: t("embeddingModel.title") }) : null;

  const updateMutation = useUpdateEmbeddingModel();
  const deleteMutation = useDeleteEmbeddingModel();

  const { isGlobal, readOnly } = useInfraReadOnly(model?.tenantId);

  function shell(entity: TurEmbeddingModel, headlineFallback: string) {
    return (
      <BentoEntityShell<EmbeddingIdentityView>
        entity={toView(entity)}
        isNew={isNew}
        headlineFallback={headlineFallback}
        eyebrow={t("embeddingModel.title")}
        listRoute={ROUTES.BENTO_EMBEDDING_MODEL_INSTANCE}
        icon={IconCube}
        tone="violet"
        formId={BENTO_EMBEDDING_FORM_ID}
        feature={t("embeddingModel.title")}
        hasStatus
        readOnly={readOnly}
        badge={isGlobal ? <GlobalBadge /> : undefined}
        notice={readOnly ? <GlobalReadOnlyNotice /> : undefined}
        onUpdate={isNew || readOnly ? undefined : (next) => updateMutation.mutateAsync(fromView(next))}
        onDelete={isNew || readOnly ? undefined : () => deleteMutation.mutateAsync(entity)}
      >
        {({ staged, onStateChange }) => (
          <BentoEmbeddingModelForm value={entity} isNew={isNew} readOnly={readOnly} staged={staged} onStateChange={onStateChange} />
        )}
      </BentoEntityShell>
    );
  }

  if (isNew) {
    return shell({} as TurEmbeddingModel, t("embeddingModel.newEmbeddingModel"));
  }

  return (
    <LoadProvider checkIsNotUndefined={model} error={error} tryAgainUrl={`${ROUTES.BENTO_EMBEDDING_MODEL_INSTANCE}/${id}`}>
      {model && shell(model, model.modelName)}
    </LoadProvider>
  );
}
