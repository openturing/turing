import { useRoutines } from "@/api/queries/routine.queries";
import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import { IconClock } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

/**
 * T48 admin — list of configured routines. Empty state surfaces a CTA to
 * create the first routine; otherwise renders the grid of routine cards
 * with click-through to the editor.
 *
 * @since 2026.3.1
 */
export default function RoutineListPage() {
  const { t } = useTranslation();
  const { data: routines, isError } = useRoutines();
  const error = isError ? t("common.connectionError", { resource: t("routine.title") }) : null;

  const gridItemList = useGridAdapter(routines, {
    name: "name",
    description: (item) =>
      item.description ?? (item.kind === "GROOVY" ? "Groovy script" : item.nativeToolName ?? "—"),
    url: (item) => `${ROUTES.ROUTINE_INSTANCE}/${item.id}`,
  });

  return (
    <LoadProvider checkIsNotUndefined={routines} error={error} tryAgainUrl={ROUTES.ROUTINE_INSTANCE}>
      {gridItemList.length > 0 ? (
        <GridList gridItemList={gridItemList}>
          <GridList.NewButton to={`${ROUTES.ROUTINE_INSTANCE}/new`} label={t("routine.title")} />
        </GridList>
      ) : (
        <BlankSlate
          icon={IconClock}
          title={t("routine.blankTitle")}
          description={t("routine.blankDescription")}
          buttonText={t("routine.newInstance")}
          urlNew={`${ROUTES.ROUTINE_INSTANCE}/new`}
        />
      )}
    </LoadProvider>
  );
}
