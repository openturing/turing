import { GridList } from "@/components/grid.list";
import { useTranslation } from "react-i18next";

export default function LoggingInstanceListPage() {
  const { t } = useTranslation();

  const gridItemList = [{
    id: "1",
    name: t("logging.turingServer"),
    description: t("logging.turingServerDesc"),
    url: "/admin/logging/instance/server",
  }, {
    id: "2",
    name: t("logging.indexing"),
    description: t("logging.indexingDesc"),
    url: "/admin/logging/instance/indexing",
  }, {
    id: "3",
    name: t("logging.aem"),
    description: t("logging.aemDesc"),
    url: "/admin/logging/instance/aem",
  }];

  return <GridList gridItemList={gridItemList} />
}


