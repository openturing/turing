import { useRoutines } from "@/api/queries/routine.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityTile, BentoListPage } from "@/components/bento";
import { IconClock } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function BentoRoutineListPage() {
  const { t } = useTranslation();
  const { data: routines, isError } = useRoutines();
  const error = isError ? t("common.connectionError", { resource: t("routine.title") }) : null;

  return (
    <BentoListPage
      items={routines}
      error={error}
      tryAgainUrl={ROUTES.BENTO_ROUTINE_INSTANCE}
      backTo={ROUTES.BENTO_AREA_GENERATIVE_AI}
      backLabel={t("home.sections.generativeAi.label")}
      heroIcon={IconClock}
      tone="emerald"
      title={t("routine.title")}
      subtitle={t("routine.blankDescription")}
      newRoute={`${ROUTES.BENTO_ROUTINE_INSTANCE}/new`}
      newLabel={t("routine.newRoutine")}
      newSubtitle="Native · Groovy"
      itemKey={(routine) => routine.id ?? routine.name}
      emptyTitle={t("routine.blankTitle")}
      emptyDescription={t("routine.blankDescription")}
      listId="routine"
      renderTile={(routine, emphasis) => (
        <BentoEntityTile
          to={`${ROUTES.BENTO_ROUTINE_INSTANCE}/${routine.id}`}
          emphasis={emphasis}
          defaultIcon={IconClock}
          tone="emerald"
          title={routine.name}
          description={routine.description ?? (routine.kind === "GROOVY" ? "Groovy script" : routine.nativeToolName ?? "—")}
          hasStatus
          enabled={routine.enabled === false ? 0 : 1}
          meta={
            <span className="rounded-full border border-border/60 bg-card/40 px-2 py-0.5 backdrop-blur">
              {routine.kind}
            </span>
          }
        />
      )}
    />
  );
}
