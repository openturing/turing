import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurLLMInstance } from "@/models/llm/llm-instance.model.ts";
import { TurLLMInstanceService } from "@/services/llm/llm.service";
import { IconCpu2 } from "@tabler/icons-react";
import { useEffect, useState } from "react";

const turLLMInstanceService = new TurLLMInstanceService();

export default function LLMInstanceListPage() {
  const [llmInstances, setLlmInstances] = useState<TurLLMInstance[]>();
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    turLLMInstanceService.query().then(setLlmInstances).catch(() => setError("Connection error or timeout while fetching instances."));
  }, []);
  const gridItemList = useGridAdapter(llmInstances, {
    name: "title",
    description: "description",
    url: (item) => `${ROUTES.LLM_INSTANCE}/${item.id}`
  });
  return (
    <LoadProvider checkIsNotUndefined={llmInstances} error={error} tryAgainUrl={`${ROUTES.LLM_INSTANCE}`}>
      {gridItemList.length > 0 ? (
        <GridList gridItemList={gridItemList} />
      ) : (
        <BlankSlate
          icon={IconCpu2}
          title="You don’t seem to have any language model instance."
          description="Create a new instance and use it in semantic navigation and chatbot."
          buttonText="New language model instance"
          urlNew={`${ROUTES.LLM_INSTANCE}/new`} />
      )}
    </LoadProvider>
  )
}


