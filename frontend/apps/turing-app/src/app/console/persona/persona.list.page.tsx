import { usePersonas } from "@/api/queries/persona.queries";
import { ROUTES } from "@/app/routes.const";
import { AiAuthoringTrigger } from "@/components/ai-authoring/ai-authoring-trigger";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import { IconUserCircle } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function PersonaListPage() {
  const { t } = useTranslation();
  const { data: personas, isError } = usePersonas();
  const error = isError ? t("common.connectionError", { resource: "personas" }) : null;
  const gridItemList = useGridAdapter(personas, {
    name: "name",
    description: "description",
    url: (item) => `${ROUTES.PERSONA_INSTANCE}/${item.id}`,
  });
  const aiChatUrl = `${ROUTES.PERSONA_INSTANCE}/new/ai-chat`;
  return (
    <LoadProvider checkIsNotUndefined={personas} error={error} tryAgainUrl={`${ROUTES.PERSONA_INSTANCE}`}>
      {gridItemList.length > 0 ? (
        <GridList gridItemList={gridItemList}>
          <GridList.NewButton to={`${ROUTES.PERSONA_INSTANCE}/new`} label={t("persona.title")} />
        </GridList>
      ) : (
        <BlankSlate
          icon={IconUserCircle}
          title={t("persona.blankTitle")}
          description={t("persona.blankDescription")}
          buttonText={t("persona.newInstance")}
          urlNew={`${ROUTES.PERSONA_INSTANCE}/new`} />
      )}
      <AiAuthoringTrigger to={aiChatUrl} />
    </LoadProvider>
  )
}
