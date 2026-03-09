import { ROUTES } from "@/app/routes.const";
import { LLMInstanceForm } from "@/components/llm/llm.instance.form";
import { LoadProvider } from "@/components/loading-provider";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import type { TurLLMInstance } from "@/models/llm/llm-instance.model.ts";
import { TurLLMInstanceService } from "@/services/llm/llm.service";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";

const turLLMInstanceService = new TurLLMInstanceService();

export default function LLMInstancePage() {
  const { id } = useParams() as { id: string };
  const [llmInstance, setLlmInstance] = useState<TurLLMInstance>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const { pushItem, popItem } = useBreadcrumb();
  useEffect(() => {
    let added = false;
    if (id === "new") {
      turLLMInstanceService.query().then(() => {
        pushItem({ label: "New Language Model" });
        setLlmInstance({} as TurLLMInstance);
        added = true;
      }).catch(() => setError("Connection error or timeout while fetching Language Model service."));
    } else {
      turLLMInstanceService.get(id).then((llmInstance) => {
        setLlmInstance(llmInstance);
        pushItem({ label: llmInstance.title, href: `${ROUTES.LLM_INSTANCE}/${llmInstance.id}` });
        added = true;
      }).catch(() => setError("Connection error or timeout while fetching Language Model instance."));
      setIsNew(false);
    }
    return () => {
      if (added) popItem();
    };
  }, [id])
  return (
    <LoadProvider checkIsNotUndefined={llmInstance} error={error} tryAgainUrl={`${ROUTES.LLM_INSTANCE}/${id}`}>
      {llmInstance && <LLMInstanceForm value={llmInstance} isNew={isNew} />}
    </LoadProvider>
  )
}
