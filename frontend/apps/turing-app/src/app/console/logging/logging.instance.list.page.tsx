import { ROUTES } from "@/app/routes.const";
import { GridList } from "@/components/grid.list";
import { useTranslation } from "react-i18next";

export default function LoggingInstanceListPage() {
  const { t } = useTranslation();

  const gridItemList = [{
    id: "1",
    name: t("logging.turingServer"),
    description: t("logging.turingServerDesc"),
    url: `${ROUTES.LOGGING_INSTANCE}/server`,
  }, {
    id: "2",
    name: t("logging.indexing"),
    description: t("logging.indexingDesc"),
    url: `${ROUTES.LOGGING_INSTANCE}/indexing`,
  }, {
    id: "3",
    name: t("logging.aem"),
    description: t("logging.aemDesc"),
    url: `${ROUTES.LOGGING_INSTANCE}/aem`,
  }];

  return <GridList gridItemList={gridItemList} />
}


