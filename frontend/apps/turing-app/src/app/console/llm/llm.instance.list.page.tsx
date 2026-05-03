import { useLlmInstances } from "@/api/queries/llm-instance.queries";
import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import { IconCpu2 } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function LLMInstanceListPage() {
  const { t } = useTranslation();
  const { data: llmInstances, isError } = useLlmInstances();
  const error = isError ? t("common.connectionError", { resource: "instances" }) : null;
  const gridItemList = useGridAdapter(llmInstances, {
    name: "title",
    description: "description",
    url: (item) => `${ROUTES.LLM_INSTANCE}/${item.id}`,
    icon: "icon",
  });
  return (
    <LoadProvider checkIsNotUndefined={llmInstances} error={error} tryAgainUrl={`${ROUTES.LLM_INSTANCE}`}>
      {gridItemList.length > 0 ? (
        <GridList gridItemList={gridItemList}>
          <GridList.NewButton to={`${ROUTES.LLM_INSTANCE}/new`} label={t("llm.title")} />
        </GridList>
      ) : (
        <BlankSlate
          icon={IconCpu2}
          title={t("llm.blankTitle")}
          description={t("llm.blankDescription")}
          buttonText={t("llm.newInstance")}
          urlNew={`${ROUTES.LLM_INSTANCE}/new`} />
      )}
    </LoadProvider>
  )
}


