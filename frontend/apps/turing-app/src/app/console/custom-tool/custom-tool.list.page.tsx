import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurCustomTool } from "@/models/customtool/custom-tool.model.ts";
import { TurCustomToolService } from "@/services/customtool/custom-tool.service";
import { IconBraces } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

/**
 * @since 2026.2.5
 */
const turCustomToolService = new TurCustomToolService();

export default function CustomToolListPage() {
  const { t } = useTranslation();
  const [tools, setTools] = useState<TurCustomTool[]>();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    turCustomToolService.query().then(setTools).catch(() => setError(t("common.connectionError", { resource: "Custom Tools" })));
  }, []);

  const gridItemList = useGridAdapter(tools, {
    name: "title",
    description: "description",
    url: (item) => `${ROUTES.CUSTOM_TOOL_INSTANCE}/${item.id}`,
    icon: "icon",
  });

  return (
    <LoadProvider checkIsNotUndefined={tools} error={error} tryAgainUrl={`${ROUTES.CUSTOM_TOOL_INSTANCE}`}>
      {gridItemList.length > 0 ? (
        <GridList gridItemList={gridItemList}>
          <GridList.NewButton to={`${ROUTES.CUSTOM_TOOL_INSTANCE}/new`} label={t("customTool.title")} />
        </GridList>
      ) : (
        <BlankSlate
          icon={IconBraces}
          title={t("customTool.blankTitle")}
          description={t("customTool.blankDescription")}
          buttonText={t("customTool.newInstance")}
          urlNew={`${ROUTES.CUSTOM_TOOL_INSTANCE}/new`} />
      )}
    </LoadProvider>
  );
}
