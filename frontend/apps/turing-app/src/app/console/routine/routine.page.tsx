import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import type { BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurRoutine } from "@/models/genai/routine.model";
import { TurRoutineService } from "@/services/genai/routine.service";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

import { RoutineForm } from "@/components/routine/routine.form";

/**
 * @since 2026.3.1
 */
const turRoutineService = new TurRoutineService();

export default function RoutinePage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const [routine, setRoutine] = useState<TurRoutine>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[] | undefined>(
    id === "new" ? [{ label: t("routine.newRoutine") }] : undefined,
  );
  useSubPageBreadcrumb(breadcrumb);

  useEffect(() => {
    if (id === "new") {
      setRoutine({
        name: "",
        kind: "NATIVE",
        enabled: true,
        defaultTimeoutMs: 60_000,
      } as TurRoutine);
      setIsNew(true);
    } else {
      turRoutineService
        .get(id)
        .then((r) => {
          setRoutine(r);
          setBreadcrumb([{ label: r.name, href: `${ROUTES.ROUTINE_INSTANCE}/${r.id}` }]);
        })
        .catch(() => setError(t("common.connectionError", { resource: t("routine.title") })));
      setIsNew(false);
    }
  }, [id, t]);

  return (
    <LoadProvider
      checkIsNotUndefined={routine}
      error={error}
      tryAgainUrl={`${ROUTES.ROUTINE_INSTANCE}/${id}`}
    >
      {routine && <RoutineForm value={routine} isNew={isNew} />}
    </LoadProvider>
  );
}
