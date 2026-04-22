import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurSEInstance } from "@/models/se/se-instance.model.ts";
import { TurSEInstanceService } from "@/services/se/se.service";
import { TurFeaturesService } from "@/services/system/features.service";
import { IconZoomCode } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

const turSEInstanceService = new TurSEInstanceService();
const turFeaturesService = new TurFeaturesService();

export default function SEInstanceListPage() {
  const { t } = useTranslation();
  const [seInstances, setSeInstances] = useState<TurSEInstance[]>();
  const [error, setError] = useState<string | null>(null);
  const [readOnly, setReadOnly] = useState<boolean>(false);

  useEffect(() => {
    turSEInstanceService
      .query()
      .then(setSeInstances)
      .catch(() => setError(t("common.connectionError", { resource: "instances" })));
    turFeaturesService.getFeatures()
      .then((f) => setReadOnly(f.seInstanceReadOnly))
      .catch(() => setReadOnly(false));
  }, []);

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
