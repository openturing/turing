import { useTokenInstances } from "@/api/queries/token-instance.queries";
import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconCode } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function TokenInstanceListPage() {
  const { t } = useTranslation();
  const { data: tokenInstances, isError } = useTokenInstances();
  const error = isError ? t("common.connectionError", { resource: "api tokens" }) : null;
  useSubPageBreadcrumb(t("apiToken.title"));
  const gridItemList = useGridAdapter(tokenInstances, {
    name: "title",
    description: "description",
    url: (item) => `${ROUTES.ADMIN_TOKENS}/${item.id}`
  });
  return (
    <LoadProvider checkIsNotUndefined={tokenInstances} error={error} tryAgainUrl={ROUTES.ADMIN_TOKENS}>
      <SubPageHeader
        icon={IconCode}
        feature={t("apiToken.title")}
        name={t("apiToken.title")}
        description={t("apiToken.description")}
      />
      {gridItemList.length > 0 ? (
        <GridList gridItemList={gridItemList}>
          <GridList.NewButton to={`${ROUTES.ADMIN_TOKENS}/new`} label={t("apiToken.title")} />
        </GridList>
      ) : (
        <BlankSlate
          icon={IconCode}
          title={t("apiToken.blankTitle")}
          description={t("apiToken.blankDescription")}
          buttonText={t("apiToken.newInstance")}
          urlNew={`${ROUTES.ADMIN_TOKENS}/new`} />
      )}
    </LoadProvider>
  )
}


