import { useDeleteRoutine, useRoutine, useUpdateRoutine } from "@/api/queries/routine.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityShell } from "@/components/bento";
import { LoadProvider } from "@/components/loading-provider";
import type { TurRoutine } from "@/models/genai/routine.model";
import { IconClock } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BENTO_ROUTINE_FORM_ID, BentoRoutineForm } from "./bento.routine.form";

/**
 * Routine uses `name` (not `title`) and a boolean `enabled`, so the shared
 * shell's identity is aliased at the page boundary (title↔name, number↔bool).
 * No icon field → `hideIcon`. See T555.
 */
type RoutineView = Omit<TurRoutine, "enabled"> & { title: string; enabled: number };

const toView = (r: TurRoutine): RoutineView => ({ ...r, title: r.name ?? "", enabled: r.enabled === false ? 0 : 1 });

const fromView = (view: RoutineView): TurRoutine => {
  const { title, ...rest } = view;
  return { ...rest, name: title ?? rest.name, enabled: view.enabled === 1 };
};

export default function BentoRoutinePage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const isNew = id === "new";

  const { data: routine, isError } = useRoutine(isNew ? undefined : id);
  const error = isError ? t("common.connectionError", { resource: t("routine.title") }) : null;

  const updateMutation = useUpdateRoutine();
  const deleteMutation = useDeleteRoutine();

  function shell(entity: TurRoutine, headlineFallback: string) {
    return (
      <BentoEntityShell<RoutineView>
        entity={toView(entity)}
        isNew={isNew}
        headlineFallback={headlineFallback}
        eyebrow={t("routine.title")}
        listRoute={ROUTES.BENTO_ROUTINE_INSTANCE}
        icon={IconClock}
        tone="emerald"
        formId={BENTO_ROUTINE_FORM_ID}
        feature={t("routine.title")}
        hasStatus
        hideIcon
        onUpdate={isNew ? undefined : (next) => updateMutation.mutateAsync(fromView(next))}
        onDelete={isNew ? undefined : () => deleteMutation.mutateAsync(entity)}
      >
        {({ staged, onStateChange }) => (
          <BentoRoutineForm value={entity} isNew={isNew} staged={staged} onStateChange={onStateChange} />
        )}
      </BentoEntityShell>
    );
  }

  if (isNew) {
    return shell({ kind: "NATIVE" } as TurRoutine, t("routine.newRoutine"));
  }

  return (
    <LoadProvider checkIsNotUndefined={routine} error={error} tryAgainUrl={`${ROUTES.BENTO_ROUTINE_INSTANCE}/${id}`}>
      {routine && shell(routine, routine.name)}
    </LoadProvider>
  );
}
