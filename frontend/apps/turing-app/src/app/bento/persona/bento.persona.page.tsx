import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import type { TurPersona } from "@/models/persona/persona.model.ts";
import { TurPersonaService } from "@/services/persona/persona.service";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Outlet, useLocation, useParams } from "react-router-dom";

const turPersonaService = new TurPersonaService();

/**
 * Context threaded from the Bento persona loader to its child routes (the
 * dashboard landing and the section editor). A single fetch feeds both so the
 * dashboard's inline edits and the editor's form operate on the same entity.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export interface BentoPersonaContext {
  persona: TurPersona;
  isNew: boolean;
  /** Replace the loaded persona (used by the dashboard's inline-edit persist). */
  setPersona: (persona: TurPersona) => void;
}

/**
 * Bento Persona detail loader — T582. Fetches the persona once and exposes it
 * to child routes via the router `<Outlet context>`:
 *
 * - the index route renders the AI-agent-style **dashboard** (BentoHero +
 *   BentoTile mosaic + Persona Launchpad), superseding T556's "reuse the
 *   pill-tab form as the landing";
 * - the section routes render the shared-form editor (pill tab-bar + single
 *   `useForm` + save bar) unchanged, still reachable from the dashboard tiles.
 */
export default function BentoPersonaPage() {
  const { id } = useParams() as { id: string };
  const location = useLocation();
  const draft = (location.state as { draft?: TurPersona } | null)?.draft;
  const { t } = useTranslation();
  const [persona, setPersona] = useState<TurPersona>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  // The persona section sub-routes (/new/general, /new/system-instruction, …)
  // are separate URLs that don't carry router state, so an audio-derived draft
  // lives only in the state of the FIRST /new navigation. Seed it exactly once
  // per "new" session — re-running on a stateless section switch would wipe the
  // draft back to an empty persona.
  const seededNewRef = useRef(false);

  useEffect(() => {
    if (id === "new") {
      if (seededNewRef.current) {
        return;
      }
      seededNewRef.current = true;
      if (draft) {
        const seeded = { ...draft };
        delete (seeded as Partial<TurPersona>).id;
        setPersona(seeded as TurPersona);
      } else {
        setPersona({ name: "", verbosity: 3, enabled: 1 } as TurPersona);
      }
      setIsNew(true);
    } else {
      seededNewRef.current = false;
      turPersonaService.get(id)
        .then(setPersona)
        .catch(() => setError(t("common.connectionError", { resource: t("persona.title") })));
      setIsNew(false);
    }
  }, [id, t, draft]);

  return (
    <LoadProvider checkIsNotUndefined={persona} error={error} tryAgainUrl={`${ROUTES.BENTO_PERSONA_INSTANCE}/${id}`}>
      {persona && (
        <Outlet context={{ persona, isNew, setPersona } satisfies BentoPersonaContext} />
      )}
    </LoadProvider>
  );
}
