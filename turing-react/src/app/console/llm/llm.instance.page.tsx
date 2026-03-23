import { ROUTES } from "@/app/routes.const";
import { LLMInstanceForm } from "@/components/llm/llm.instance.form";
import { LoadProvider } from "@/components/loading-provider";
import type { BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurLLMInstance } from "@/models/llm/llm-instance.model.ts";
import { TurLLMInstanceService } from "@/services/llm/llm.service";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const turLLMInstanceService = new TurLLMInstanceService();

export default function LLMInstancePage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const [llmInstance, setLlmInstance] = useState<TurLLMInstance>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[] | undefined>(
    id === "new" ? [{ label: t("llm.newLanguageModel") }] : undefined
  );
  useSubPageBreadcrumb(breadcrumb);

  useEffect(() => {
    if (id === "new") {
      turLLMInstanceService.query().then(() => {
        setLlmInstance({} as TurLLMInstance);
      }).catch(() => setError(t("common.connectionError", { resource: t("llm.title") })));
    } else {
      turLLMInstanceService.get(id).then((llmInstance) => {
        setLlmInstance(llmInstance);
        setBreadcrumb([{ label: llmInstance.title, href: `${ROUTES.LLM_INSTANCE}/${llmInstance.id}` }]);
      }).catch(() => setError(t("common.connectionError", { resource: t("llm.title") })));
      setIsNew(false);
    }
  }, [id])
  return (
    <LoadProvider checkIsNotUndefined={llmInstance} error={error} tryAgainUrl={`${ROUTES.LLM_INSTANCE}/${id}`}>
      {llmInstance && <LLMInstanceForm value={llmInstance} isNew={isNew} />}
    </LoadProvider>
  )
}
