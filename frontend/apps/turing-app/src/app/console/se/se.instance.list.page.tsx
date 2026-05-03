import { useFeatures } from "@/api/queries/features.queries";
import { useSeInstances } from "@/api/queries/se-instance.queries";
import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import { IconZoomCode } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function SEInstanceListPage() {
  const { t } = useTranslation();
  const { data: seInstances, isError } = useSeInstances();
  const error = isError ? t("common.connectionError", { resource: "instances" }) : null;
  const { data: features } = useFeatures();
  const readOnly = features?.seInstanceReadOnly ?? false;

  const gridItemList = useGridAdapter(seInstances, {
    name: "title",
    description: "description",
    url: (item) => `${ROUTES.SE_INSTANCE}/${item.id}`,
    icon: "icon",
  });

  return (
    <LoadProvider checkIsNotUndefined={seInstances} error={error} tryAgainUrl={`${ROUTES.SE_INSTANCE}`}>
      {gridItemList.length > 0 ? (
        <GridList gridItemList={gridItemList}>
          {!readOnly && (
            <GridList.NewButton to={`${ROUTES.SE_INSTANCE}/new`} label={t("se.title")} />
          )}
        </GridList>
      ) : readOnly ? (
        <BlankSlate
          icon={IconZoomCode}
          title={t("se.blankTitle")}
          description={t("se.blankDescription")}
          buttonText=""
        />
      ) : (
        <BlankSlate
          icon={IconZoomCode}
          title={t("se.blankTitle")}
          description={t("se.blankDescription")}
          buttonText={t("se.newInstance")}
          urlNew={`${ROUTES.SE_INSTANCE}/new`}
        />
      )}
    </LoadProvider>
  );
}
