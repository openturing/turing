import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { PersonaInstanceFormLayout } from "@/components/persona/persona.instance.form.layout";
import type { TurPersona } from "@/models/persona/persona.model.ts";
import { TurPersonaService } from "@/services/persona/persona.service";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useLocation, useParams } from "react-router-dom";

const turPersonaService = new TurPersonaService();

/**
 * Bento Persona detail — T556. Persona is a rich multi-section editor with its
 * own nested-route sidebar (general / system-instruction / style / audience /
 * notebook / fit-report / guidelines), so it reuses the existing
 * {@link PersonaInstanceFormLayout} verbatim, passing the Bento base route so
 * its section links stay inside the Bento shell. The section child routes are
 * registered under this page's `:id` route (see bento.routes.tsx).
 */
export default function BentoPersonaPage() {
  const { id } = useParams() as { id: string };
  const location = useLocation();
  const draft = (location.state as { draft?: TurPersona } | null)?.draft;
  const { t } = useTranslation();
  const [persona, setPersona] = useState<TurPersona>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (id === "new") {
      if (draft) {
        const seeded = { ...draft };
        delete (seeded as Partial<TurPersona>).id;
        setPersona(seeded as TurPersona);
      } else {
        setPersona({ name: "", verbosity: 3, enabled: 1 } as TurPersona);
      }
      setIsNew(true);
    } else {
      turPersonaService.get(id)
        .then(setPersona)
        .catch(() => setError(t("common.connectionError", { resource: t("persona.title") })));
      setIsNew(false);
    }
  }, [id, t, draft]);

  return (
    <LoadProvider checkIsNotUndefined={persona} error={error} tryAgainUrl={`${ROUTES.BENTO_PERSONA_INSTANCE}/${id}`}>
      {persona && <PersonaInstanceFormLayout value={persona} isNew={isNew} baseRoute={ROUTES.BENTO_PERSONA_INSTANCE} />}
    </LoadProvider>
  );
}
