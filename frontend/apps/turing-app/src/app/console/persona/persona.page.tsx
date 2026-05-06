import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { PersonaForm } from "@/components/persona/persona.form";
import type { BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurPersona } from "@/models/persona/persona.model.ts";
import { TurPersonaService } from "@/services/persona/persona.service";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const turPersonaService = new TurPersonaService();

export default function PersonaPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const [persona, setPersona] = useState<TurPersona>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[] | undefined>(
    id === "new" ? [{ label: t("persona.newInstance") }] : undefined
  );
  useSubPageBreadcrumb(breadcrumb);

  useEffect(() => {
    if (id === "new") {
      // Don't pre-set `id` — TurUuidGenerator only assigns a UUID when the
      // value is null; an empty string is treated as a pre-assigned id and
      // gets persisted as-is, breaking the detail-page link afterwards.
      setPersona({
        name: "",
        verbosity: 3,
        enabled: 1,
      } as TurPersona);
    } else {
      turPersonaService.get(id)
        .then((p) => {
          setPersona(p);
          setBreadcrumb([{ label: p.name, href: `${ROUTES.PERSONA_INSTANCE}/${p.id}` }]);
        })
        .catch(() => setError(t("common.connectionError", { resource: t("persona.title") })));
      setIsNew(false);
    }
  }, [id, t]);

  return (
    <LoadProvider checkIsNotUndefined={persona} error={error} tryAgainUrl={`${ROUTES.PERSONA_INSTANCE}/${id}`}>
      {persona && <PersonaForm value={persona} isNew={isNew} />}
    </LoadProvider>
  );
}
