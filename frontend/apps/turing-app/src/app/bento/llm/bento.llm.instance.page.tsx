import { useDeleteLlmInstance, useLlmInstance, useUpdateLlmInstance } from "@/api/queries/llm-instance.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityShell } from "@/components/bento";
import { LoadProvider } from "@/components/loading-provider";
import type { TurLLMInstance } from "@/models/llm/llm-instance.model.ts";
import { IconCpu2 } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BENTO_LLM_FORM_ID, BentoLLMInstanceForm } from "./bento.llm.instance.form";

export default function BentoLLMInstancePage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const isNew = id === "new";

  const { data: llm, isError } = useLlmInstance(isNew ? undefined : id);
  const error = isError ? t("common.connectionError", { resource: t("llm.title") }) : null;

  const updateMutation = useUpdateLlmInstance();
  const deleteMutation = useDeleteLlmInstance();

  /**
   * Thin wrapper over the shared {@link BentoEntityShell}. The Shell owns
   * the hero identity (inline-edit title/description, icon picker, status
   * pill), the immediate-save-vs-staged-create modes, the scroll-linked
   * save-bar morph and the delete flow; here we only wire the LLM query
   * hooks and drop in the form as the render-prop child.
   */
  function shell(entity: TurLLMInstance, headlineFallback: string) {
    return (
      <BentoEntityShell
        entity={entity}
        isNew={isNew}
        headlineFallback={headlineFallback}
        eyebrow={t("llm.title")}
        listRoute={ROUTES.BENTO_LLM_INSTANCE}
        icon={IconCpu2}
        tone="indigo"
        formId={BENTO_LLM_FORM_ID}
        feature={t("llm.title")}
        hasStatus
        onUpdate={isNew ? undefined : (next) => updateMutation.mutateAsync(next)}
        onDelete={isNew ? undefined : () => deleteMutation.mutateAsync(entity)}
      >
        {({ staged, onStateChange }) => (
          <BentoLLMInstanceForm value={entity} isNew={isNew} staged={staged} onStateChange={onStateChange} />
        )}
      </BentoEntityShell>
    );
  }

  if (isNew) {
    return shell({} as TurLLMInstance, t("llm.newLanguageModel"));
  }

  return (
    <LoadProvider checkIsNotUndefined={llm} error={error} tryAgainUrl={`${ROUTES.BENTO_LLM_INSTANCE}/${id}`}>
      {llm && shell(llm, llm.title)}
    </LoadProvider>
  );
}
