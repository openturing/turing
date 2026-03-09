import { GridList } from "@/components/grid.list";

const gridItemList = [{
  id: "1",
  name: "Turing ES Server",
  description: "Logging server instance",
  url: "/admin/logging/instance/server",
}, {
  id: "2",
  name: "Indexing",
  description: "Indexing service instance",
  url: "/admin/logging/instance/indexing",
}, {
  id: "3",
  name: "AEM",
  description: "AEM service instance",
  url: "/admin/logging/instance/aem",
}];
export default function LoggingInstanceListPage() {

  return <GridList gridItemList={gridItemList} />
}


